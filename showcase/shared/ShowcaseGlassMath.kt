package a3.showcase

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Token-locked squircle + highlight + blur energy. Does not live in the renderer. */
object ShowcaseGlassMath {
    data class Highlight(val widthPx: Int, val startAlpha: Float, val endAlpha: Float)

    fun nestedRadius(outerPx: Float, paddingPx: Float, minPx: Float): Float =
        maxOf(minPx, outerPx - paddingPx)

    fun points(width: Float, height: Float, radius: Float, n: Float, steps: Int = 24): FloatArray {
        val r = min(radius, min(width, height) / 2f)
        val out = ArrayList<Float>((steps + 1) * 8)
        fun corner(cx: Float, cy: Float, start: Double, end: Double) {
            for (i in 0..steps) {
                val t = start + (end - start) * (i.toDouble() / steps)
                val c = cos(t)
                val s = sin(t)
                val x = cx + r * sgn(c) * abs(c).pow(2.0 / n)
                val y = cy + r * sgn(s) * abs(s).pow(2.0 / n)
                out += x.toFloat()
                out += y.toFloat()
            }
        }
        corner(r, r, Math.PI, 1.5 * Math.PI)
        corner(width - r, r, 1.5 * Math.PI, 2.0 * Math.PI)
        corner(width - r, height - r, 0.0, 0.5 * Math.PI)
        corner(r, height - r, 0.5 * Math.PI, Math.PI)
        return out.toFloatArray()
    }

    fun contains(px: Float, py: Float, width: Float, height: Float, radius: Float, n: Float): Boolean {
        val r = min(radius, min(width, height) / 2f)
        val x = px.coerceIn(0f, width)
        val y = py.coerceIn(0f, height)
        val cx = when {
            x < r -> r
            x > width - r -> width - r
            else -> return y >= 0f && y <= height && x >= 0f && x <= width
        }
        val cy = when {
            y < r -> r
            y > height - r -> height - r
            else -> return x >= 0f && x <= width
        }
        val dx = abs((x - cx) / r).toDouble()
        val dy = abs((y - cy) / r).toDouble()
        return dx.pow(n.toDouble()) + dy.pow(n.toDouble()) <= 1.0
    }

    fun edgeEnergy(pixels: IntArray, width: Int, height: Int): Float {
        if (width < 2 || height < 2) return 0f
        var acc = 0.0
        var n = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val i = y * width + x
                val lum = luma(pixels[i])
                if (x + 1 < width) {
                    acc += abs(lum - luma(pixels[i + 1]))
                    n++
                }
                if (y + 1 < height) {
                    acc += abs(lum - luma(pixels[i + width]))
                    n++
                }
                x++
            }
            y++
        }
        if (n == 0) return 0f
        return (acc / n).toFloat()
    }

    fun innerHighlightBrighterThanFill(): Boolean {
        val t = ShowcaseTokens.snapshot
        return t.inner.startAlpha > 0.1f && t.outer.startAlpha > 0.1f
    }

    private fun luma(p: Int): Float {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun sgn(v: Double): Double = if (v < 0) -1.0 else 1.0
}