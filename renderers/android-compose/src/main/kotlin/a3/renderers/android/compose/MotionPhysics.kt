package a3.renderers.android.compose

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * L3 motion: ODE spring, cubic-bezier, inertia, ripple, rubber-band, shared bounds.
 * Clock-free. Compose consumes these numbers; tests integrate without a frame clock.
 *
 * Equation: d²x/dt² = (-stiffness · (x - target) - damping · v) / mass
 */
internal object MotionPhysics {
    data class State(val x: Float, val v: Float)
    data class Rect(val l: Float, val t: Float, val w: Float, val h: Float) {
        val r: Float get() = l + w
        val b: Float get() = t + h
        fun contains(px: Float, py: Float) = px >= l && px < r && py >= t && py < b
    }
    data class Box(val x: Float, val y: Float, val w: Float, val wH: Float)

    const val DT = 1f / 240f

    fun accel(x: Float, v: Float, target: Float, mass: Float, stiffness: Float, damping: Float): Float {
        val disp = x - target
        return (-stiffness * disp - damping * v) / mass
    }

    fun step(
        state: State,
        target: Float,
        mass: Float,
        stiffness: Float,
        damping: Float,
        dt: Float = DT
    ): State {
        val a = accel(state.x, state.v, target, mass, stiffness, damping)
        val v = state.v + a * dt
        val x = state.x + v * dt
        return State(x, v)
    }

    fun integrate(
        from: Float,
        target: Float,
        mass: Float,
        stiffness: Float,
        damping: Float,
        durationMs: Int,
        reduced: Boolean = false,
        initialV: Float = 0f
    ): List<State> {
        if (reduced) return listOf(State(target, 0f))
        val out = ArrayList<State>()
        var s = State(from, initialV)
        val steps = max(1, (durationMs / 1000f / DT).toInt())
        var i = 0
        while (i < steps) {
            s = step(s, target, mass, stiffness, damping)
            out += s
            i++
        }
        return out
    }

    fun inheritVelocity(state: State, gestureV: Float): State = State(state.x, gestureV)

    fun overshoot(samples: List<State>, from: Float, target: Float): Boolean {
        if (target >= from) return samples.any { it.x > target + 0.002f }
        return samples.any { it.x < target - 0.002f }
    }

    fun linear(from: Float, target: Float, t: Float): Float = from + (target - from) * t

    fun notLinear(samples: List<State>, from: Float, target: Float, durationMs: Int): Boolean {
        val mid = samples[samples.size / 2].x
        val linearMid = linear(from, target, 0.5f)
        return abs(mid - linearMid) > 0.01f
    }

    fun bezier(t: Float, p: FloatArray): Float {
        val u = t.coerceIn(0f, 1f)
        var s = u
        var i = 0
        while (i < 8) {
            val x = cubic(s, 0f, p[0], p[2], 1f)
            val dx = dcubic(s, 0f, p[0], p[2], 1f)
            if (abs(dx) < 1e-6f) break
            s = (s - (x - u) / dx).coerceIn(0f, 1f)
            i++
        }
        return cubic(s, 0f, p[1], p[3], 1f)
    }

    private fun cubic(t: Float, a: Float, b: Float, c: Float, d: Float): Float {
        val u = 1f - t
        return u * u * u * a + 3f * u * u * t * b + 3f * u * t * t * c + t * t * t * d
    }

    private fun dcubic(t: Float, a: Float, b: Float, c: Float, d: Float): Float {
        val u = 1f - t
        return 3f * u * u * (b - a) + 6f * u * t * (c - b) + 3f * t * t * (d - c)
    }

    fun integrateSettled(
        from: Float,
        target: Float,
        mass: Float,
        stiffness: Float,
        damping: Float,
        reduced: Boolean = false,
        initialV: Float = 0f,
        maxMs: Int = 2000
    ): List<State> {
        if (reduced) return listOf(State(target, 0f))
        val out = ArrayList<State>()
        var s = State(from, initialV)
        var t = 0f
        val maxT = maxMs / 1000f
        while (t < maxT) {
            s = step(s, target, mass, stiffness, damping)
            out += s
            t += DT
            if (abs(s.x - target) < 0.0005f && abs(s.v) < 0.02f) break
        }
        return out
    }

    fun afterMs(state: State, target: Float, mass: Float, stiffness: Float, damping: Float, ms: Float): State {
        var s = state
        var t = 0f
        while (t < ms / 1000f) {
            s = step(s, target, mass, stiffness, damping)
            t += DT
        }
        return s
    }

    fun sharedRect(from: Rect, to: Rect, t: Float, bezier: FloatArray): Rect {
        val u = bezier(t, bezier)
        return Rect(
            l = from.l + (to.l - from.l) * u,
            t = from.t + (to.t - from.t) * u,
            w = from.w + (to.w - from.w) * u,
            h = from.h + (to.h - from.h) * u
        )
    }

    fun nodeIdentity(id: String, text: String): Int = 31 * id.hashCode() + text.hashCode()

    fun fluidWidth(from: Float, to: Float, t: Float, bezier: FloatArray): Float =
        from + (to - from) * bezier(t, bezier)

    fun fluidBoxes(fromW: Float, toW: Float, t: Float, count: Int, bezier: FloatArray): List<Box> {
        val from = column(fromW, count)
        val to = grid(toW, count)
        val u = bezier(t, bezier)
        return from.zip(to) { a, b ->
            Box(
                x = a.x + (b.x - a.x) * u,
                y = a.y + (b.y - a.y) * u,
                w = a.w + (b.w - a.w) * u,
                wH = a.wH + (b.wH - a.wH) * u
            )
        }
    }

    private fun column(width: Float, count: Int): List<Box> {
        val h = 20f
        return (0 until count).map { i -> Box(0f, i * h, width, h - 2f) }
    }

    private fun grid(width: Float, count: Int): List<Box> {
        val colW = width / 2f
        val h = 20f
        return (0 until count).map { i ->
            Box((i % 2) * colW, (i / 2) * h, colW - 2f, h - 2f)
        }
    }

    fun rippleRadius(tMs: Float, maxR: Float, tauMs: Float): Float {
        val t = max(0f, tMs)
        return maxR * (1f - exp(-t / tauMs))
    }

    fun rippleAlpha(tMs: Float, tauMs: Float): Float = exp(-tMs / tauMs).coerceIn(0f, 1f)

    fun rubberStretch(overscroll: Float, range: Float = 48f): Float {
        val o = max(0f, overscroll)
        return o / (1f + o / range)
    }

    fun rubberLinear(overscroll: Float): Float = overscroll
}
