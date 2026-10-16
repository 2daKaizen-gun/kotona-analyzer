# How a score is produced, and where it is checked

One page, start to end. Every stage names the file that does the work and the test that holds it, so a
number on screen can be traced back to a line of code or a labelled sentence.

```
POST /analyze   { text, relationship }
  │
  1  normalize, and reject what is not analysable      JapaneseTextNormalizer
  2  morphology: does it end politely?                 JapaneseTokenService
  3  ask the model, with the schema forced             GeminiService + NuanceSchemaFactory
  4  rules adjust the three metrics and the grade      AnalysisValidator
  5  strip non-Japanese out of the generated text      JapaneseOutputSanitizer
  6  save the row                                      AnalysisHistoryService
  7  show the score with every adjustment              kotona-web / ResultView
  │
  └→ { metrics, total, riskAnalysis, honne, smartReplies, scoreAdjustments }
```

| stage | what it does | what checks it |
|---|---|---|
| 1 | NFKC-normalizes, trims, and refuses input with no Japanese in it — before any paid call. The normalized text is what gets analysed; history keeps the sentence as typed | `JapaneseTextNormalizerTest`, `GeminiServiceTest` |
| 2 | Kuromoji IPADIC: です・ます conjugation types and the くださる imperative, not substrings | `JapaneseTokenServiceTest`; agreement with the labels **41 / 41** |
| 3 | `responseSchema` is generated from the `NuanceResponseDTO` record tree, so the model cannot answer in a shape the server does not accept, and nothing is parsed out of markdown | `NuanceSchemaFactoryTest`; `./gradlew liveTest` spends one real call and checks the answer still parses |
| 4 | the subject of this page, below | `AnalysisValidatorScoringTest`, `RuleLayerEvaluationTest`, `RuleLayerOrderingTest` |
| 5 | the model sometimes mixes Korean or English into Japanese replies; those are dropped rather than shown | `JapaneseOutputSanitizerTest` |
| 6 | one history row per analysis, written through Flyway-managed schema. A failed save is logged and the analysis is returned anyway — the model call has already been paid for | `AnalysisHistorySaveTest`, `GeminiServiceTest`; CI starts from an empty database |
| 7 | the adjustments are rendered as a collapsible list — which metric, before, after, why | `ResultView.test.tsx`, plus the Playwright flows |

## Stage 4, in detail

The model returns politeness (max 40), indirectness (30) and etiquette (30). The schema states those ranges
but cannot enforce them, so a metric outside its scale is first pulled back onto it — a politeness of −20
once produced a total of −10. After that the rules may lower each one, and may only lower it. Every change,
the pull included, is appended to `scoreAdjustments` with its reason, so nothing the server does to the
model's answer is invisible to the reader.

| rule | fires when | effect | why it is gated on the model's own score |
|---|---|---|---|
| no polite ending | the model gave politeness **≥ 30** and Kuromoji finds no です・ます / 〜ください | **−10** | if the model already scored it low, it saw the same thing; subtracting again would double-count |
| no cushion phrase | the model gave etiquette **≥ 20** and no cushion lemma or phrase is present | **−10** | the same reason |
| no indirect ending | the model gave indirectness **≥ 20** and no 〜でしょうか-type ending is present | **−5** | the same reason |

The grade is computed separately:

```
risk = Σ weight(signal found)        0.8 難しい · 見送る · 添いかねる · 予定はございません
                                     0.6 考える + おく
                                     0.5 検討 · 持ち帰る · 善処
                                     0.2 確認
risk × relationship multiplier       1.0 internal · 1.2 external · 1.5 interview
  ≥ 0.7 → DANGER     ≥ 0.3 → CAUTION     otherwise SAFE

final grade = the more severe of (rules, model)
```

A signal word is counted only where it actually refuses or defers. `JapaneseTokenService.refusalLemmas`
reads Kuromoji's parts of speech and leaves a word out in four places — before #91 the rules read a bag of
words, and 「ご確認とご検討のほど、よろしくお願いいたします」, a request, came out `DANGER`:

