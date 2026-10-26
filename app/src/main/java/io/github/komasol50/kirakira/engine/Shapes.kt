package io.github.komasol50.kirakira.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * オリジナルのベクター形状。実在キャラクターは使わない（王冠/リボン/ちょうちょは自作の簡易造形）。
 * すべて中心(cx,cy)・大きさsize基準。base canvas とツールボタン描画の双方で共有する。
 */
object Shapes {

    /** 5つ星の Path（中心cx,cy・外半径r・回転rot ラジアン）。 */
    fun star(cx: Float, cy: Float, r: Float, rot: Float): Path {
        val p = Path()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.45f
            val a = rot + i * (PI / 5).toFloat() - (PI / 2).toFloat()
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    /** ハートの Path（中心cx,cy・大きさr・回転rot）。 */
    fun heart(cx: Float, cy: Float, r: Float, rot: Float): Path {
        val p = Path()
        val s = r / 14f
        // 原点中心で組み立ててから移動・回転・拡大
        p.moveTo(0f, 5f)
        p.cubicTo(-1f, 1f, -6f, -6f, -12f, -2f)
        p.cubicTo(-16f, 1f, -13f, 8f, 0f, 14f)
        p.cubicTo(13f, 8f, 16f, 1f, 12f, -2f)
        p.cubicTo(6f, -6f, 1f, 1f, 0f, 5f)
        p.close()
        val m = android.graphics.Matrix()
        m.postScale(s, s)
        m.postRotate(rot * 180f / PI.toFloat())
        m.postTranslate(cx, cy)
        p.transform(m)
        return p
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun fill(canvas: Canvas, path: Path, color: Int, glowColor: Int, glow: Float) {
        if (glow > 0f) {
            paint.setShadowLayer(glow, 0f, 0f, glowColor)
        } else {
            paint.clearShadowLayer()
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawPath(path, paint)
        paint.clearShadowLayer()
    }

    /** 王冠（ゴールド＋宝石）。 */
    fun drawCrown(canvas: Canvas, cx: Float, cy: Float, size: Float, glow: Int) {
        val s = size / 20f
        val body = Path().apply {
            moveTo(cx - 11 * s, cy + 8 * s)
            lineTo(cx - 12 * s, cy - 6 * s)
            lineTo(cx - 4 * s, cy + 1 * s)
            lineTo(cx, cy - 9 * s)
            lineTo(cx + 4 * s, cy + 1 * s)
            lineTo(cx + 12 * s, cy - 6 * s)
            lineTo(cx + 11 * s, cy + 8 * s)
            close()
        }
        fill(canvas, body, Color.parseColor("#FFD86B"), glow, size * 0.5f)
        val base = Path().apply {
            moveTo(cx - 11 * s, cy + 8 * s)
            lineTo(cx + 11 * s, cy + 8 * s)
            lineTo(cx + 10 * s, cy + 13 * s)
            lineTo(cx - 10 * s, cy + 13 * s)
            close()
        }
        fill(canvas, base, Color.parseColor("#FFC24B"), 0, 0f)
        paint.style = Paint.Style.FILL
        paint.clearShadowLayer()
        paint.color = Color.parseColor("#FF6FC0"); canvas.drawCircle(cx, cy + 3 * s, 2.6f * s, paint)
        paint.color = Color.parseColor("#6FB6FF"); canvas.drawCircle(cx - 6 * s, cy + 4 * s, 2f * s, paint)
        paint.color = Color.parseColor("#B77BFF"); canvas.drawCircle(cx + 6 * s, cy + 4 * s, 2f * s, paint)
    }

    /** リボン（ピンクの蝶結び）。 */
    fun drawRibbon(canvas: Canvas, cx: Float, cy: Float, size: Float, glow: Int) {
        val s = size / 20f
        val left = Path().apply {
            moveTo(cx, cy)
            lineTo(cx - 13 * s, cy - 8 * s)
            lineTo(cx - 13 * s, cy + 8 * s)
            close()
        }
        val right = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + 13 * s, cy - 8 * s)
            lineTo(cx + 13 * s, cy + 8 * s)
            close()
        }
        fill(canvas, left, Color.parseColor("#FF6FC0"), glow, size * 0.5f)
        fill(canvas, right, Color.parseColor("#FF6FC0"), glow, size * 0.5f)
        paint.style = Paint.Style.FILL
        paint.clearShadowLayer()
        paint.color = Color.parseColor("#FF9AD4")
        canvas.drawCircle(cx, cy, 3.2f * s, paint)
    }

    /** ちょうちょ（パープルの羽）。 */
    fun drawButterfly(canvas: Canvas, cx: Float, cy: Float, size: Float, glow: Int) {
        val s = size / 20f
        paint.style = Paint.Style.FILL
        val topL = Path().apply { addOval(cx - 12 * s, cy - 11 * s, cx - 1 * s, cy - 1 * s, Path.Direction.CW) }
        val topR = Path().apply { addOval(cx + 1 * s, cy - 11 * s, cx + 12 * s, cy - 1 * s, Path.Direction.CW) }
        val botL = Path().apply { addOval(cx - 10 * s, cy + 1 * s, cx - 1 * s, cy + 11 * s, Path.Direction.CW) }
        val botR = Path().apply { addOval(cx + 1 * s, cy + 1 * s, cx + 10 * s, cy + 11 * s, Path.Direction.CW) }
        fill(canvas, topL, Color.parseColor("#B77BFF"), glow, size * 0.5f)
        fill(canvas, topR, Color.parseColor("#B77BFF"), glow, size * 0.5f)
        fill(canvas, botL, Color.parseColor("#FF6FC0"), 0, 0f)
        fill(canvas, botR, Color.parseColor("#FF6FC0"), 0, 0f)
        paint.clearShadowLayer()
        paint.color = Color.parseColor("#3A1E66")
        paint.strokeWidth = 2f * s
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(cx - 1f * s, cy - 10 * s, cx + 1f * s, cy + 10 * s, 2f * s, 2f * s, paint)
    }
}
