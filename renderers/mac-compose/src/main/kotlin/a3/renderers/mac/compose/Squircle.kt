// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import java.awt.geom.Path2D
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal object SquircleGeometry {
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

    fun composePath(width: Float, height: Float, radius: Float, n: Float): Path {
        val pts = points(width, height, radius, n)
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        path.close()
        return path
    }

    fun awtPath(width: Float, height: Float, radius: Float, n: Float): Path2D.Float {
        val pts = points(width, height, radius, n)
        val path = Path2D.Float()
        path.moveTo(pts[0].toDouble(), pts[1].toDouble())
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i].toDouble(), pts[i + 1].toDouble())
            i += 2
        }
        path.closePath()
        return path
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

    private fun sgn(v: Double): Double = if (v < 0) -1.0 else 1.0
}

internal class SquircleShape(
    private val radius: Dp,
    private val n: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        return Outline.Generic(SquircleGeometry.composePath(size.width, size.height, r, n))
    }
}
