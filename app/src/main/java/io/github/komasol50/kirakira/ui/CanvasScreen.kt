package io.github.komasol50.kirakira.ui

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.komasol50.kirakira.engine.Brush as BrushKind
import io.github.komasol50.kirakira.engine.Design
import io.github.komasol50.kirakira.engine.DrawEngine
import io.github.komasol50.kirakira.engine.ParticleSystem
import io.github.komasol50.kirakira.engine.PlayTimer
import io.github.komasol50.kirakira.engine.Stamp
import io.github.komasol50.kirakira.engine.AmbientKind
import io.github.komasol50.kirakira.engine.BgTheme
import io.github.komasol50.kirakira.engine.Tool
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private class Ambient(val nx: Float, val ny: Float, val r: Float, val ph: Float, val sp: Float)

/** 全画面の画面状態（Navigationライブラリ不使用・Compose stateで切替）。 */
enum class Screen { CANVAS, MENU, GALLERY, BG_SELECT, CONFIRM_RESET, STAMP_PICK }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CanvasScreen(engine: DrawEngine, particles: ParticleSystem, timer: PlayTimer) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var screen by remember { mutableStateOf(Screen.CANVAS) }
    // あそびタイマー（v2.6・S）: 残り分ピルと休憩オーバーレイの状態。
    var remainingMin by remember { mutableStateOf(timer.remainingMinutes()) }
    var breakActive by remember { mutableStateOf(false) }
    // 画面サイズ（休憩解除の祝祭バースト用）。
    var canvasW by remember { mutableStateOf(0) }
    var canvasH by remember { mutableStateOf(0) }
    // 1秒ごとに累積を確認：残り分の更新と、休憩到達の検知（描画中は onUp を待つ）。
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            remainingMin = timer.remainingMinutes()
            if (!breakActive && timer.isBreakDue() && !engine.isDrawing) {
                breakActive = true
            }
            kotlinx.coroutines.delay(1000)
        }
    }
    // 休憩に入った瞬間だけやさしいチャイムを鳴らす。
    androidx.compose.runtime.LaunchedEffect(breakActive) {
        if (breakActive) engine.breakChime()
    }
    // 上下バーの実高さ(px)。両方確定したら背景を可視領域へ合わせる（v2.5・R）。
    var topBarPx by remember { mutableStateOf(0) }
    var bottomBarPx by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(topBarPx, bottomBarPx) {
        if (topBarPx > 0 && bottomBarPx > 0) engine.setBarHeights(topBarPx, bottomBarPx)
    }
    var toast by remember { mutableStateOf("") }
    val toastAlpha by animateFloatAsState(if (toast.isNotEmpty()) 1f else 0f, label = "toast")
    fun showToast(msg: String) {
        toast = msg
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ toast = "" }, 1600)
    }

    val stars = remember {
        List(26) { Ambient(Random.nextFloat(), Random.nextFloat(), 0.6f + Random.nextFloat() * 1.6f, Random.nextFloat() * 6f, 0.5f + Random.nextFloat() * 1.2f) }
    }

    val frame = remember { mutableLongStateOf(0L) }
    val startNs = remember { mutableLongStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { t ->
                if (startNs.longValue == 0L) startNs.longValue = t
                particles.update()
                frame.longValue = t
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBackground(engine.theme)
            // 描画キャンバス
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        for (ch in ev.changes) {
                            val id = ch.id.value
                            when {
                                ch.changedToDown() -> { engine.onDown(id, ch.position.x, ch.position.y); ch.consume() }
                                ch.changedToUp() -> { engine.onUp(id); ch.consume() }
                                ch.pressed -> {
                                    ch.historical.forEach { engine.onMove(id, it.position.x, it.position.y) }
                                    engine.onMove(id, ch.position.x, ch.position.y)
                                    ch.consume()
                                }
                            }
                        }
                    }
                }
            }
            .drawWithContentLayer(engine, particles, stars, frame, startNs, engine.theme),
    ) {
        // レイアウト確定でビットマップサイズを確定
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizedChanged { w, h -> engine.ensureSize(w, h); canvasW = w; canvasH = h },
        )

        // 上部バー（全幅・上端に密着）
        TopBar(
            engine = engine,
            remainingMin = remainingMin,
            onMenu = { screen = Screen.MENU },
            onSave = {
                Thread {
                    val ok = engine.exportToGallery(context)
                    mainHandler.post { showToast(if (ok) "ほぞんしたよ！" else "ほぞんに しっぱい") }
                }.start()
            },
            modifier = Modifier.align(Alignment.TopCenter).onSizedChanged { _, h -> topBarPx = h },
        )

        // 下部トレイ（全幅・下端に密着）
        ToolTray(
            engine = engine,
            onOpenStampPicker = { screen = Screen.STAMP_PICK },
            modifier = Modifier.align(Alignment.BottomCenter).onSizedChanged { _, h -> bottomBarPx = h },
        )

        // トースト
        if (toastAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 76.dp)
                    .graphicsAlpha(toastAlpha)
                    .background(Brush.horizontalGradient(listOf(Design.Pink, Design.Purple)), RoundedCornerShape(999.dp))
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            ) {
                Text(toast, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
        }

        // 全画面オーバーレイ（メニュー／ギャラリー／はいけい／確認）
        when (screen) {
            Screen.CANVAS -> {}
            Screen.MENU -> MenuOverlay(
                engine = engine,
                onGallery = { screen = Screen.GALLERY },
                onBgSelect = { screen = Screen.BG_SELECT },
                onReset = { screen = Screen.CONFIRM_RESET },
                onClose = { screen = Screen.CANVAS },
            )
            Screen.GALLERY -> GalleryOverlay(engine = engine, onBack = { screen = Screen.MENU })
            Screen.BG_SELECT -> BgSelectOverlay(engine = engine, onBack = { screen = Screen.MENU })
            Screen.CONFIRM_RESET -> ConfirmResetOverlay(
                onConfirm = {
                    engine.magicReset()
                    showToast("まほうで きれいに なったよ")
                    screen = Screen.CANVAS
                },
                onCancel = { screen = Screen.MENU },
            )
            Screen.STAMP_PICK -> StampPickerOverlay(
                engine = engine,
                onPick = { stamp ->
                    engine.selectStamp(stamp)
                    screen = Screen.CANVAS
                },
                onBack = { screen = Screen.CANVAS },
            )
        }

        // 休憩オーバーレイ（全画面・全タッチをブロック・他のオーバーレイより手前・v2.6・S）
        if (breakActive) {
            BreakOverlay(
                engine = engine,
                onUnlock = {
                    timer.reset(context)
                    remainingMin = timer.remainingMinutes()
                    breakActive = false
                    engine.celebrateBreakCleared(canvasW.toFloat(), canvasH.toFloat())
                    showToast("よく がんばったね！")
                },
            )
        }
    }
}

