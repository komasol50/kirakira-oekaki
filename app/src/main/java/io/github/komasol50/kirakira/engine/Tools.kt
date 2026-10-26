package io.github.komasol50.kirakira.engine

import io.github.komasol50.kirakira.R

/** ブラシ4種＋消しゴム（v2.3）。 */
enum class Brush { SPARKLE, STAR, HEART, RAINBOW, ERASER }

/**
 * スタンプ12種（v2.2でPNG化・v2.5でりぼん/ハート追加・drawable-nodpi のWebP。実在キャラは使わない）。
 * [res] は描画・サムネで使う画像リソース、[jpName] はピッカーの見出し。
 * 並び順はスタンプピッカー（4列×3行グリッド）の表示順。
 */
enum class Stamp(val res: Int, val jpName: String) {
    UNICORN(R.drawable.stamp_unicorn, "ユニコーン"),
    CASTLE(R.drawable.stamp_castle, "おしろ"),
    CAT(R.drawable.stamp_cat, "ねこ"),
    FLOWER(R.drawable.stamp_flower, "おはな"),
    RAINBOW(R.drawable.stamp_rainbow, "にじ"),
    STAR(R.drawable.stamp_star, "ほし"),
    STRAWBERRY(R.drawable.stamp_strawberry, "いちご"),
    TIARA(R.drawable.stamp_tiara, "ティアラ"),
    WAND(R.drawable.stamp_wand, "ステッキ"),
    BUTTERFLY(R.drawable.stamp_butterfly, "ちょうちょ"),
    RIBBON(R.drawable.stamp_ribbon, "りぼん"),
    HEART(R.drawable.stamp_heart, "ハート"),
}

/** 現在の道具。ブラシモードかスタンプモードのどちらか。 */
sealed interface Tool {
    data class BrushTool(val brush: Brush) : Tool
    data class StampTool(val stamp: Stamp) : Tool
}
