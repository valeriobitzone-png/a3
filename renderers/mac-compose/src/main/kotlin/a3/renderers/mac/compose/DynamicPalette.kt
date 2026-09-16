// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import com.materialkolor.palettes.CorePalette
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Tonal palette from a wallpaper bitmap, locked to a3ui-graphics color tokens.
 * Extraction is an adapter: ink/paper stay the token pair; roles are harmonized
 * so WCAG AA ([ColorSnapshot.minRatio]) never drops. Shaders consume the Tonal.
 */
internal object DynamicPalette {
    data class Rgb(val r: Int, val g: Int, val b: Int) {
        fun pack(): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    data class Tonal(
        val primary: Rgb,
        val secondary: Rgb,
        val tertiary: Rgb,
        val neutral: Rgb,
        val ink: Rgb,
        val paper: Rgb,
        val source: String
    ) {
        fun roles(): List<Rgb> = listOf(primary, secondary, tertiary, neutral, ink, paper)
    }

    const val CONFUSION_MIN = 0.15f
    const val ADAPT_DELTA_MIN = 0.5f
    const val SOURCE_EXTRACTED = "extracted"
    const val SOURCE_FALLBACK = "token-fallback"

    fun unpack(argb: Int): Rgb = Rgb((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    fun tokenInk(): Rgb = unpack(GraphicsTokens.colors.ink)
    fun tokenPaper(): Rgb = unpack(GraphicsTokens.colors.paper)
    fun tokenAmber(): Rgb = unpack(GraphicsTokens.colors.amber)

    fun fallback(): Tonal {
        val ink = tokenInk()
        val paper = tokenPaper()
        val amber = tokenAmber()
        return Tonal(
            primary = ink,
            secondary = amber,
            tertiary = ink,
            neutral = paper,
            ink = ink,
            paper = paper,
            source = SOURCE_FALLBACK
        )
    }

    fun extract(pixels: IntArray, width: Int, height: Int): Tonal {
        if (pixels.isEmpty() || width <= 0 || height <= 0) return fallback()
        val quantized = QuantizerCelebi.quantize(pixels, 128)
        val ranked = Score.score(quantized)
        val seed = ranked.firstOrNull() ?: return fallback()
        val core = CorePalette.contentOf(seed)
        val paper = tokenPaper()
        val ink = tokenInk()
        val minRatio = GraphicsTokens.colors.minRatio
        val primary = ensureContrast(unpack(core.a1.tone(40)), paper, minRatio)
        val secondary = ensureContrast(unpack(core.a2.tone(40)), paper, minRatio)
        val tertiary = ensureContrast(unpack(core.a3.tone(40)), paper, minRatio)
        val neutral = unpack(core.n1.tone(90))
        val tonal = Tonal(primary, secondary, tertiary, neutral, ink, paper, SOURCE_EXTRACTED)
        return if (valid(tonal)) tonal else fallback()
    }

    fun valid(tonal: Tonal): Boolean {
        if (tonal.roles().any { it.r !in 0..255 || it.g !in 0..255 || it.b !in 0..255 }) return false
        val min = GraphicsTokens.colors.minRatio
        return contrast(tonal.ink, tonal.paper) + 0.001f >= min &&
            contrast(tonal.primary, tonal.paper) + 0.001f >= min
    }

    fun relativeLuminance(c: Rgb): Float {
        fun lin(channel: Int): Float {
            val s = channel / 255f
            return if (s <= 0.04045f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * lin(c.r) + 0.7152f * lin(c.g) + 0.0722f * lin(c.b)
    }

    fun contrast(a: Rgb, b: Rgb): Float {
        val l1 = relativeLuminance(a)
        val l2 = relativeLuminance(b)
        val hi = max(l1, l2)
        val lo = min(l1, l2)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    fun detailEnergy(pixels: IntArray, width: Int, height: Int): Float {
        if (width < 2 || height < 2) return 0f
        var acc = 0.0
        var n = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val i = y * width + x
                val lum = luma(unpack(pixels[i]))
                if (x + 1 < width) {
                    acc += abs(lum - luma(unpack(pixels[i + 1])))
                    n++
                }
                if (y + 1 < height) {
                    acc += abs(lum - luma(unpack(pixels[i + width])))
                    n++
                }
                x++
            }
            y++
        }
        if (n == 0) return 0f
        return (acc / n).toFloat()
    }

    fun confused(pixels: IntArray, width: Int, height: Int): Boolean =
        detailEnergy(pixels, width, height) >= CONFUSION_MIN

    fun adaptText(proposed: Rgb, backdrop: IntArray, width: Int, height: Int): Rgb {
        if (backdrop.isEmpty()) return proposed
        val mean = meanRgb(backdrop)
        val min = if (confused(backdrop, width, height)) {
            GraphicsTokens.colors.highContrastMinRatio
        } else {
            GraphicsTokens.colors.minRatio
        }
        return ensureContrast(proposed, mean, min)
    }

    fun fixtureWallpaper(width: Int, height: Int): IntArray {
        val amber = tokenAmber().pack()
        val ink = tokenInk().pack()
        val paper = tokenPaper().pack()
        val teal = (0xFF shl 24) or (0x20 shl 16) or (0x88 shl 8) or 0x90
        val out = IntArray(width * height)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val cell = ((x / 10) + (y / 10)) % 4
                out[y * width + x] = when (cell) {
                    0 -> amber
                    1 -> teal
                    2 -> ink
                    else -> paper
                }
                x++
            }
            y++
        }
        return out
    }

    fun checker(width: Int, height: Int, cell: Int = 2): IntArray {
        val a = (0xFF shl 24) or (0xE8 shl 16) or (0xE8 shl 8) or 0xE8
        val b = (0xFF shl 24) or (0x28 shl 16) or (0x28 shl 8) or 0x28
        val out = IntArray(width * height)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                out[y * width + x] = if (((x / cell) + (y / cell)) and 1 == 0) a else b
                x++
            }
            y++
        }
        return out
    }

    private fun meanRgb(pixels: IntArray): Rgb {
        var r = 0L
        var g = 0L
        var b = 0L
        var i = 0
        while (i < pixels.size) {
            val c = unpack(pixels[i])
            r += c.r
            g += c.g
            b += c.b
            i++
        }
        val n = pixels.size.coerceAtLeast(1)
        return Rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun ensureContrast(fg: Rgb, bg: Rgb, minRatio: Float): Rgb {
        if (contrast(fg, bg) >= minRatio) return fg
        val ink = tokenInk()
        val paper = tokenPaper()
        val towardInk = contrast(ink, bg) >= contrast(paper, bg)
        val target = if (towardInk) ink else paper
        var t = 0f
        var out = fg
        while (t <= 1.001f) {
            out = mix(fg, target, t)
            if (contrast(out, bg) >= minRatio) return out
            t += 0.05f
        }
        return target
    }

    fun mix(a: Rgb, b: Rgb, t: Float): Rgb {
        val u = t.coerceIn(0f, 1f)
        return Rgb(
            (a.r + (b.r - a.r) * u).toInt().coerceIn(0, 255),
            (a.g + (b.g - a.g) * u).toInt().coerceIn(0, 255),
            (a.b + (b.b - a.b) * u).toInt().coerceIn(0, 255)
        )
    }

    private fun luma(c: Rgb): Float = (0.2126f * c.r + 0.7152f * c.g + 0.0722f * c.b) / 255f
}