// ---- 上部バー（全幅・上端密着・下辺のみ区切り線） ----
@Composable
private fun TopBar(engine: DrawEngine, remainingMin: Int, onMenu: () -> Unit, onSave: () -> Unit, modifier: Modifier) {
    FlushBar(modifier = modifier, lineAtBottom = true) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // メニュー（サウンド・リセットはこの中へ移設）
            RoundButton(size = 46.dp, onClick = onMenu) { tint -> IconGlyph("menu", tint = tint) }
            // もどる（undo）を大きめに。使えない時は薄く。
            RoundButton(size = 56.dp, enabled = engine.canUndo, onClick = { engine.undo() }) { tint -> IconGlyph("undo", tint = tint) }
            // すすむ（redo）。redo可能な時だけ有効（v2.3・L）。
            RoundButton(size = 56.dp, enabled = engine.canRedo, onClick = { engine.redo() }) { tint -> IconGlyph("redo", tint = tint) }
            // 次の休憩までの残り分ピル（中央寄り・1分単位・v2.6・S）
            Spacer(modifier = Modifier.weight(1f))
            TimerPill(remainingMin = remainingMin)
            Spacer(modifier = Modifier.weight(1f))
            // カメラ（ほぞん）
            RoundButton(size = 46.dp, onClick = onSave) { tint -> IconGlyph("camera", tint = tint) }
        }
    }
}

/** 「⏰あと◯ふん」ピル（次の休憩までの残り分）。白地＋濃い紫インクでバーに馴染ませる。 */
@Composable
private fun TimerPill(remainingMin: Int) {
    Box(
        modifier = Modifier
            .background(Design.BtnFace, RoundedCornerShape(999.dp))
            .border(1.dp, Design.BarLine, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "⏰あと${remainingMin}ふん",
            color = Design.BarInk, fontWeight = FontWeight.Black, fontSize = 15.sp,
            maxLines = 1,
        )
    }
}

