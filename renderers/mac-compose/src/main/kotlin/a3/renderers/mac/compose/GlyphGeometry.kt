package a3.renderers.mac.compose

import kotlin.math.cos
import kotlin.math.sin

/**
 * Geometric glyphs (polyline), not illustrated assets.
 * PENDING → spinner, DONE → check, HELD → pause.
 * Morph = linear interpolation of resampled points; Compose springs the t.
 */
internal object GlyphGeometry {
    data class Pt(val x: Float, val y: Float)

    enum class Kind { SPINNER, CHECK, PAUSE, MIC, LENS }

    const val N = 16

    fun kind(action: String, status: String): Kind? = when {
        action == "pending" -> Kind.SPINNER
        action == "done" -> Kind.CHECK
        status == "held" -> Kind.PAUSE
        else -> null
    }

    fun tag(kind: Kind): String = when (kind) {
        Kind.SPINNER -> "spinner"
        Kind.CHECK -> "check"
        Kind.PAUSE -> "held"
        Kind.MIC -> "mic"
        Kind.LENS -> "lens"
    }

    fun points(kind: Kind, t: Float, reduced: Boolean): List<Pt> {
        val u = if (reduced) 1f else t.coerceIn(0f, 1f)
        return when (kind) {
            Kind.SPINNER -> spinner(if (reduced) 0f else u)
            Kind.CHECK -> check(u)
            Kind.PAUSE -> pause()
            Kind.MIC -> mic(u)
            Kind.LENS -> lens(if (reduced) 0f else u)
        }
    }

    fun morph(from: Kind, to: Kind, t: Float): List<Pt> {
        val a = points(from, 1f, reduced = true)
        val b = points(to, 1f, reduced = true)
        val u = t.coerceIn(0f, 1f)
        return a.zip(b) { p, q -> Pt(p.x + (q.x - p.x) * u, p.y + (q.y - p.y) * u) }
    }

    private fun spinner(t: Float): List<Pt> {
        val rot = t * Math.PI.toFloat() * 2f
        val out = ArrayList<Pt>(N)
        var i = 0
        while (i < N) {
            val a = rot + (i / N.toFloat()) * 1.5f * Math.PI.toFloat()
            out += Pt(0.5f + 0.32f * cos(a), 0.5f + 0.32f * sin(a))
            i++
        }
        return out
    }

    private fun check(t: Float): List<Pt> {
        val stem = listOf(Pt(0.18f, 0.52f), Pt(0.42f, 0.78f), Pt(0.84f, 0.22f))
        return resample(stem, N, t)
    }

    private fun pause(): List<Pt> {
        val left = rect(0.28f, 0.22f, 0.16f, 0.56f)
        val right = rect(0.56f, 0.22f, 0.16f, 0.56f)
        return resample(left + right, N, 1f)
    }

    private fun mic(t: Float): List<Pt> {
        val fill = 0.22f + 0.56f * t
        val cap = listOf(
            Pt(0.38f, 0.22f),
            Pt(0.62f, 0.22f),
            Pt(0.62f, fill),
            Pt(0.38f, fill)
        )
        val stem = listOf(Pt(0.50f, fill), Pt(0.50f, 0.82f), Pt(0.34f, 0.82f), Pt(0.66f, 0.82f))
        return resample(cap + stem, N, 1f)
    }

    private fun lens(t: Float): List<Pt> {
        val rot = t * Math.PI.toFloat() * 0.5f
        val ring = ArrayList<Pt>()
        var i = 0
        while (i < 12) {
            val a = i / 12f * Math.PI.toFloat() * 2f
            ring += Pt(0.42f + 0.22f * cos(a), 0.42f + 0.22f * sin(a))
            i++
        }
        val hx = 0.58f + 0.28f * cos(rot)
        val hy = 0.58f + 0.28f * sin(rot)
        ring += Pt(0.58f, 0.58f)
        ring += Pt(hx, hy)
        ring += Pt(hx + 0.04f, hy + 0.04f)
        ring += Pt(0.62f, 0.62f)
        return resample(ring, N, 1f)
    }

    private fun rect(x: Float, y: Float, w: Float, h: Float): List<Pt> = listOf(
        Pt(x, y),
        Pt(x + w, y),
        Pt(x + w, y + h),
        Pt(x, y + h)
    )

    private fun resample(src: List<Pt>, count: Int, t: Float): List<Pt> {
        if (src.isEmpty()) return List(count) { Pt(0.5f, 0.5f) }
        val out = ArrayList<Pt>(count)
        val last = ((src.size - 1) * t).coerceAtLeast(0.001f)
        var i = 0
        while (i < count) {
            val f = i / (count - 1f) * last
            val i0 = f.toInt().coerceIn(0, src.lastIndex)
            val i1 = (i0 + 1).coerceAtMost(src.lastIndex)
            val u = f - i0
            val a = src[i0]
            val b = src[i1]
            out += Pt(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u)
            i++
        }
        return out
    }
}
