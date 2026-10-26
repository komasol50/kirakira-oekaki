package io.github.komasol50.kirakira.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.sin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.github.komasol50.kirakira.engine.Brush
import io.github.komasol50.kirakira.engine.Design
import io.github.komasol50.kirakira.engine.Shapes
import io.github.komasol50.kirakira.engine.Stamp

private val gold = Design.Gold
private val pink = Design.Pink
private val purple = Design.Purple

/**
 * バーのアイコン。[tint] は地色に合わせたインク（白地＝濃い紫 Design.BarInk／選択の
 * グラデ地＝白）。ブラシ4種は v2.3 で「線見本（短い波線）」に描き直し（feedback 14）。
 */
@Composable
fun IconGlyph(kind: String, modifier: Modifier = Modifier.size(28.dp), tint: Color = Design.BarInk) {
    Canvas(modifier = modifier) {
        when (kind) {
            "sparkle" -> brushSparkle(this, tint)
            "star" -> brushStar(this, tint)
            "heart" -> brushHeart(this, tint)
            "rainbow" -> brushRainbow(this)
            "eraser" -> eraser(this, tint)
            "sound_on" -> speaker(this, true, tint)
            "sound_off" -> speaker(this, false, tint)
            "undo" -> undoRedo(this, tint, forward = false)
            "redo" -> undoRedo(this, tint, forward = true)
            "wand" -> wand(this, tint)
            "camera" -> camera(this, tint)
            "menu" -> menu(this, tint)
        }
    }
}

/** 短い波線の Path（アイコン内を左右に横切る sine 波・線見本の土台）。 */
private fun wavePath(ds: DrawScope, amp: Float = 0.15f): androidx.compose.ui.graphics.Path {
    val w = ds.size.width; val h = ds.size.height
    val x0 = w * 0.16f; val x1 = w * 0.84f; val cy = h * 0.5f
    val a = h * amp
    val p = androidx.compose.ui.graphics.Path()
    val steps = 24
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val x = x0 + (x1 - x0) * t
        val y = cy + sin(t * (2f * PI_F) * 1.25f) * a
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    return p
}

/** 波線上の等間隔サンプル点（星・ハートの小飾りを載せる位置）。 */
private fun wavePoints(ds: DrawScope, n: Int): List<Offset> {
    val w = ds.size.width; val h = ds.size.height
    val x0 = w * 0.16f; val x1 = w * 0.84f; val cy = h * 0.5f
    val a = h * 0.15f
    return (0 until n).map { i ->
        val t = (i + 0.5f) / n
        val x = x0 + (x1 - x0) * t
        val y = cy + sin(t * (2f * PI_F) * 1.25f) * a
        Offset(x, y)
    }
}

private const val PI_F = Math.PI.toFloat()

private fun androidPaint(argb: Int) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color = argb; style = android.graphics.Paint.Style.FILL
}

private fun nativeShape(ds: DrawScope, block: (android.graphics.Canvas, Float, Float, Float) -> Unit) {
    val cx = ds.size.width / 2f; val cy = ds.size.height / 2f
    val r = ds.size.minDimension * 0.42f
    ds.drawIntoCanvas { block(it.nativeCanvas, cx, cy, r) }
}

// ---- ブラシ4種の線見本（v2.3・N）：短い波線＝「線として描いた結果」が分かる ----

