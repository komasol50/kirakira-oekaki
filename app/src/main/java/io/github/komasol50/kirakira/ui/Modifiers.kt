package io.github.komasol50.kirakira.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import io.github.komasol50.kirakira.engine.BgTheme
import kotlin.math.min

/** 背景テーマのグラデ地（よぞら=中心やや上のラジアル／みどり・みずのなか=縦グラデ）。 */
fun Modifier.drawBackground(theme: BgTheme): Modifier = this.drawBehind {
    val brush = if (theme.radial) {
        Brush.radialGradient(
            colors = theme.gradient,
            center = Offset(size.width * 0.5f, size.height * 0.42f),
            radius = min(size.width, size.height) * 0.9f,
        )
    } else {
        Brush.verticalGradient(colors = theme.gradient)
    }
    drawRect(brush)
}

/** レイアウト確定サイズ(px)を通知。 */
fun Modifier.onSizedChanged(block: (Int, Int) -> Unit): Modifier =
    this.onSizeChanged { block(it.width, it.height) }

/** このバーの上のタッチを消費し、背後のキャンバスに描画が漏れないようにする（子ボタンは先に処理されるので有効）。 */
fun Modifier.consumeTouches(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent()
            e.changes.forEach { it.consume() }
        }
    }
}

fun Modifier.graphicsAlpha(a: Float): Modifier = this.then(Modifier.alpha(a))
