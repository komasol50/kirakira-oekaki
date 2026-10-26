package io.github.komasol50.kirakira.engine

import androidx.compose.ui.graphics.Color
import io.github.komasol50.kirakira.R

/**
 * 配色・寸法の正本。
 * Claude Design「デザインブリーフ v1.0」パターンA（夜空の宝石箱）を反映。
 * 正本ドキュメント: docs/design/brief.md
 * v2: 背景テーマ3種（よぞら／みどり／みずのなか）・パレット16色へ拡張。
 * v2.2: 画像背景テーマ5種体制（よぞら＝グラデ／はらっぱ・うみ・ほしぞら・にじ＝画像）へ拡張。
 */
object Design {
    // 夜空グラデーション（radial: Glow → Top → Deep）
    val NightGlow = Color(0xFF4A2B8F)
    val NightTop = Color(0xFF31175C)
    val NightDeep = Color(0xFF130824)
    val Star = Color(0xFF8F7BC8)   // 背景の瞬く星

    // やさしいみどりの環境エフェクト（舞う花びら/たね毛）・みずのなかの環境光（泡）
    // v2.1: 緑・水を明るいトーンへ差し替え。ホタル光は明色地で見えないため花びらへ変更。
    val PetalWhite = Color(0xFFFFFFFF)   // 白い花びら
    val PetalPink = Color(0xFFFFC6DE)    // 薄ピンクの花びら
    val PetalYellow = Color(0xFFFFF0A6)  // 薄黄の花びら（旧ホタル色を流用）
    val Bubble = Color(0xFFFFFFFF)       // 明色地対応：泡の芯は白
    val BubbleEdge = Color(0xFF4FA8DC)   // 泡の縁取り（薄青）

    // v2.2: ほしぞらの金/白のやわらかい瞬き・にじのパステル粒
    val TwinkleGold = Color(0xFFFFE9A8)  // ほしぞらの金の光点
    val TwinkleWhite = Color(0xFFFFFFFF) // ほしぞらの白の光点
    val PastelDrift = listOf(            // にじの上を漂うパステルの粒
        Color(0xFFFFC6DE), Color(0xFFFFF0A6), Color(0xFFC6E8FF),
        Color(0xFFD6C6FF), Color(0xFFC6FFD9),
    )

    // アクセント（UIは Pink/Purple/Gold/Ink の4色で統一）
    val Pink = Color(0xFFFF7AC8)       // CandyPink
    val PinkDeep = Color(0xFFE8489F)   // CandyPinkDeep
    val Purple = Color(0xFFB583FF)     // WitchPurple
    val Gold = Color(0xFFFFD76A)       // RoyalGold（面には使わない・線/粒/バッジ専用）
    val GoldDeep = Color(0xFFF0AE3C)
    val Ink = Color(0xFFFFF6FD)        // StardustInk

    // 上下バーのパステル配色（v2.3・feedback 16「黒はつまらない→かわいい色」）
    // 明るいクリーム〜ピンクの面＋やわらかいピンクの区切り線。5テーマすべての上で自然に見えること。
    val BarBg = Color(0xF7FFF0FA)      // バー地（クリーム〜ピンク・わずかに透過）
    val BarLine = Color(0xFFE9BFE0)    // 区切り線・ボタン枠（やわらかいピンク）
    val BtnFace = Color(0xFFFFFFFF)    // バーのボタン地（白）
    val BarInk = Color(0xFF6B4A8A)     // バーのアイコン/文字インク（濃いめの紫・白地でも視認）
    val ButtonBg = Color(0x1FFFFFFF)   // GlassSurface（白12%・オーバーレイ用に据え置き）

    // メニュー等オーバーレイ用（v2）
    val Scrim = Color(0xCC0A0413)      // 全画面スクリム（暗）
    val CardBg = Color(0xFF241243)     // メニューカード地（不透明）

