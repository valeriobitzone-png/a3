// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.showcase

import kotlin.math.cos
import kotlin.math.sin

/** Clock-free formulas matching renderer chrome (tokens + documented constants). */
object ShowcaseChromeMath {
    data class Stop(val r: Float, val g: Float, val b: Float)
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val radius: Float)
    data class Spark(val x: Float, val y: Float, val vx: Float, val vy: Float, val life: Float)
    data class SpringState(val x: Float, val v: Float)

    fun palette(): Triple<Stop, Stop, Stop> {
        val s = ShowcaseTokens.snapshot
        return Triple(rgb(s.paper), rgb(s.amber), rgb(s.ink))
    }

    fun shade(u: Float, v: Float, timeSec: Float, pal: Triple<Stop, Stop, Stop> = palette()): Int {
        val t = timeSec * ShowcaseTokens.GRADIENT_SPEED
        val n = 0.5f + 0.5f * sin(ShowcaseTokens.TAU * (u * 0.70f + v * 0.40f + t))
        val m = 0.5f + 0.5f * sin(ShowcaseTokens.TAU * (u * 0.30f - v * 0.80f + t * 0.73f) + ShowcaseTokens.GRADIENT_AMPLITUDE)
        val blob = mix(pal.second, pal.third, n)
        val col = mix(pal.first, blob, m * ShowcaseTokens.mixAmt())
        return pack(col)
    }

    fun collapsed(): Rect = Rect(20f, 8f, 108f, 32f, 16f)

    fun expanded(): Rect = Rect(8f, 4f, 220f, 64f, 18f)

    fun morph(t: Float): Rect {
        val a = collapsed()
        val b = expanded()
        val u = t.coerceIn(0f, 1f)
        return Rect(
            x = a.x + (b.x - a.x) * u,
            y = a.y + (b.y - a.y) * u,
            w = a.w + (b.w - a.w) * u,
            h = a.h + (b.h - a.h) * u,
            radius = a.radius + (b.radius - a.radius) * u
        )
    }

    fun blurPx(open: Float): Float = ShowcaseTokens.snapshot.acrylicBlurPx * open.coerceIn(0f, 1.2f)

    fun overlayAlpha(open: Float): Float = ShowcaseTokens.snapshot.fillOpacity * open.coerceIn(0f, 1f)

    fun noiseAt(x: Int, y: Int): Float {
        var n = x * 374761 + y * 668265 + 1013904223
        n = n xor (n shl 13)
        val u = ((n ushr 8) and 255) / 255f
        return (u - 0.5f) * 2f * ShowcaseTokens.NOISE_STRENGTH
    }

    fun emit(count: Int = ShowcaseTokens.PARTICLE_COUNT, seed: Int = 7, cx: Float = 0.5f, cy: Float = 0.5f): List<Spark> {
        var s = seed
        val out = ArrayList<Spark>(count)
        var i = 0
        while (i < count) {
            s = s * 1664525 + 1013904223
            val ang = ((s ushr 16) and 0xFFFF) / 65536f * ShowcaseTokens.TAU
            val spd = 0.08f + ((s ushr 8) and 255) / 255f * 0.12f
            out += Spark(cx, cy, cos(ang) * spd, sin(ang) * spd, 1f)
            i++
        }
        return out
    }

    fun stepSparks(sparks: List<Spark>, t: Float): List<Spark> {
        val u = t.coerceIn(0f, 1f)
        return sparks.map {
            Spark(it.x + it.vx * u, it.y + it.vy * u, it.vx, it.vy, (1f - u).coerceAtLeast(0f))
        }
    }

    fun springStep(
        state: SpringState,
        target: Float,
        mass: Float,
        stiffness: Float,
        damping: Float,
        dt: Float
    ): SpringState {
        val a = (-stiffness * (state.x - target) - damping * state.v) / mass
        val v = state.v + a * dt
        val x = state.x + v * dt
        return SpringState(x, v)
    }

    fun maskEdge(x: Float, width: Float, fade: Float = ShowcaseTokens.MASK_FADE_PX): Float {
        val w = width.coerceAtLeast(1f)
        val f = fade.coerceAtLeast(1f)
        val left = (x / f).coerceIn(0f, 1f)
        val right = ((w - x) / f).coerceIn(0f, 1f)
        return kotlin.math.min(left, right)
    }

    private fun rgb(argb: Int) = Stop(
        ((argb shr 16) and 0xFF) / 255f,
        ((argb shr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f
    )

    private fun mix(a: Stop, b: Stop, t: Float): Stop {
        val u = t.coerceIn(0f, 1f)
        return Stop(a.r + (b.r - a.r) * u, a.g + (b.g - a.g) * u, a.b + (b.b - a.b) * u)
    }

    private fun pack(c: Stop): Int {
        val r = (c.r * 255f).toInt().coerceIn(0, 255)
        val g = (c.g * 255f).toInt().coerceIn(0, 255)
        val b = (c.b * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
