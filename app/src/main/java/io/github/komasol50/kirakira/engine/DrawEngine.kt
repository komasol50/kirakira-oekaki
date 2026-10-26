package io.github.komasol50.kirakira.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.util.ArrayDeque
import kotlin.math.hypot
import kotlin.random.Random

/**
 * お絵かきの中核。確定した線・スタンプを [base] ビットマップに焼き込み、
 * きらめきの粒は [particles] が持つ。UIから見える選択状態は Compose state で公開する。
 */
class DrawEngine(
    private val particles: ParticleSystem,
    private val sound: SoundEngine,
) {
    // ---- UIが観測する状態 ----
    var tool by mutableStateOf<Tool>(Tool.BrushTool(Brush.SPARKLE))
        private set
    var color by mutableStateOf(Design.Palette[0])
        private set
    var sizeKey by mutableStateOf("mid")
        private set
    var soundOn by mutableStateOf(true)
        private set
    var baseVersion by mutableIntStateOf(0)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)   // すすむ（redo）が可能か（v2.3）
        private set
    var theme by mutableStateOf(BgTheme.NIGHT)   // 背景テーマ（永続化・UIが観測）
        private set
    var lastStamp by mutableStateOf(Stamp.UNICORN)   // 直近に選んだスタンプ（ツール段のサムネ表示用）
        private set

    // ---- 内部状態 ----
    private var base: Bitmap? = null
    private var baseCanvas: Canvas? = null
    private var w = 0
    private var h = 0
    private val undo = ArrayDeque<Bitmap>()
    private val redo = ArrayDeque<Bitmap>()   // すすむ用（深さは MaxUndo と同じ・v2.3）
    private val strokes = HashMap<Long, StrokeState>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)  // 画像用（背景・スタンプ）
    private val rnd = Random(System.nanoTime())
    private var saveCount = 0
    private var pendingRestore: File? = null

    // ---- 画像キャッシュ（v2.2） ----
    // decodeResource には Context が要るが毎フレーム描画には渡せないので applicationContext を保持する。
    private var appCtx: Context? = null
    // 背景画像を画面サイズへ centerCrop したキャッシュ。テーマ/サイズ変更で作り直す。
    private var bgBitmap: Bitmap? = null
    private var bgKey: String? = null
    // 選択中スタンプのみサイズ別に持つキャッシュ（12種は常駐させない）。
    private val stampCache = HashMap<String, Bitmap>()
    private var stampCacheKey: Stamp? = null

    // 上下バーの実高さ(px)。CanvasScreenが測って通知（v2.5・R）。両方>0で「可視領域合わせ」有効。
    private var topBarPx = 0
    private var bottomBarPx = 0
    private val barsKnown get() = topBarPx > 0 && bottomBarPx > 0

    /** 背景画像（centerCrop済み・画像テーマ以外はnull）。UIが毎フレーム drawBitmap する。 */
    val backgroundBitmap: Bitmap? get() = bgBitmap

    private class StrokeState(var x: Float, var y: Float, var dist: Float, var hue: Float)

    val bitmap: Bitmap? get() = base

    /** 描画中のストロークがあるか（v2.6・S: 休憩表示は onUp を待つため）。 */
    val isDrawing: Boolean get() = strokes.isNotEmpty()

    // ---- 選択操作 ----
    fun selectBrush(b: Brush) { tool = Tool.BrushTool(b); sound.blip(784f) }
    fun selectStamp(s: Stamp) { tool = Tool.StampTool(s); lastStamp = s; sound.blip(784f) }
    fun selectColor(c: Color) { color = c; sound.blip(880f) }
    fun selectSize(key: String) { sizeKey = key; sound.blip(660f) }
    fun toggleSound() { soundOn = !soundOn; sound.enabled = soundOn; if (soundOn) sound.blip(1046f) }

    // ---- あそびタイマー用の効果音・演出（v2.6・S） ----
    /** 休憩開始のやさしいチャイム。 */
    fun breakChime() = sound.chime()
    /** 解除の足し算をまちがえたときの控えめな音。 */
    fun wrongTone() = sound.wrongTone()
    /** 休憩解除に成功したときのお祝い（画面いっぱいの粒＋ファンファーレ）。 */
    fun celebrateBreakCleared(width: Float, height: Float) {
        val cw = if (width > 0f) width else w.toFloat()
        val ch = if (height > 0f) height else h.toFloat()
        if (cw > 0f && ch > 0f) {
            particles.celebrate(
                cw, ch,
                intArrayOf(0xFFFF7AC8.toInt(), 0xFFB583FF.toInt(), 0xFFFFD76A.toInt(), 0xFFFFF6FD.toInt()), 90,
            )
        }
        sound.fanfare()
    }

    // ---- 背景テーマ（SharedPreferencesで永続化・新規ライブラリ不使用） ----
    fun loadTheme(ctx: Context) {
        appCtx = ctx.applicationContext
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = p.getString(KEY_THEME, null)
        // enum名 NIGHT/GREEN/WATER は据え置き→旧保存値もそのまま復元（HOSHIZORA/NIJIは新規）
        theme = BgTheme.values().firstOrNull { it.name == name } ?: BgTheme.NIGHT
        rebuildBackground()
    }

    fun setTheme(ctx: Context, t: BgTheme) {
        appCtx = ctx.applicationContext
        theme = t
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME, t.name).apply()
        } catch (_: Throwable) {}
        rebuildBackground()   // 画像テーマは選択時に1回だけデコード→centerCropしてキャッシュ
        sound.blip(880f)
    }

    /**
     * 背景選択オーバーレイのライブプレビュー（v2.3・M）。
     * 表示だけ切り替えて永続化はしない（後ろのキャンバスへ試し適用）。
     */
    fun previewTheme(ctx: Context, t: BgTheme) {
        appCtx = ctx.applicationContext
        theme = t
        rebuildBackground()
        sound.blip(880f)
    }

    /** ライブプレビューで選んだテーマを「OK！」で確定＝永続化する（v2.3・M）。 */
    fun commitTheme(ctx: Context) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME, theme.name).apply()
        } catch (_: Throwable) {}
    }

    /**
     * 背景画像テーマなら現在の画面サイズへ centerCrop したビットマップを用意する。
     * よぞら（imageRes=null）はキャッシュを空にしてグラデ地に任せる。
     * テーマ／サイズが前回と同じなら作り直さない。切替時は旧bitmapを recycle。
     */
    private fun rebuildBackground() {
        val res = theme.imageRes
        if (res == null || w <= 0 || h <= 0) {
            if (res == null) { bgBitmap?.recycle(); bgBitmap = null; bgKey = null }
            return
        }
        // 可視領域に合わせるためバー高さもキー要素にする（高さ変化・回転で作り直す）
        val key = "${theme.name}-$w-$h-$topBarPx-$bottomBarPx"
        if (bgKey == key && bgBitmap != null) return
        val ctx = appCtx ?: return
        try {
            val src = BitmapFactory.decodeResource(ctx.resources, res) ?: return
            // バー高さ未確定なら現行の全画面フォールバック
            val cropped = if (barsKnown) buildVisibleBackground(src, w, h, topBarPx, bottomBarPx)
                          else centerCropTo(src, w, h)
            src.recycle()
            bgBitmap?.recycle()
            bgBitmap = cropped
            bgKey = key
        } catch (_: Throwable) {}
    }

    /**
     * 上下バーの実高さを受け取り、可視領域に合わせて背景を作り直す（v2.5・R）。
     * 高さが変わった時だけ再構築し、毎フレーム描画のbgBitmapへ反映する。
     */
    fun setBarHeights(topPx: Int, bottomPx: Int) {
        if (topPx == topBarPx && bottomPx == bottomBarPx) return
        topBarPx = topPx
        bottomBarPx = bottomPx
        rebuildBackground()
        baseVersion++   // 背景差し替えを画面へ促す
    }

    /**
     * 画像を「上下バーの間の可視領域」へ centerCrop する（v2.5・R）。
     * 縁の飾りがバー裏に隠れないよう可視矩形へ収め、バー裏の帯は画像の平均色で無地埋め
     * （バーはほぼ不透明だがわずかな透過対策）。はみ出しは可視矩形でクリップして帯を汚さない。
     */
    private fun buildVisibleBackground(src: Bitmap, dstW: Int, dstH: Int, top: Int, bottom: Int): Bitmap {
        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(averageColor(src))
        val visTop = top.coerceIn(0, dstH)
        val visBottom = (dstH - bottom).coerceIn(visTop, dstH)
        val vh = visBottom - visTop
        if (vh > 0) {
            c.save()
            c.clipRect(0f, visTop.toFloat(), dstW.toFloat(), visBottom.toFloat())
            val scale = maxOf(dstW.toFloat() / src.width, vh.toFloat() / src.height)
            val sw = src.width * scale
            val sh = src.height * scale
            val left = (dstW - sw) / 2f
            val topY = visTop + (vh - sh) / 2f
            c.drawBitmap(src, null, android.graphics.RectF(left, topY, left + sw, topY + sh), bmpPaint)
            c.restore()
        }
        return out
    }

    /** [src] の平均色（1×1へ縮小して1画素サンプル）。帯の無地埋め用。 */
    private fun averageColor(src: Bitmap): Int = try {
        val one = Bitmap.createScaledBitmap(src, 1, 1, true)
        val c = one.getPixel(0, 0)
        if (one !== src) one.recycle()
        c
    } catch (_: Throwable) { 0xFF000000.toInt() }

    /** [src] を dstW×dstH に centerCrop（縦横比を保って埋め、はみ出しは切る）。 */
    private fun centerCropTo(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val scale = maxOf(dstW.toFloat() / src.width, dstH.toFloat() / src.height)
        val sw = src.width * scale
        val sh = src.height * scale
        val left = (dstW - sw) / 2f
        val top = (dstH - sh) / 2f
        c.drawBitmap(src, null, android.graphics.RectF(left, top, left + sw, top + sh), bmpPaint)
        return out
    }

    // ---- サイズ確定（レイアウト確定時に呼ぶ） ----
    fun ensureSize(newW: Int, newH: Int) {
        if (newW <= 0 || newH <= 0) return
        if (base != null && newW == w && newH == h) return
        val old = base
        val nb = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
        val c = Canvas(nb)
        // 復元（プロセス復帰）
        val restore = pendingRestore
        if (restore != null && restore.exists()) {
            try {
                val loaded = BitmapFactory.decodeFile(restore.absolutePath)
                if (loaded != null) {
                    c.drawBitmap(loaded, null, android.graphics.RectF(0f, 0f, newW.toFloat(), newH.toFloat()), null)
                    loaded.recycle()
                }
            } catch (_: Throwable) {}
            pendingRestore = null
        } else if (old != null) {
            // 回転などでサイズが変わった場合は引き伸ばして保持
            c.drawBitmap(old, null, android.graphics.RectF(0f, 0f, newW.toFloat(), newH.toFloat()), null)
        }
        base = nb; baseCanvas = c; w = newW; h = newH
        old?.recycle()
        rebuildBackground()   // 画面サイズ確定/変更時に背景画像を作り直す
        baseVersion++
    }

    // ---- ポインタ ----
    fun onDown(id: Long, x: Float, y: Float) {
        val c = baseCanvas ?: return
        if (strokes.isEmpty()) pushUndo()
        when (val t = tool) {
            is Tool.StampTool -> {
                drawStamp(c, t.stamp, x, y)
                particles.celebrateAt(x, y, color.toArgb())
                baseVersion++
                sound.pop()
            }
            is Tool.BrushTool -> {
                strokes[id] = StrokeState(x, y, 0f, ((rnd.nextFloat() * 360f)))
                drawSegment(c, strokes[id]!!, x + 0.01f, y + 0.01f)
                baseVersion++
            }
        }
    }

    fun onMove(id: Long, x: Float, y: Float) {
        val c = baseCanvas ?: return
        val s = strokes[id] ?: return
        drawSegment(c, s, x, y)
        baseVersion++
    }

    fun onUp(id: Long) { strokes.remove(id) }

    // ---- 描画本体 ----
    private fun currentWidth() = Design.Sizes[sizeKey] ?: 16f

    private fun drawSegment(c: Canvas, s: StrokeState, x: Float, y: Float) {
        val brush = (tool as? Tool.BrushTool)?.brush ?: return
        val wdt = currentWidth()
        var argb = color.toArgb()
        if (brush == Brush.RAINBOW) {
            s.hue = (s.hue + hypot((x - s.x).toDouble(), (y - s.y).toDouble()).toFloat() * 0.8f) % 360f
            argb = android.graphics.Color.HSVToColor(floatArrayOf(s.hue, 1f, 0.85f))
        }
        when (brush) {
            Brush.SPARKLE, Brush.RAINBOW -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                // ハロー（広い低透明）
                paint.color = withAlpha(argb, 0x38); paint.strokeWidth = wdt * 2.4f
                c.drawLine(s.x, s.y, x, y, paint)
                // 本体
                paint.color = withAlpha(argb, 0xE6); paint.strokeWidth = wdt
                c.drawLine(s.x, s.y, x, y, paint)
                // 明るい芯
                paint.color = withAlpha(0xFFFFFF, 0x99); paint.strokeWidth = wdt * 0.35f
                c.drawLine(s.x, s.y, x, y, paint)
                bakeGlitter(c, s.x, s.y, x, y, argb, wdt)
                particles.emit(x, y, argb, 2, 1f)
            }
            Brush.STAR, Brush.HEART -> {
                s.dist += hypot((x - s.x).toDouble(), (y - s.y).toDouble()).toFloat()
                val gap = wdt * 2.4f
                while (s.dist >= gap) {
                    s.dist -= gap
                    val r = wdt * (0.9f + rnd.nextFloat() * 0.5f)
                    val rot = (rnd.nextFloat() - 0.5f) * 1.2f
                    paint.style = Paint.Style.FILL
                    paint.color = argb
                    val path = if (brush == Brush.STAR) Shapes.star(x, y, r, rot) else Shapes.heart(x, y, r, rot)
                    c.drawPath(path, paint)
                    particles.emit(x, y, argb, 3, 1.6f, forceCross = brush == Brush.STAR)
                }
            }
            Brush.ERASER -> {
                // 背景が透けるよう base を PorterDuff.CLEAR で透明化。太さ＝選択サイズ×1.8。
                // ハロー/グリッタ/パーティクルなし・色選択の影響なし。
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeWidth = wdt * 1.8f
                paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                c.drawLine(s.x, s.y, x, y, paint)
                paint.xfermode = null
                sound.softErase()
                s.x = x; s.y = y
                return
            }
        }
        sound.tinkle()
        s.x = x; s.y = y
    }

    private fun bakeGlitter(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, argb: Int, wdt: Float) {
        val d = hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
        val n = (d / 5f).toInt().coerceAtLeast(1)
        paint.style = Paint.Style.FILL
        for (i in 0 until n) {
            val t = rnd.nextFloat()
            val gx = x1 + (x2 - x1) * t + (rnd.nextFloat() - 0.5f) * wdt * 1.6f
            val gy = y1 + (y2 - y1) * t + (rnd.nextFloat() - 0.5f) * wdt * 1.6f
            val a = (0x59 + (rnd.nextFloat() * 0x8C).toInt())
            paint.color = withAlpha(
                if (rnd.nextFloat() < 0.3f) 0xFFFFFF else if (rnd.nextFloat() < 0.5f) 0xFFD86B else (argb and 0xFFFFFF), a
            )
            if (rnd.nextFloat() < 0.12f) {
                c.drawPath(Shapes.star(gx, gy, 1.5f + rnd.nextFloat() * 2f, rnd.nextFloat() * 3f), paint)
            } else {
                c.drawCircle(gx, gy, 0.6f + rnd.nextFloat() * 1.2f, paint)
            }
        }
    }

    private fun drawStamp(c: Canvas, stamp: Stamp, x: Float, y: Float) {
        // v2.2: PNGスタンプを大中小=90/140/200px目安・±10°のランダム回転で配置
        val px = when (sizeKey) { "small" -> 90; "big" -> 200; else -> 140 }
        val bmp = stampBitmap(stamp, px) ?: return
        val rot = (rnd.nextFloat() - 0.5f) * 20f   // ±10°
        c.save()
        c.rotate(rot, x, y)
        c.drawBitmap(bmp, x - px / 2f, y - px / 2f, bmpPaint)
        c.restore()
    }

    /**
     * 選択中スタンプをサイズ別にデコード＆キャッシュ（10種は常駐させない）。
     * 別スタンプへ切り替わったら旧キャッシュを解放してから作り直す。
     */
    private fun stampBitmap(stamp: Stamp, px: Int): Bitmap? {
        val ctx = appCtx ?: return null
        if (stampCacheKey != stamp) {
            stampCache.values.forEach { it.recycle() }
            stampCache.clear()
            stampCacheKey = stamp
        }
        val key = "$px"
        stampCache[key]?.let { return it }
        return try {
            val src = BitmapFactory.decodeResource(ctx.resources, stamp.res) ?: return null
            val scaled = Bitmap.createScaledBitmap(src, px, px, true)
            if (scaled !== src) src.recycle()
            stampCache[key] = scaled
            scaled
        } catch (_: Throwable) { null }
    }

    // ---- undo / redo / reset ----
    private fun pushUndo() {
        val b = base ?: return
        try {
            if (undo.size >= Design.MaxUndo) undo.pollFirst()?.recycle()
            undo.addLast(b.copy(Bitmap.Config.ARGB_8888, false))
            canUndo = true
            clearRedo()   // 新しい描画操作が入ったら「すすむ」履歴は無効化
        } catch (_: Throwable) {}
    }

    /** 現在の base を [stack] へ積む（深さ MaxUndo・溢れたら古い方を recycle）。 */
    private fun pushSnapshot(stack: ArrayDeque<Bitmap>) {
        val b = base ?: return
        if (stack.size >= Design.MaxUndo) stack.pollFirst()?.recycle()
        stack.addLast(b.copy(Bitmap.Config.ARGB_8888, false))
    }

    private fun clearRedo() {
        while (redo.isNotEmpty()) redo.pollLast()?.recycle()
        canRedo = false
    }

    fun undo() {
        val c = baseCanvas ?: return
        val prev = undo.pollLast() ?: return
        try { pushSnapshot(redo); canRedo = true } catch (_: Throwable) {}   // 戻る前の状態を「すすむ」へ
        c.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
        c.drawBitmap(prev, 0f, 0f, null)
        prev.recycle()
        canUndo = undo.isNotEmpty()
        baseVersion++
        sound.blip(392f)
    }

    fun redo() {
        val c = baseCanvas ?: return
        val next = redo.pollLast() ?: return
        try { pushSnapshot(undo); canUndo = true } catch (_: Throwable) {}   // すすむ前の状態を「もどる」へ
        c.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
        c.drawBitmap(next, 0f, 0f, null)
        next.recycle()
        canRedo = redo.isNotEmpty()
        baseVersion++
        sound.blip(523f)
    }

    fun magicReset() {
        val c = baseCanvas ?: return
        pushUndo()
        c.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
        particles.celebrate(
            w.toFloat(), h.toFloat(),
            intArrayOf(0xFFFF7AC8.toInt(), 0xFFB583FF.toInt(), 0xFFFFD76A.toInt(), 0xFFFFF6FD.toInt()), 90,
        )
        sound.whoosh()
        baseVersion++
    }

    // ---- 保存/復元 ----
    /** プロセス復帰用にディスクへ退避。 */
    fun saveToDisk(ctx: Context) {
        val b = base ?: return
        try {
            File(ctx.filesDir, CANVAS_FILE).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Throwable) {}
    }

    fun markRestore(ctx: Context) {
        val f = File(ctx.filesDir, CANVAS_FILE)
        if (f.exists()) pendingRestore = f
    }

    /** 選択中テーマの背景ごと合成してフォトギャラリー＋アプリ内ギャラリーへPNG保存。成功でtrue。 */
    fun exportToGallery(ctx: Context): Boolean {
        val b = base ?: return false
        return try {
            // 画像テーマ×バー高さ確定なら「見えている範囲」だけをクロップして保存（v2.5・R）。
            // よぞら/バー未確定は現行どおり全画面。
            val useVisible = theme.imageRes != null && barsKnown
            val vTop = if (useVisible) topBarPx.coerceIn(0, b.height) else 0
            val vBottom = if (useVisible) (b.height - bottomBarPx).coerceIn(vTop, b.height) else b.height
            val outW = b.width
            val outH = vBottom - vTop
            if (outH <= 0) return false
            val srcRect = android.graphics.Rect(0, vTop, outW, vBottom)
            val dstRectF = android.graphics.RectF(0f, 0f, outW.toFloat(), outH.toFloat())
            val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val oc = Canvas(out)
            // 画像テーマは同じ centerCrop（可視領域）合成、よぞらはグラデ合成
            val bg = bgBitmap
            if (theme.imageRes != null && bg != null && bg.width == b.width && bg.height == b.height) {
                oc.drawBitmap(bg, srcRect, dstRectF, bmpPaint)
            } else if (theme.imageRes != null) {
                // 万一キャッシュがサイズ不一致なら描画時サイズへ作り直して合成
                val src = BitmapFactory.decodeResource(ctx.resources, theme.imageRes!!)
                if (src != null) {
                    val rebuilt = if (useVisible) buildVisibleBackground(src, b.width, b.height, topBarPx, bottomBarPx)
                                  else centerCropTo(src, b.width, b.height)
                    src.recycle()
                    oc.drawBitmap(rebuilt, srcRect, dstRectF, bmpPaint)
                    rebuilt.recycle()
                } else {
                    oc.drawPaint(Paint().apply { shader = backgroundShader(outW, outH) })
                }
            } else {
                oc.drawPaint(Paint().apply { shader = backgroundShader(outW, outH) })
            }
            // 描画bitmapも同じ可視矩形でクロップして重ねる（見たままが写真になる）
            oc.drawBitmap(b, srcRect, dstRectF, null)
            // アプリ内ギャラリーへ二重保存（権限不要・INTERNET不要のため内部コピー方式）
            saveInternalCopy(ctx, out)
            val name = "kirakira-${System.currentTimeMillis()}-${++saveCount}.png"
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KirakiraOekaki")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    ctx.contentResolver.openOutputStream(uri)?.use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    ctx.contentResolver.update(uri, values, null, null)
                    true
                } else false
            } else {
                // Q未満はアプリ専用のPicturesへ（権限不要・端末により本体ギャラリー非表示の場合あり）
                val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: ctx.filesDir
                val f = File(dir, name)
                f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            }
            out.recycle()
            if (ok) sound.fanfare()
            ok
        } catch (_: Throwable) { false }
    }

    /** 選択中テーマの背景シェーダ（radialなら中心やや上・そうでなければ縦グラデ）。 */
    private fun backgroundShader(w: Int, h: Int): Shader {
        val cols = theme.gradient.map { it.toArgb() }.toIntArray()
        return if (theme.radial) {
            RadialGradient(
                w * 0.5f, h * 0.42f, minOf(w, h) * 0.9f,
                cols, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        } else {
            LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                cols, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
        }
    }

    /** アプリ内 filesDir/gallery/ へPNGコピー。直近[Design.GalleryLimit]枚のみ残す。 */
    private fun saveInternalCopy(ctx: Context, bmp: Bitmap) {
        try {
            val dir = File(ctx.filesDir, GALLERY_DIR)
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, "g-${System.currentTimeMillis()}-${saveCount}.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // 古い順に間引く
            val files = dir.listFiles { x -> x.isFile }?.sortedBy { it.lastModified() } ?: return
            if (files.size > Design.GalleryLimit) {
                for (i in 0 until files.size - Design.GalleryLimit) files[i].delete()
            }
        } catch (_: Throwable) {}
    }

    /** アプリ内ギャラリーのPNG一覧（新しい順）。 */
    fun galleryFiles(ctx: Context): List<File> {
        val dir = File(ctx.filesDir, GALLERY_DIR)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".png") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun release() {
        base?.recycle(); base = null
        while (undo.isNotEmpty()) undo.pollLast()?.recycle()
        while (redo.isNotEmpty()) redo.pollLast()?.recycle()
        bgBitmap?.recycle(); bgBitmap = null; bgKey = null
        stampCache.values.forEach { it.recycle() }; stampCache.clear(); stampCacheKey = null
    }

    private fun withAlpha(rgb: Int, alpha: Int): Int = (alpha shl 24) or (rgb and 0xFFFFFF)

    companion object {
        private const val CANVAS_FILE = "canvas.png"
        private const val GALLERY_DIR = "gallery"
        private const val PREFS = "kirakira_prefs"
        private const val KEY_THEME = "bg_theme"
    }
}

/** スタンプ配置時の小さなバースト。 */
private fun ParticleSystem.celebrateAt(x: Float, y: Float, argb: Int) {
    for (i in 0 until 22) emit(x, y, if (i % 3 == 0) 0xFFFFD76A.toInt() else argb, 1, 2.6f, forceCross = i % 4 == 0)
}