// ---- 下部トレイ（全幅・下端密着・上辺のみ区切り線） ----
// v2.1: 横スクロール全廃。全ボタン常時表示。段構成＝①どうぐ ②いろ8色 ③いろ8色 ④おおきさ3個。
// v2.2: どうぐ段＝ブラシ4＋仕切り＋スタンプボタン1個（サムネ表示・タップでピッカー）。
@Composable
private fun ToolTray(engine: DrawEngine, onOpenStampPicker: () -> Unit, modifier: Modifier) {
    val tool = engine.tool
    FlushBar(modifier = modifier, lineAtBottom = false) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            // ① どうぐの段（ブラシ4＋区切り＋スタンプボタン1個）
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val brushSel = tool as? Tool.BrushTool
                RoundButton(size = 44.dp, selected = brushSel?.brush == BrushKind.SPARKLE, onClick = { engine.selectBrush(BrushKind.SPARKLE) }) { tint -> IconGlyph("sparkle", tint = tint) }
                RoundButton(size = 44.dp, selected = brushSel?.brush == BrushKind.STAR, onClick = { engine.selectBrush(BrushKind.STAR) }) { tint -> IconGlyph("star", tint = tint) }
                RoundButton(size = 44.dp, selected = brushSel?.brush == BrushKind.HEART, onClick = { engine.selectBrush(BrushKind.HEART) }) { tint -> IconGlyph("heart", tint = tint) }
                RoundButton(size = 44.dp, selected = brushSel?.brush == BrushKind.RAINBOW, onClick = { engine.selectBrush(BrushKind.RAINBOW) }) { tint -> IconGlyph("rainbow", tint = tint) }
                // 消しゴム（ブラシ4の後ろ・仕切りの前・v2.3・O）
                RoundButton(size = 44.dp, selected = brushSel?.brush == BrushKind.ERASER, onClick = { engine.selectBrush(BrushKind.ERASER) }) { tint -> IconGlyph("eraser", tint = tint) }
                Divider()
                // スタンプボタン: 選択中（直近）スタンプのサムネを表示。タップでピッカーを開く。
                StampButton(
                    stamp = engine.lastStamp,
                    selected = tool is Tool.StampTool,
                    onClick = onOpenStampPicker,
                )
            }
            // ②③ いろの段（18色を9色ずつ2段で常時表示）
            SwatchRow(engine, Design.Palette.subList(0, 9))
            SwatchRow(engine, Design.Palette.subList(9, 18))
            // ④ おおきさの段（大中小・中央寄せ）
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Design.SizeKeys.forEach { key ->
                    SizeDot(key = key, selected = engine.sizeKey == key, onClick = { engine.selectSize(key) })
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 9色を等間隔で1段に並べる（横スクロールなし・幅360dpでも収まる固定サイズ）。 */
@Composable
private fun SwatchRow(engine: DrawEngine, colors: List<Color>) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        colors.forEach { c ->
            Swatch(color = c, selected = engine.color == c, onClick = { engine.selectColor(c) })
        }
    }
}

// ---- 部品 ----
/**
 * 画面端に密着した全幅バー。角丸・浮きなし。
 * [lineAtBottom]=true で下辺（上部バー用）、false で上辺（下部トレイ用）に1本だけ区切り線。
 * consumeTouches で背後キャンバスへの描画漏れを防ぐ（現行踏襲）。
 */
@Composable
private fun FlushBar(modifier: Modifier, lineAtBottom: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Design.BarBg)
            .drawBehind {
                val y = if (lineAtBottom) size.height else 0f
                drawLine(Design.BarLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .consumeTouches(),
    ) { content() }
}

@Composable
private fun RoundButton(
    size: androidx.compose.ui.unit.Dp = 56.dp,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable (tint: Color) -> Unit,
) {
    // 選択＝ピンク→紫グラデ＋白アイコン／非選択＝白地＋濃い紫インク（P）
    val bg = if (selected) Brush.linearGradient(listOf(Design.Pink, Design.Purple)) else Brush.linearGradient(listOf(Design.BtnFace, Design.BtnFace))
    val tint = if (selected) Color.White else Design.BarInk
    Box(
        modifier = Modifier
            .size(size)
            .background(bg, CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) Design.Ink else Design.BarLine, CircleShape)
            .graphicsAlpha(if (enabled) 1f else 0.35f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) { content(tint) }
}

