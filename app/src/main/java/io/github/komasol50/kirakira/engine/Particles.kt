package io.github.komasol50.kirakira.engine

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, val decay: Float,
    val size: Float, val colorArgb: Int,
    val cross: Boolean,
    var rot: Float, val spin: Float,
    val tw: Float, val ph: Float,
)

/**
 * キラキラの粒。上限 [Design.MaxParticles] を超えたら古い順に間引く（60fps最優先）。
 */
class ParticleSystem {
    val items = ArrayList<Particle>(Design.MaxParticles)
    private val rnd = Random(System.nanoTime())

    fun emit(x: Float, y: Float, colorArgb: Int, n: Int, spread: Float, forceCross: Boolean? = null) {
        repeat(n) {
            if (items.size >= Design.MaxParticles) items.removeAt(0)
            val a = rnd.nextFloat() * (2 * Math.PI).toFloat()
            val sp = spread * (0.2f + rnd.nextFloat() * 1.4f)
            items.add(
                Particle(
                    x = x + (rnd.nextFloat() - 0.5f) * 6f,
                    y = y + (rnd.nextFloat() - 0.5f) * 6f,
                    vx = cos(a) * sp,
                    vy = sin(a) * sp - 0.35f,
                    life = 1f,
                    decay = 0.012f + rnd.nextFloat() * 0.02f,
                    size = 2f + rnd.nextFloat() * 4f,
                    colorArgb = colorArgb,
                    cross = forceCross ?: (rnd.nextFloat() < 0.5f),
                    rot = rnd.nextFloat() * Math.PI.toFloat(),
                    spin = (rnd.nextFloat() - 0.5f) * 0.1f,
                    tw = 4f + rnd.nextFloat() * 7f,
                    ph = rnd.nextFloat() * 6f,
                )
            )
        }
    }

    /** 画面いっぱいの祝祭バースト（保存・リセット時）。 */
    fun celebrate(w: Float, h: Float, colors: IntArray, count: Int) {
        repeat(count) { i ->
            emit(rnd.nextFloat() * w, rnd.nextFloat() * h, colors[i % colors.size], 1, 2.2f, i % 3 == 0)
        }
    }

    fun update() {
        var i = items.size - 1
        while (i >= 0) {
            val p = items[i]
            p.x += p.vx; p.y += p.vy; p.vy -= 0.012f; p.vx *= 0.985f
            p.rot += p.spin; p.life -= p.decay
            if (p.life <= 0f) items.removeAt(i)
            i--
        }
    }

    fun clear() = items.clear()
}
