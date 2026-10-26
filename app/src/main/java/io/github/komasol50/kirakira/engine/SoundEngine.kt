package io.github.komasol50.kirakira.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 効果音を手続き的に合成して鳴らす（音源ファイル不要）。
 * 音の性格は sample/prototype.html の WebAudio 版に合わせた短く控えめなもの。
 * 音まわりの例外は握りつぶす（お絵かき本体を絶対に落とさない）。
 */
class SoundEngine {
    @Volatile var enabled = true
    private val sr = 22050
    private val pool = Executors.newFixedThreadPool(3)
    private var lastTinkle = 0L
    private var lastErase = 0L
    private val penta = floatArrayOf(1046f, 1174f, 1318f, 1568f, 1760f, 2093f)

    private fun envToneSamples(freq: Float, durSec: Float, vol: Float, triangle: Boolean): ShortArray {
        val n = (sr * durSec).toInt().coerceAtLeast(1)
        val out = ShortArray(n)
        val twoPi = (2 * PI).toFloat()
        for (i in 0 until n) {
            val t = i.toFloat() / sr
            val phase = twoPi * freq * t
            val raw = if (triangle) (2f / PI.toFloat()) * kotlin.math.asin(sin(phase)) else sin(phase)
            // 立ち上がり12ms→指数減衰
            val atk = (t / 0.012f).coerceAtMost(1f)
            val env = atk * exp(-3.5f * t / durSec)
            out[i] = (raw * env * vol * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun playSamples(buf: ShortArray) {
        if (!enabled) return
        pool.execute {
            var at: AudioTrack? = null
            try {
                at = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(sr)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    buf.size * 2,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE,
                )
                at.write(buf, 0, buf.size)
                at.play()
                Thread.sleep((buf.size * 1000L / sr) + 60L)
            } catch (_: Throwable) {
                // 無音で続行
            } finally {
                try { at?.release() } catch (_: Throwable) {}
            }
        }
    }

    private fun tone(freq: Float, durSec: Float, vol: Float, triangle: Boolean = true) =
        playSamples(envToneSamples(freq, durSec, vol, triangle))

    fun blip(freq: Float) = tone(freq, 0.12f, 0.05f)

    /** 描画中の小さな鈴。呼び出しは高頻度なので内部でスロットル＆間引き。 */
    fun tinkle() {
        val now = System.currentTimeMillis()
        if (now - lastTinkle < 110L || Math.random() < 0.45) return
        lastTinkle = now
        tone(penta[(Math.random() * penta.size).toInt()], 0.18f, 0.028f, triangle = false)
    }

    fun pop() {
        tone(520f, 0.09f, 0.07f)
        tone(1040f, 0.12f, 0.04f, triangle = false)
    }

    fun fanfare() {
        val notes = floatArrayOf(1046f, 1318f, 1568f, 2093f)
        pool.execute {
            for (f in notes) {
                playSamples(envToneSamples(f, 0.3f, 0.06f, true))
                try { Thread.sleep(90) } catch (_: Throwable) {}
            }
        }
    }

    /** 消しゴムの柔らかい低めの「シュッ」（高頻度呼び出しなので内部でスロットル）。 */
    fun softErase() {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastErase < 90L) return
        lastErase = now
        val n = (sr * 0.12f).toInt()
        val out = ShortArray(n)
        var prev = 0f
        for (i in 0 until n) {
            val env = (1f - i.toFloat() / n)
            // ホワイトノイズを軽くローパス（前値と平均）して柔らかい低めの息づかいに
            val raw = (Math.random() * 2 - 1).toFloat()
            prev = prev * 0.6f + raw * 0.4f
            out[i] = (prev * env * 0.05f * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playSamples(out)
    }

    /** 休憩開始のやさしいチャイム（v2.6・S）。ゆっくり上がる3音のトライアングル波。 */
    fun chime() {
        if (!enabled) return
        val notes = floatArrayOf(1318f, 1760f, 2093f)
        pool.execute {
            for (f in notes) {
                playSamples(envToneSamples(f, 0.45f, 0.05f, true))
                try { Thread.sleep(130) } catch (_: Throwable) {}
            }
        }
    }

    /** 誤答の控えめな音（v2.6・S）。低めの短い2音（きつくならないよう小音量）。 */
    fun wrongTone() {
        tone(311f, 0.14f, 0.045f, triangle = false)
        tone(233f, 0.20f, 0.045f, triangle = false)
    }

    fun whoosh() {
        if (!enabled) return
        val n = (sr * 0.6f).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val env = 1f - i.toFloat() / n
            out[i] = ((Math.random() * 2 - 1).toFloat() * env * 0.12f * Short.MAX_VALUE).toInt().toShort()
        }
        playSamples(out)
    }

    fun release() {
        try { pool.shutdownNow() } catch (_: Throwable) {}
    }
}