/** スタンプボタン（選択中スタンプのサムネを丸ボタンに表示・タップでピッカー）。 */
@Composable
private fun StampButton(stamp: Stamp, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                if (selected) Brush.linearGradient(listOf(Design.Pink, Design.Purple))
                else Brush.linearGradient(listOf(Design.BtnFace, Design.BtnFace)),
                CircleShape,
            )
            .border(if (selected) 3.dp else 1.dp, if (selected) Design.Ink else Design.BarLine, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(stamp.res),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    // v2.1: 8色×2段が幅360dpに収まるよう固定サイズ（36dp）。選択は枠の太さ/色で示す。
    Box(
        modifier = Modifier
            .size(36.dp)
            // 選択枠は新インク（濃い紫）で明るいバー上でも視認（P）
            .background(color, CircleShape)
            .border(if (selected) 4.dp else 2.dp, if (selected) Design.BarInk else Design.BarLine, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
    )
}

@Composable
private fun SizeDot(key: String, selected: Boolean, onClick: () -> Unit) {
    // v2.1: 大中小の差を一目で分かるようドット径差を拡大（12/22/34dp）。
    val dot = when (key) { "small" -> 12.dp; "big" -> 34.dp; else -> 22.dp }
    // 選択＝紫地＋白ドット／非選択＝白地＋濃い紫ドット（P・バーのインクに整合）
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(if (selected) Design.Purple.copy(alpha = 0.85f) else Design.BtnFace, CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) Design.Ink else Design.BarLine, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(dot).background(if (selected) Color.White else Design.BarInk, CircleShape))
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.size(width = 1.dp, height = 36.dp).background(Design.BarLine))
}

