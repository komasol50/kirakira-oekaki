package io.github.komasol50.kirakira.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.komasol50.kirakira.engine.Design

// このアプリは「夜空」の世界観1本。常に暗いカラースキームで固定する（ライト切替は持たない）。
private val KirakiraColorScheme = darkColorScheme(
    primary = Design.Pink,
    secondary = Design.Purple,
    tertiary = Design.Gold,
    background = Design.NightDeep,
    surface = Design.NightTop,
    onPrimary = Color(0xFF2A0E3A),
    onSecondary = Color(0xFF2A0E3A),
    onBackground = Design.Ink,
    onSurface = Design.Ink,
)

@Composable
fun KirakiraTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme() // 参照だけ。配色は固定。
    MaterialTheme(
        colorScheme = KirakiraColorScheme,
        typography = KirakiraTypography,
        content = content,
    )
}