| place | example | dropped |
|---|---|---|
| after the prefix ご・御 — the other side is asked | 「ご検討ください」 | the deferral words† |
| before a past た in the verb chain — it is finished | 「検討した結果」 | the deferral words† |
| inside a が・けど clause the sentence continues past — a concession | 「難しい課題ですが、対応いたします」 | 難しい |
| in a message that promises a reply by a stated time | 「〜までに回答します」 | the deferral words† |

† 検討・確認・考える・持ち帰る・善処 (`EtiquetteConstants.DEFERRAL_LEMMAS`). Refusal words like 見送る are never
dropped. Seven labelled sentences hold these places; on them the old
rules raised 6 false alarms, the current ones none.

Taking the more severe of the two is the one place the design is deliberately asymmetric. The rules see only
the words in their dictionary, read in the places above, so they miss anything outside it; the model reads the sentence but has no
fixed definition it can be held to. Either may be right alone, and the cost of the two mistakes is not
symmetric — a missed refusal is a reply sent to a client in good faith, a false alarm is a sentence the user
rewrites for nothing. So a miss is corrected by the other side, and a false alarm is what **the rules** are
tested against (**0 / 27** on the safe sentences) — only the rules, which is the part worth being careful
about: nothing asserts that the model will not raise one, and the model's answer wins whenever it is the
more severe.

What that asymmetry costs is measured rather than assumed. On all 41 labelled sentences, the
composed grade — the one this stage returns — agrees with the label 37 times, and the four it does not are
all *more* severe than the label, none less. One of them is 「社内で確認のうえ、改めてご連絡いたします」: the
rules call it `SAFE`, the label calls it `SAFE`, and the model's `CAUTION` becomes the answer because it is
the more severe. The other three are the model reading a deferral as a refusal in practice where the label
reads the text. The design says that is the right direction to err in; the number says how often erring
costs a reader a warning they did not need. `ProductGradeTest` recomputes it from the recorded answers on
every push and names those four, so a fifth fails the build instead of joining them — which is how the
fourth, 「現状では難しいですが、社内で検討いたします」, was found.

`redFlags` merges both lists for the same reason. It used to carry only the model's, which produced
`riskLevel: SAFE` next to a list of warning signs when the rules had found something the model had not.

## A worked example

「確認してください」 to an external contact. The model's three numbers are assumed here — that part has no
ground truth and this is an illustration of the path, not a measurement:

| step | value |
|---|---|
| model returns | politeness 34, indirectness 22, etiquette 24 → 80 |
| polite ending | 〜ください is 尊敬語 — found, no penalty |
| cushion phrase | none (no お手数 / 恐れ入る / …) and etiquette ≥ 20 → etiquette **24 → 14** |
| indirect ending | none (plain imperative) and indirectness ≥ 20 → indirectness **22 → 17** |
| total | 34 + 17 + 14 = **65** |
| risk | 確認 = 0.2, external × 1.2 = 0.24 → below 0.3 → SAFE from the rules |
| grade | SAFE, unless the model said otherwise, in which case the model's grade stands |
| returned | two entries in `scoreAdjustments`, each with the before, the after and the reason |

So of the 35 points lost, 20 came from the model and 15 from two dictionary lookups — and the reader is told
which, because that is the difference between a score and a verdict.

## What this page does not claim

The model's three numbers have no authority behind them, the weights above were chosen by hand, and nothing
calibrates what a polite request is *worth* out of 40. What is verified is narrower and each part carries its
denominator in the [README](../README.md#what-is-still-unverified): polite form and cushion detection match
the labels on all **41** sentences, the refusal dictionary finds **13 of 14** risky ones and raises **0 of
27** false alarms, the rules grade **0 of 41** more severely than the label, all **7** ordering pairs hold for the rules and for the model (six strictly, one level),
and the composed grade agrees with the label on **37 of the 41** sentences.
