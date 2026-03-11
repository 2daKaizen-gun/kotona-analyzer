package com.kaizen.kotona.analyzer.utils;

import java.util.List;

public class EtiquetteConstants {
    // 쿠션어 리스트(Etiquette 점수 검증용)
    public static final List<String> CUSHION_PHRASES = List.of(
            "お手数", "恐縮", "申し訳ございません", "差し支えなければ", "お忙しいところ", "念のため"
    );

    // 완곡 어법 어미 (Indirectness 점수 검증용)
    public static final List<String> INDIRECT_ENDINGS = List.of(
            "でしょうか", "いただけますか", "かと思われます", "ございませんか"
    );
}