/** キラキラ: 波線＋小さな光の粒。 */
private fun brushSparkle(ds: DrawScope, tint: Color) = with(ds) {
    val sw = size.minDimension * 0.1f
    drawPath(wavePath(ds), tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    // 波線に沿う小さな光の粒（きらめき）
    wavePoints(ds, 3).forEachIndexed { i, o ->
        val r = size.minDimension * (0.07f - i % 2 * 0.02f)
        drawCircle(tint, r, Offset(o.x, o.y - size.minDimension * 0.2f))
    }
    drawCircle(tint.copy(alpha = 0.7f), size.minDimension * 0.05f, Offset(size.width * 0.82f, size.height * 0.24f))
}

/** ほし: 波線に沿って小さな星3つ。 */
private fun brushStar(ds: DrawScope, tint: Color) = with(ds) {
    val sw = size.minDimension * 0.09f
    drawPath(wavePath(ds), tint.copy(alpha = 0.85f), style = Stroke(width = sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawIntoCanvas { c ->
        val p = androidPaint(tint.toArgb())
        wavePoints(ds, 3).forEach { o ->
            c.nativeCanvas.drawPath(Shapes.star(o.x, o.y, size.minDimension * 0.14f, 0f), p)
        }
    }
}

/** ハート: 波線に沿って小さなハート3つ。 */
private fun brushHeart(ds: DrawScope, tint: Color) = with(ds) {
    val sw = size.minDimension * 0.09f
    drawPath(wavePath(ds), tint.copy(alpha = 0.85f), style = Stroke(width = sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawIntoCanvas { c ->
        val p = androidPaint(tint.toArgb())
        wavePoints(ds, 3).forEach { o ->
            c.nativeCanvas.drawPath(Shapes.heart(o.x, o.y, size.minDimension * 0.16f, 0f), p)
        }
    }
}

/** にじ: 多色の波線（虹色を少しずつずらして重ねる）。 */
private fun brushRainbow(ds: DrawScope) = with(ds) {
    val sw = size.minDimension * 0.085f
    val cols = listOf(Color(0xFFFF5A7E), Color(0xFFFFB347), Color(0xFF7BF0C9), Color(0xFF6FB6FF), Color(0xFFB583FF))
    cols.forEachIndexed { i, c ->
        val dy = (i - (cols.size - 1) / 2f) * size.minDimension * 0.075f
        translate(0f, dy) {
            drawPath(wavePath(ds, amp = 0.13f), c, style = Stroke(width = sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

// ---- 消しゴム（v2.3・O）：消しゴム形＋消し跡の白線 ----
private fun eraser(ds: DrawScope, tint: Color) = with(ds) {
    val u = size.minDimension; val cx = size.width / 2f; val cy = size.height * 0.46f
    // 斜めに傾けた消しゴム本体
    rotate(-24f, Offset(cx, cy)) {
        val w = u * 0.62f; val h = u * 0.42f
        drawRoundRect(
            tint, topLeft = Offset(cx - w / 2, cy - h / 2),
            size = androidx.compose.ui.geometry.Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * 0.08f, u * 0.08f),
        )
        // 消しゴムの帯（白の差し色）
        drawRoundRect(
            Color.White.copy(alpha = 0.85f), topLeft = Offset(cx - w / 2, cy + h * 0.08f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.34f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * 0.06f, u * 0.06f),
        )
    }
    // 消し跡の白線（下に短い横線を3本）
    for (i in 0..2) {
        val y = size.height * 0.78f + i * u * 0.09f
        val x0 = cx - u * 0.3f + i * u * 0.08f
        drawLine(tint.copy(alpha = 0.6f), Offset(x0, y), Offset(x0 + u * 0.4f, y), strokeWidth = u * 0.05f, cap = StrokeCap.Round)
    }
}

private fun speaker(ds: DrawScope, on: Boolean, tint: Color) = with(ds) {
    val u = size.minDimension
    val cx = size.width * 0.38f; val cy = size.height / 2f
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx - u * 0.22f, cy - u * 0.1f)
        lineTo(cx - u * 0.05f, cy - u * 0.1f)
        lineTo(cx + u * 0.12f, cy - u * 0.26f)
        lineTo(cx + u * 0.12f, cy + u * 0.26f)
        lineTo(cx - u * 0.05f, cy + u * 0.1f)
        lineTo(cx - u * 0.22f, cy + u * 0.1f)
        close()
    }
    drawPath(path, tint)
    if (on) {
        for (i in 1..2) {
            val rad = u * (0.14f + i * 0.12f)
            drawArc(
                tint, -50f, 100f, false,
                topLeft = Offset(cx + u * 0.16f - rad, cy - rad),
                size = androidx.compose.ui.geometry.Size(rad * 2, rad * 2),
                style = Stroke(width = u * 0.06f, cap = StrokeCap.Round),
            )
        }
    } else {
        drawLine(Design.Pink, Offset(cx + u * 0.18f, cy - u * 0.22f), Offset(cx + u * 0.42f, cy + u * 0.22f), strokeWidth = u * 0.08f, cap = StrokeCap.Round)
    }
}

/** もどる/すすむ。[forward]=true で左右反転（すすむ）。 */
private fun undoRedo(ds: DrawScope, tint: Color, forward: Boolean) = with(ds) {
    val u = size.minDimension; val cx = size.width / 2f; val cy = size.height / 2f
    val sx = if (forward) -1f else 1f   // すすむは水平反転
    scale(sx, 1f, Offset(cx, cy)) {
        val rad = u * 0.32f
        drawArc(
            tint, 20f, 280f, false,
            topLeft = Offset(cx - rad, cy - rad),
            size = androidx.compose.ui.geometry.Size(rad * 2, rad * 2),
            style = Stroke(width = u * 0.11f, cap = StrokeCap.Round),
        )
        // 矢じり（開始端＝右上あたり）
        val ax = cx + rad * 0.94f; val ay = cy + rad * 0.34f
        val tri = androidx.compose.ui.graphics.Path().apply {
            moveTo(ax, ay)
            lineTo(ax - u * 0.18f, ay - u * 0.02f)
            lineTo(ax - u * 0.02f, ay + u * 0.17f)
            close()
        }
        drawPath(tri, tint)
    }
}

private fun wand(ds: DrawScope, tint: Color) = with(ds) {
    val u = size.minDimension; val cx = size.width / 2f; val cy = size.height / 2f
    drawLine(tint, Offset(cx - u * 0.28f, cy + u * 0.34f), Offset(cx + u * 0.12f, cy - u * 0.12f), strokeWidth = u * 0.11f, cap = StrokeCap.Round)
    drawIntoCanvas {
        it.nativeCanvas.drawPath(Shapes.star(cx + u * 0.2f, cy - u * 0.22f, u * 0.2f, 0f), androidPaint(tint.toArgb()))
    }
    drawCircle(Design.Pink, u * 0.05f, Offset(cx - u * 0.05f, cy + u * 0.05f))
    drawCircle(tint, u * 0.04f, Offset(cx + u * 0.36f, cy + u * 0.08f))
}

private fun menu(ds: DrawScope, tint: Color) = with(ds) {
    // 3本線＋右上に小さなキラリ（メニューの目印）
    val u = size.minDimension; val cx = size.width / 2f; val cy = size.height / 2f
    val wLine = u * 0.52f
    val sw = u * 0.11f
    for (i in -1..1) {
        val y = cy + i * u * 0.22f
        drawLine(tint, Offset(cx - wLine / 2, y), Offset(cx + wLine / 2, y), strokeWidth = sw, cap = StrokeCap.Round)
    }
    drawIntoCanvas {
        it.nativeCanvas.drawPath(Shapes.star(cx + wLine * 0.62f, cy - u * 0.32f, u * 0.14f, 0f), androidPaint(tint.toArgb()))
    }
}

private fun camera(ds: DrawScope, tint: Color) = with(ds) {
    val u = size.minDimension; val cx = size.width / 2f; val cy = size.height * 0.54f
    val w = u * 0.7f; val h = u * 0.5f
    drawRoundRect(
        tint, topLeft = Offset(cx - w / 2, cy - h / 2),
        size = androidx.compose.ui.geometry.Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * 0.12f, u * 0.12f),
    )
    // ファインダーの出っぱり
    drawRoundRect(
        tint, topLeft = Offset(cx - u * 0.14f, cy - h / 2 - u * 0.1f),
        size = androidx.compose.ui.geometry.Size(u * 0.28f, u * 0.14f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(u * 0.05f, u * 0.05f),
    )
    drawCircle(Color.White, u * 0.17f, Offset(cx, cy))
    drawCircle(Design.Pink, u * 0.1f, Offset(cx, cy))
}