    // おえかき18色パレット（9色×2段）
    // 明背景4テーマが主役になったため v2.4 でくろ・ちゃいろを解禁（よぞら上のくろは見えにくいが許容）。
    val Palette = listOf(
        // ---- 1段目 ----
        Color(0xFFFF7AC8), // ピンク（デフォルト）
        Color(0xFFB583FF), // むらさき
        Color(0xFF6FC2FF), // そら
        Color(0xFF7BF0C9), // ミント
        Color(0xFFFFE066), // レモン
        Color(0xFFFFFFFF), // しろ
        Color(0xFFF6C39A), // はだいろ（v2.6.1: オレンジと近かったピーチを置換。人物・プリンセスの顔用）
        Color(0xFFFF5A7E), // チェリー
        Color(0xFFB07A4F), // ちゃいろ
        // ---- 2段目 ----
        Color(0xFFFF4FA3), // ふかピンク
        Color(0xFFFF6B6B), // あか
        Color(0xFFFFB347), // オレンジ
        Color(0xFFC6F24E), // きみどり
        Color(0xFF5EE6A0), // みどり
        Color(0xFF8FE3FF), // みずいろ
        Color(0xFF7FB2FF), // あお
        Color(0xFFD9A9FF), // ラベンダー
        Color(0xFF3B3642), // くろ
    )

    // 線の太さ（px基準）
    val Sizes = mapOf("small" to 8f, "mid" to 16f, "big" to 28f)
    val SizeKeys = listOf("small", "mid", "big")

    const val MaxParticles = 500
    const val MaxUndo = 8
    const val GalleryLimit = 30   // アプリ内ギャラリーの保持枚数（古い順に間引く）
}

/** 背景の環境エフェクトの種類。 */
enum class AmbientKind { STARS, PETALS, BUBBLES, GOLD_TWINKLE, PASTEL_DRIFT }

/**
 * 背景テーマ5種。グラデ色・環境エフェクト・（画像テーマは）画像リソースを一括で持つ。
 * [radial] が true なら中心やや上のラジアル、false なら上→下の縦グラデ。
 * [gradient] は画像読込前のフォールバック地＆よぞらの本体。exportToGallery でも参照する。
 * [imageRes] が非nullなら画像テーマ（drawable-nodpi のWebP）。null（よぞら）はグラデ地。
 * v2.2: SharedPreferences 互換のため enum 名 NIGHT/GREEN/WATER は変更しない
 * （旧 GREEN→はらっぱ・WATER→うみ・NIGHT→よぞら に自動対応）。
 */
enum class BgTheme(
    val jpName: String,
    val gradient: List<Color>,
    val radial: Boolean,
    val ambient: AmbientKind,
    val imageRes: Int? = null,
) {
    NIGHT(
        "よぞら",
        listOf(Design.NightGlow, Design.NightTop, Design.NightDeep),
        radial = true,
        ambient = AmbientKind.STARS,
    ),
    GREEN(
        "はらっぱ",
        // 画像読込前のフォールバック（明るいパステル緑）
        listOf(Color(0xFFC8EFC0), Color(0xFF9BDD9B), Color(0xFF6FBF84)),
        radial = false,
        ambient = AmbientKind.PETALS,
        imageRes = R.drawable.bg_harappa,
    ),
    WATER(
        "うみ",
        // 画像読込前のフォールバック（明るいアクア）
        listOf(Color(0xFFBEE9FF), Color(0xFF7FCBEF), Color(0xFF4FA8DC)),
        radial = false,
        ambient = AmbientKind.BUBBLES,
        imageRes = R.drawable.bg_umi,
    ),
    HOSHIZORA(
        "ほしぞら",
        // 画像読込前のフォールバック（深い星空の青）
        listOf(Color(0xFF2A2E6B), Color(0xFF161A44), Color(0xFF0A0C24)),
        radial = false,
        ambient = AmbientKind.GOLD_TWINKLE,
        imageRes = R.drawable.bg_hoshizora,
    ),
    NIJI(
        "にじ",
        // 画像読込前のフォールバック（やわらかいパステル）
        listOf(Color(0xFFFFE0EC), Color(0xFFE0F0FF), Color(0xFFF0E0FF)),
        radial = false,
        ambient = AmbientKind.PASTEL_DRIFT,
        imageRes = R.drawable.bg_niji,
    ),
}
