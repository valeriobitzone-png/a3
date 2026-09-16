// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.showcase

/**
 * Same 4-cell fixture as renderer DynamicPalette.fixtureWallpaper (amber / teal / ink / paper).
 * Cell size is larger on device for fill-rate; the modulo formula is identical.
 */
object ShowcaseWallpaper {
    const val CELL = 24
    const val TEAL = (0xFF shl 24) or (0x20 shl 16) or (0x88 shl 8) or 0x90
    const val GUTTER_DP = 22

    fun cellColor(cx: Int, cy: Int): Int {
        val s = ShowcaseTokens.snapshot
        return when (((cx + cy) % 4 + 4) % 4) {
            0 -> s.amber
            1 -> TEAL
            2 -> s.ink
            else -> s.paper
        }
    }

    fun colorAt(x: Int, y: Int, cell: Int = CELL): Int {
        val c = cell.coerceAtLeast(1)
        return cellColor(floorDiv(x, c), floorDiv(y, c))
    }

    fun blurredAt(x: Int, y: Int, cell: Int = CELL): Int {
        val c = cell.coerceAtLeast(1)
        val cx = floorDiv(x, c)
        val cy = floorDiv(y, c)
        var r = 0
        var g = 0
        var b = 0
        var n = 0
        var dy = -1
        while (dy <= 1) {
            var dx = -1
            while (dx <= 1) {
                val packed = cellColor(cx + dx, cy + dy)
                r += (packed shr 16) and 0xFF
                g += (packed shr 8) and 0xFF
                b += packed and 0xFF
                n++
                dx++
            }
            dy++
        }
        return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    fun fill(width: Int, height: Int, cell: Int = CELL, blurred: Boolean = false): IntArray {
        val out = IntArray(width * height)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                out[y * width + x] = if (blurred) blurredAt(x, y, cell) else colorAt(x, y, cell)
                x++
            }
            y++
        }
        return out
    }

    fun chroma(pixels: IntArray): Float {
        if (pixels.isEmpty()) return 0f
        var acc = 0.0
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            acc += (max - min)
        }
        return (acc / pixels.size / 255.0).toFloat()
    }

    private fun floorDiv(value: Int, cell: Int): Int {
        return if (value >= 0) value / cell else (value - cell + 1) / cell
    }
}