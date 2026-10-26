package io.github.komasol50.kirakira.engine

import android.content.Context
import kotlin.math.ceil

/**
 * あそびタイマー（v2.6・S）。フォアグラウンドの経過時間だけを累積する。
 *
 * - 計測はフォアグラウンド（onResume〜onPause）のみ。バックグラウンド時間は数えない。
 * - 累積値は SharedPreferences に保存し、アプリ再起動でもリセットされない（ズル防止）。
 * - 休憩の解除に成功したときだけ [reset] で累積を0へ戻す。
 * - System.currentTimeMillis ベース（新規ライブラリ・権限を足さない）。
 */
class PlayTimer {
    // 保存済みの累積フォアグラウンド時間(ms)。
    private var accumulatedMs = 0L
    // 現在のフォアグラウンド開始時刻。0なら停止中（バックグラウンド）。
    private var resumeAt = 0L

    /** SharedPreferences から前回までの累積を読み込む（プロセス復帰時も維持）。 */
    fun load(ctx: Context) {
        accumulatedMs = try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_ELAPSED, 0L)
        } catch (_: Throwable) { 0L }
    }

    /** フォアグラウンドに入った（計測開始）。 */
    fun onResume() {
        resumeAt = System.currentTimeMillis()
    }

    /** フォアグラウンドを離れた（計測停止＋累積を保存）。 */
    fun onPause(ctx: Context) {
        if (resumeAt > 0L) {
            accumulatedMs += (System.currentTimeMillis() - resumeAt).coerceAtLeast(0L)
            resumeAt = 0L
        }
        save(ctx)
    }

    /** 現時点までの累積(ms)。フォアグラウンド中なら経過中の分も足して返す。 */
    fun elapsedMs(): Long {
        val live = if (resumeAt > 0L) (System.currentTimeMillis() - resumeAt).coerceAtLeast(0L) else 0L
        return accumulatedMs + live
    }

    /** 次の休憩までの残り分（1〜10・1分単位で切り上げ・ピル表示用）。0にはしない（0は休憩）。 */
    fun remainingMinutes(): Int {
        val rem = THRESHOLD_MS - elapsedMs()
        if (rem <= 0L) return 0
        return ceil(rem / 60_000f).toInt().coerceIn(1, (THRESHOLD_MS / 60_000L).toInt())
    }

    /** 累積が休憩しきい値（10分）に達したか。 */
    fun isBreakDue(): Boolean = elapsedMs() >= THRESHOLD_MS

    /** 休憩を解除して累積を0へ（フォアグラウンド中なら計測を今から再スタート）。 */
    fun reset(ctx: Context) {
        accumulatedMs = 0L
        resumeAt = if (resumeAt > 0L) System.currentTimeMillis() else 0L
        save(ctx)
    }

    private fun save(ctx: Context) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_ELAPSED, accumulatedMs).apply()
        } catch (_: Throwable) {}
    }

    companion object {
        // 休憩までのフォアグラウンド累積しきい値（10分）。
        const val THRESHOLD_MS = 10 * 60 * 1000L
        private const val PREFS = "kirakira_prefs"        // DrawEngine と同じ prefs ファイル
        private const val KEY_ELAPSED = "play_elapsed_ms"
    }
}
