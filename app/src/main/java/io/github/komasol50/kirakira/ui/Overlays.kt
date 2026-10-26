package io.github.komasol50.kirakira.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.komasol50.kirakira.engine.BgTheme
import io.github.komasol50.kirakira.engine.Design
import io.github.komasol50.kirakira.engine.DrawEngine
import io.github.komasol50.kirakira.engine.Stamp
import java.io.File
import kotlin.random.Random

// ==== 共通部品 ====

/** 全画面スクリム。[onDismiss] が非nullなら外側タップで閉じる（null時はタッチを吸収して背後を保護）。 */
@Composable
private fun FullScreenScrim(onDismiss: (() -> Unit)?, content: @Composable () -> Unit) {
    val base = Modifier.fillMaxSize().background(Design.Scrim)
    val m = if (onDismiss != null) {
        base.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
    } else {
        base.consumeTouches()
    }
    Box(modifier = m, contentAlignment = Alignment.Center) { content() }
}

/** 中央のメニューカード。自身のタップは吸収（背後スクリムのdismissを誘発しない）。 */
@Composable
private fun MenuCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Design.CardBg)
            .border(2.dp, Design.Gold.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** 大きな押しやすいボタン（幅は呼び出し側で指定）。 */
@Composable
private fun BigButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Brush = Brush.horizontalGradient(listOf(Design.Pink, Design.Purple)),
    fontSize: Int = 20,
    height: Int = 60,
) {
    Box(
        modifier = modifier
            .height(height.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(2.dp, Design.BarLine, RoundedCornerShape(18.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Design.Ink, fontWeight = FontWeight.Black, fontSize = fontSize.sp, textAlign = TextAlign.Center)
    }
}

/** 単色ブラシ（控えめボタン用）。 */
private fun solid(c: androidx.compose.ui.graphics.Color) = Brush.linearGradient(listOf(c, c))

// ==== メニュー ====

@Composable
fun MenuOverlay(
    engine: DrawEngine,
    onGallery: () -> Unit,
    onBgSelect: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    FullScreenScrim(onDismiss = onClose) {
        MenuCard {
            Text(
                "✨ キラキラおえかき ✨",
                color = Design.Ink, fontWeight = FontWeight.Black, fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
            BigButton("ギャラリー", onGallery, Modifier.fillMaxWidth())
            BigButton("はいけいを かえる", onBgSelect, Modifier.fillMaxWidth())
            BigButton(if (engine.soundOn) "おと オン" else "おと オフ", { engine.toggleSound() }, Modifier.fillMaxWidth())
            // 「ぜんぶ けす」は控えめ色（誤タップを誘わない）
            BigButton("ぜんぶ けす", onReset, Modifier.fillMaxWidth(), bg = solid(Design.ButtonBg), fontSize = 18, height = 54)
            BigButton("とじる", onClose, Modifier.fillMaxWidth(), bg = solid(Design.ButtonBg))
        }
    }
}

// ==== 「ぜんぶ けす」確認 ====

@Composable
fun ConfirmResetOverlay(onConfirm: () -> Unit, onCancel: () -> Unit) {
    FullScreenScrim(onDismiss = onCancel) {
        MenuCard {
            Text(
                "ほんとうに\nぜんぶ けす？",
                color = Design.Ink, fontWeight = FontWeight.Black, fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 【やめる】を視覚的に優勢に（大きく・明るく）
                BigButton("やめる", onCancel, Modifier.weight(1.7f), fontSize = 22, height = 68)
                // 【けす】は控えめ（小さく・くすんだ色）
                BigButton("けす", onConfirm, Modifier.weight(1f), bg = solid(Design.ButtonBg), fontSize = 15, height = 52)
            }
        }
    }
}

// ==== はいけい選択 ====

@Composable
fun BgSelectOverlay(engine: DrawEngine, onBack: () -> Unit) {
    val ctx = LocalContext.current
    // 開いた時点のテーマを記憶（やめる/システム戻るで復元・v2.3・M）
    val original = remember { engine.theme }
    fun cancel() { engine.previewTheme(ctx, original); onBack() }
    BackHandler { cancel() }
    // 外側タップでは閉じない（OK！/やめるで明示的に決める）
    FullScreenScrim(onDismiss = null) {
        MenuCard {
            Text(
                "はいけいを えらんでね",
                color = Design.Ink, fontWeight = FontWeight.Black, fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
            // カードタップ＝後ろのキャンバスへライブプレビュー（永続化はOK！のとき）
            BgTheme.values().forEach { t ->
                ThemePreviewCard(theme = t, selected = engine.theme == t) { engine.previewTheme(ctx, t) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // OK！を大きく明るく（確定＝永続化して閉じる）
                BigButton("OK！", { engine.commitTheme(ctx); onBack() }, Modifier.weight(1.7f), fontSize = 24, height = 66)
                // やめる＝開いた時点のテーマへ戻して閉じる（控えめ）
                BigButton("やめる", { cancel() }, Modifier.weight(1f), bg = solid(Design.ButtonBg), fontSize = 16, height = 56)
            }
        }
    }
}

/** 選択カード。画像テーマは実画像サムネ、よぞらはグラデをそのままプレビュー。 */
@Composable
private fun ThemePreviewCard(theme: BgTheme, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .drawBackground(theme)
            .border(
                if (selected) 4.dp else 2.dp,
                if (selected) Design.Gold else Design.BarLine,
                RoundedCornerShape(16.dp),
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 画像テーマは実画像のサムネイル（centerCropで全面）
        theme.imageRes?.let { res ->
            Image(
                painter = painterResource(res),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 明るい画像の上でも読めるよう、名前は暗いピルの上に置く
        Box(
            modifier = Modifier
                .padding(start = 14.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Design.Scrim)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                theme.jpName,
                color = Design.Ink, fontWeight = FontWeight.Black, fontSize = 18.sp,
            )
        }
        if (selected) {
            Text(
                "✓",
                color = Design.Gold, fontWeight = FontWeight.Black, fontSize = 22.sp,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
            )
        }
    }
}

// ==== アプリ内ギャラリー ====

@Composable
fun GalleryOverlay(engine: DrawEngine, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val files = remember { engine.galleryFiles(ctx) }
    var viewing by remember { mutableStateOf<File?>(null) }

    FullScreenScrim(onDismiss = null) {
        val v = viewing
        if (v != null) {
            FullImageView(file = v, onBack = { viewing = null })
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "ギャラリー",
                    color = Design.Ink, fontWeight = FontWeight.Black, fontSize = 22.sp,
                )
                if (files.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "まだ ほぞんした えが ないよ",
                            color = Design.Ink, fontWeight = FontWeight.Black, fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(files) { f -> GalleryThumb(f) { viewing = f } }
                    }
                }
                BigButton("もどる", onBack, Modifier.widthIn(min = 180.dp).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GalleryThumb(file: File, onClick: () -> Unit) {
    val bmp = remember(file.path) { decodeSampled(file, 260, 260)?.asImageBitmap() }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Design.NightDeep)
            .border(2.dp, Design.BarLine, RoundedCornerShape(12.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FullImageView(file: File, onBack: () -> Unit) {
    val bmp = remember(file.path) { decodeSampled(file, 1440, 1440)?.asImageBitmap() }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(
                bitmap = bmp, contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 20.dp),
        ) {
            BigButton("もどる", onBack, Modifier.width(180.dp))
        }
    }
}

/** PNGを表示サイズへ間引きデコード（メインスレッドの負荷を抑える）。 */
private fun decodeSampled(file: File, reqW: Int, reqH: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outHeight / sample > reqH || bounds.outWidth / sample > reqW) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.absolutePath, opts)
    } catch (_: Throwable) {
        null
    }
}

// ==== スタンプピッカー ====

/** スタンプ12種を4列×3行グリッドで並べる全画面ピッカー。選ぶと閉じてスタンプモードへ。 */
@Composable
fun StampPickerOverlay(engine: DrawEngine, onPick: (Stamp) -> Unit, onBack: () -> Unit) {
    FullScreenScrim(onDismiss = null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "スタンプを えらんでね",
                color = Design.Ink, fontWeight = FontWeight.Black, fontSize = 22.sp,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(Stamp.values()) { s ->
                    StampCell(stamp = s, selected = engine.lastStamp == s) { onPick(s) }
                }
            }
            BigButton("もどる", onBack, Modifier.widthIn(min = 180.dp).fillMaxWidth())
        }
    }
}

// ==== 休憩ロック（あそびタイマー・v2.6・S） ====

/**
 * 「きゅうけいしてね」全画面オーバーレイ。全タッチをブロックし、絵はそのまま保持する。
 * 解除は 2桁＋2桁（10〜99）の足し算。正解で [onUnlock]（累積リセット＋お祝い）。
 * システム戻るでも抜けられないよう BackHandler で吸収する。
 */
@Composable
fun BreakOverlay(engine: DrawEngine, onUnlock: () -> Unit) {
    var a by remember { mutableStateOf(Random.nextInt(10, 100)) }
    var b by remember { mutableStateOf(Random.nextInt(10, 100)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    fun newProblem() {
        a = Random.nextInt(10, 100)
        b = Random.nextInt(10, 100)
        input = ""
    }
    fun onDigit(d: Int) {
        if (input.length < 3) input += d.toString()
        wrong = false
    }
    fun onClear() { input = ""; wrong = false }
    fun onOk() {
        val ans = input.toIntOrNull()
        if (ans != null && ans == a + b) {
            onUnlock()
        } else {
            wrong = true
            engine.wrongTone()
            newProblem()
        }
    }

    // システム戻るで休憩を抜けられないように消費（何もしない）。
    BackHandler {}
    FullScreenScrim(onDismiss = null) {
        MenuCard {
            Text(
                "🌙 きゅうけいしてね ⭐",
                color = Design.Ink, fontWeight = FontWeight.Black, fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "めを やすめよう。\nおとなの ひとに みせてね",
                color = Design.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
            // 足し算の問題と入力欄
            Text(
                "$a ＋ $b ＝",
                color = Design.Ink, fontWeight = FontWeight.Black, fontSize = 26.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Design.BtnFace)
                    .border(2.dp, if (wrong) Design.PinkDeep else Design.BarLine, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (input.isEmpty()) "？" else input,
                    color = Design.BarInk, fontWeight = FontWeight.Black, fontSize = 28.sp,
                )
            }
            if (wrong) {
                Text(
                    "ちがうよ。もういちど！",
                    color = Design.PinkDeep, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // 数字キーパッド（1-9・けす・0・OK）
            NumRow { NumKey("1") { onDigit(1) }; NumKey("2") { onDigit(2) }; NumKey("3") { onDigit(3) } }
            NumRow { NumKey("4") { onDigit(4) }; NumKey("5") { onDigit(5) }; NumKey("6") { onDigit(6) } }
            NumRow { NumKey("7") { onDigit(7) }; NumKey("8") { onDigit(8) }; NumKey("9") { onDigit(9) } }
            NumRow {
                NumKey("けす", bg = solid(Design.ButtonBg)) { onClear() }
                NumKey("0") { onDigit(0) }
                NumKey("OK", bg = Brush.horizontalGradient(listOf(Design.Pink, Design.Purple)), ink = Color.White) { onOk() }
            }
        }
    }
}

/** キーパッドの1行（3キー等幅）。 */
@Composable
private fun NumRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** キーパッドの1キー（大きく押しやすい）。 */
@Composable
private fun RowScope.NumKey(
    label: String,
    bg: Brush = Brush.horizontalGradient(listOf(Design.BtnFace, Design.BtnFace)),
    ink: Color = Design.BarInk,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(2.dp, Design.BarLine, RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
    }
}

/** ピッカーの1マス（画像サムネ＋ひらがな名）。 */
@Composable
private fun StampCell(stamp: Stamp, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(16.dp))
            .background(Design.CardBg)
            .border(
                if (selected) 3.dp else 2.dp,
                if (selected) Design.Gold else Design.BarLine,
                RoundedCornerShape(16.dp),
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(stamp.res),
            contentDescription = stamp.jpName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Text(
            stamp.jpName,
            color = Design.Ink, fontWeight = FontWeight.Bold, fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