// ---- 描画レイヤー（base bitmap + 星 + 粒） ----
private fun Modifier.drawWithContentLayer(
    engine: DrawEngine,
    particles: ParticleSystem,
    stars: List<Ambient>,
    frame: androidx.compose.runtime.MutableLongState,
    startNs: androidx.compose.runtime.MutableLongState,
    theme: BgTheme,
): Modifier = this.drawBehind {
    val f = frame.longValue                                  // 読むことで毎フレーム再描画される
    val time = (f - startNs.longValue) / 1_000_000_000f
    val w = size.width; val h = size.height
    // 背景画像（画像テーマのみ・centerCrop済みを毎フレーム描画。よぞらはnullでグラデ地に任せる）
    drawIntoCanvas { c ->
        engine.backgroundBitmap?.let { c.nativeCanvas.drawBitmap(it, 0f, 0f, null) }
    }
    // base bitmap
    drawIntoCanvas { c ->
        engine.bitmap?.let { c.nativeCanvas.drawBitmap(it, 0f, 0f, null) }
    }
    // 背景の環境エフェクト（テーマ連動・解析計算なので粒上限500とは別枠・60fps維持）
    when (theme.ambient) {
        AmbientKind.STARS -> stars.forEach { s ->
            // 瞬く星
            val a = 0.35f + 0.35f * (0.5f + 0.5f * sin(time * s.sp + s.ph))
            drawCircle(Design.Star.copy(alpha = a), s.r, Offset(s.nx * w, s.ny * h))
        }
        AmbientKind.PETALS -> stars.forEachIndexed { i, s ->
            // 明るい地の上をゆっくり漂い落ちる花びら（白・薄ピンク・薄黄）
            val fall = 0.018f + s.sp * 0.012f
            val yy = (s.ny + time * fall) % 1f
            val cx = s.nx * w + sin(time * 0.5f + s.ph) * 22f   // 左右にゆらゆら
            val cy = yy * h
            val col = when (i % 3) { 0 -> Design.PetalWhite; 1 -> Design.PetalPink; else -> Design.PetalYellow }
            val a = (0.5f + 0.3f * (0.5f + 0.5f * sin(time * 0.9f + s.ph))).coerceIn(0f, 1f)
            val pw = 2.4f + s.r * 2.2f      // 花びらの半幅
            val ph = 3.6f + s.r * 3.4f      // 花びらの半高（縦長）
            rotate(time * 40f * s.sp + s.ph * 57.2958f, Offset(cx, cy)) {
                drawOval(col.copy(alpha = a), Offset(cx - pw, cy - ph), Size(pw * 2f, ph * 2f))
            }
        }
        AmbientKind.BUBBLES -> stars.forEach { s ->
            // 下から上へのぼる泡（白＋薄青の縁取り・明色地でも見える）
            val speed = 0.03f + s.sp * 0.02f
            val yy = ((s.ny - time * speed) % 1f + 1f) % 1f
            val cx = s.nx * w + sin(time * 0.8f + s.ph) * 6f
            val cy = yy * h
            val rr = 5f + s.r * 4f
            val a = (0.42f + 0.2f * (0.5f + 0.5f * sin(time * 1.2f + s.ph))).coerceIn(0f, 1f)
            drawCircle(Design.Bubble.copy(alpha = a * 0.5f), rr, Offset(cx, cy))                             // 白い中身
            drawCircle(Design.BubbleEdge.copy(alpha = a), rr, Offset(cx, cy), style = Stroke(width = 2f))    // 薄青の縁
            drawCircle(Design.Bubble.copy(alpha = a), rr * 0.28f, Offset(cx - rr * 0.3f, cy - rr * 0.3f))    // ハイライト
        }
        AmbientKind.GOLD_TWINKLE -> stars.forEachIndexed { i, s ->
            // ほしぞら: 金/白のやわらかい瞬き（その場でふわっと明滅・十字のきらめき）
            val tw = (0.5f + 0.5f * sin(time * (0.7f + s.sp) + s.ph))
            val a = (0.25f + 0.55f * tw).coerceIn(0f, 1f)
            val col = if (i % 3 == 0) Design.TwinkleWhite else Design.TwinkleGold
            val cx = s.nx * w; val cy = s.ny * h
            val rr = s.r * (1.1f + tw * 0.8f)
            drawCircle(col.copy(alpha = a), rr, Offset(cx, cy))
            drawCircle(col.copy(alpha = a * 0.3f), rr * 2.4f, Offset(cx, cy))   // やわらかいハロー
            // 十字のきらめき
            val cross = rr * 2.6f
            drawLine(col.copy(alpha = a * 0.7f), Offset(cx - cross, cy), Offset(cx + cross, cy), strokeWidth = 1.2f, cap = StrokeCap.Round)
            drawLine(col.copy(alpha = a * 0.7f), Offset(cx, cy - cross), Offset(cx, cy + cross), strokeWidth = 1.2f, cap = StrokeCap.Round)
        }
        AmbientKind.PASTEL_DRIFT -> stars.forEachIndexed { i, s ->
            // にじ: パステル色の粒がゆっくり漂う（横に流れつつ上下にゆらぐ）
            val drift = 0.01f + s.sp * 0.01f
            val xx = (s.nx + time * drift) % 1f
            val cx = xx * w
            val cy = s.ny * h + sin(time * 0.6f + s.ph) * 20f
            val col = Design.PastelDrift[i % Design.PastelDrift.size]
            val a = (0.4f + 0.3f * (0.5f + 0.5f * sin(time * 0.8f + s.ph))).coerceIn(0f, 1f)
            val rr = 2.6f + s.r * 3f
            drawCircle(col.copy(alpha = a), rr, Offset(cx, cy))
            drawCircle(col.copy(alpha = a * 0.35f), rr * 2.2f, Offset(cx, cy))
        }
    }
    // パーティクル
    for (p in particles.items) {
        val a = (p.life * (0.55f + 0.45f * sin(time * p.tw + p.ph))).coerceIn(0f, 1f)
        if (a <= 0f) continue
        val col = Color(p.colorArgb).copy(alpha = a)
        val s = p.size * (0.5f + p.life * 0.7f)
        if (p.cross) {
            rotate(p.rot * 57.2958f, Offset(p.x, p.y)) {
                drawLine(col, Offset(p.x - s * 2, p.y), Offset(p.x + s * 2, p.y), strokeWidth = 1.6f, cap = StrokeCap.Round)
                drawLine(col, Offset(p.x, p.y - s * 2), Offset(p.x, p.y + s * 2), strokeWidth = 1.6f, cap = StrokeCap.Round)
            }
        } else {
            drawCircle(col, s, Offset(p.x, p.y))
            drawCircle(col.copy(alpha = a * 0.35f), s * 2.2f, Offset(p.x, p.y))
        }
    }
}
