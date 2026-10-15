# Where the backend could run for free

The frontend is on Vercel's free tier. The backend is not hosted anywhere, which is why the live site is a demo
([#27](https://github.com/2daKaizen-gun/kotona-analyzer/issues/27)). This page records what hosting it would
take, so the decision can be made from numbers rather than from memory. Written 2026-10-15 (#93).

## What this app actually needs

Measured on the release jar (`app.jar`, 93 MB), JDK 21, against a local MySQL. A container on a free tier
has slower CPUs than the machine this ran on; the startup figure is a floor, not an estimate.

| Heap limit | Starts? | Startup to first `/api/health` | Resident memory after 90 requests |
|---|---|---|---|
| `-Xmx64m` | **no** — `OutOfMemoryError: Java heap space` during startup | — | — |
| `-Xmx96m` | yes | 20.6 s | 350 MB |
| `-Xmx128m` | yes | 19.0 s | 366 MB |
| `-Xmx256m` | yes | 18.9 s | 395 MB |
| `-Xmx96m` + `-XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=48m -XX:MaxMetaspaceSize=160m -XX:TieredStopAtLevel=1` | yes | 19.5 s | **308 MB** |

The requests mixed dictionary reads with analysis requests refused before the model call (the model key
was a dummy, rejected by Google, so no quota was spent) — enough to load Kuromoji's dictionary and exercise
the tokenizer, which is where the memory goes.

So, concretely:

- **Memory: about 320 MB at the least.** A 512 MB instance fits, with the lean flags above for headroom.
  A 256 MB instance does not.
- **Startup: about 20 s on a desktop CPU.** On a 0.1 vCPU free instance it will be several times that. Any
  host that sleeps when idle makes the first visitor after a quiet period wait for a full JVM start.
- **Requests: up to 100 s** (`GEMINI_TIMEOUT_MS`). Hosts that cap a request below that would cut analyses.
- **Database: real MySQL 8.** The schema uses `utf8mb4_ja_0900_as_cs_ks`, a MySQL-specific Japanese
  collation, and Flyway's `V1__baseline.sql` creates it. A "MySQL-compatible" service has to have that
  exact collation, or the first migration fails.

## The options as of October 2026

Free tiers change often; each line below links where it was read, and the figures should be checked again
before acting on them.

| | Compute | Sleeps when idle | Fits this app? |
|---|---|---|---|
| **Oracle Cloud Always Free** (Ampere A1) | 2 OCPU / 12 GB in total — cut from 4 / 24 in mid-2026 | no | **Yes, with room for MySQL on the same VM.** `docker compose up` as in the README. The catch is getting it: sign-up needs card verification, capacity is not always available, and the cut was applied without an announcement |
| **Google Cloud Run** | free monthly 180k vCPU-s, 360k GiB-s, 2M requests; instance size is chosen per service | scales to zero | Yes at 512 MiB–1 GiB; request timeout configurable up to 60 min. Needs a billing account. Cold start = a full JVM start |
| **Render** free web service | reported 512 MB / 0.1 CPU; 750 h a month | after 15 min, ~1 min to wake | Tight — only with the lean flags. Wake-up plus a slow JVM start on 0.1 CPU will be long |
| **Koyeb** free instance | 512 MB / 0.1 vCPU, one per organization | after about an hour | Same as Render, a little longer between sleeps |

Database, for the three options that do not bring their own:

| | Free plan | Fits? |
|---|---|---|
| **Aiven for MySQL** | 1 CPU, 1 GB RAM, 1 GB disk, one service, no time limit; may be shut down after long inactivity | **Yes** — real MySQL, and the data here is kilobytes |
| **TiDB Cloud Starter** | MySQL-compatible, 25 GiB row storage | **Unverified** — no source found saying it has `utf8mb4_ja_0900_as_cs_ks`; check with `SHOW COLLATION` first |
| PlanetScale | free plan removed | — |

## Recommendation

1. **Oracle Always Free, if an account can be had.** It is the only option that is always on, holds the
   app and the database together, and is not tight on memory. Everything needed is already in the repo:
   the Dockerfile (non-root since #91) and `docker-compose.yml`.
2. **Otherwise Cloud Run + Aiven MySQL.** It sleeps, but sizes memory freely and is generous with request
   time. Expect the first request after idle to take as long as a JVM start.
3. Render or Koyeb only if neither works — 512 MB and 0.1 CPU is the edge of what this app runs in.

Whichever it is, going live also means the steps in kotona-web's `DEPLOYMENT.md`: `KOTONA_API_URL`,
`KOTONA_API_KEY` matching the backend's `API_KEY`, **`KOTONA_SITE_PASSWORD`**, and `CORS_ALLOWED_ORIGINS`.

## Sources

- Render: [Deploy for Free](https://render.com/docs/free); 512 MB / 0.1 CPU from a third-party guide ([livemy.app](https://livemy.app/blog/render-pricing)), not found on Render's own page
- Oracle: [InfoQ, July 2026](https://infoq.com/news/2026/07/oracle-cloud-free-tier-limits/), [terminalbytes](https://terminalbytes.com/oracle-cloud-free-tier-changes-2026) — sources disagree on the effective date (June 15 or August 18, 2026)
- Cloud Run: [quotas](https://docs.cloud.google.com/run/quotas); free allowances via [cloudchipr](https://cloudchipr.com/blog/cloud-run-pricing)
- Koyeb: [freetier.co](https://freetier.co/articles/best-free-cloud-hosting-2026), [community thread](https://community.koyeb.com/t/deployment-keeps-sleeping/3300)
- Aiven: [MySQL free tier](https://aiven.io/docs/products/mysql/concepts/mysql-free-tier)
- TiDB: [cluster tiers](https://docs.pingcap.com/tidbcloud/select-cluster-tier), [character sets](https://docs.pingcap.com/tidb/v6.5/character-set-and-collation/)
- PlanetScale: [cloudinsight roundup](https://cloudinsight.cc/en/blog/free-cloud-database)
- The collation: [MySQL 8.4 manual](https://docs.oracle.com/cd/E17952_01/mysql-8.4-en/charset-mysql.html)
