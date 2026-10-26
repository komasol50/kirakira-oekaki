package io.github.komasol50.kirakira

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.komasol50.kirakira.engine.DrawEngine
import io.github.komasol50.kirakira.engine.ParticleSystem
import io.github.komasol50.kirakira.engine.PlayTimer
import io.github.komasol50.kirakira.engine.SoundEngine
import io.github.komasol50.kirakira.ui.CanvasScreen
import io.github.komasol50.kirakira.ui.theme.KirakiraTheme

class MainActivity : ComponentActivity() {

    private val particles = ParticleSystem()
    private val sound = SoundEngine()
    private val engine by lazy { DrawEngine(particles, sound) }
    private val playTimer = PlayTimer()   // あそびタイマー（v2.6・S）
    private var lastBack = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 画面を消さない（子どもが眺めている間に暗くならない）
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        // プロセス復帰時に前回の絵を復元する準備
        engine.markRestore(this)

        // 保存済みの背景テーマを読み込む（SharedPreferences）
        engine.loadTheme(this)

        // あそびタイマーの累積を復元（再起動でも維持・休憩中なら休憩から始まる）
        playTimer.load(this)

        // 戻るの誤爆防止: 1回目は無視して案内、2秒以内にもう1回で終了
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - lastBack < 2000) {
                    finish()
                } else {
                    lastBack = now
                    Toast.makeText(this@MainActivity, "もういちどで おしまい", Toast.LENGTH_SHORT).show()
                }
            }
        })

        setContent {
            KirakiraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CanvasScreen(engine = engine, particles = particles, timer = playTimer)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        playTimer.onResume()   // フォアグラウンド計測を開始
    }

    override fun onPause() {
        super.onPause()
        playTimer.onPause(this)   // 計測を止めて累積を保存（バックグラウンドは数えない）
        engine.saveToDisk(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.release()
        sound.release()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        // v2.4: ステータスバー（時計・電池）は常時表示。ナビゲーションバーだけ隠す（誤タップ防止）。
        controller.show(WindowInsetsCompat.Type.statusBars())
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.isAppearanceLightStatusBars = true // 明るいバー地に濃いアイコン
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
