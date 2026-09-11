package a3.renderers.android.compose

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
        val seed = dominant(pixels) ?: return fallback()
        if (chroma(seed) < 12f) return fallback()
        val paper = tokenPaper()
        val ink = tokenInk()
        val amber = tokenAmber()
        val minRatio = GraphicsTokens.colors.minRatio
        val towardAmber = hueDist(seed, amber) < 48f
        val primarySeed = if (towardAmber) mix(seed, amber, 0.45f) else mix(seed, ink, 0.22f)
        val primary = ensureContrast(primarySeed, paper, minRatio)
        val secondary = ensureContrast(mix(rotate(seed, 28f), amber, 0.15f), paper, minRatio)
        val tertiary = ensureContrast(mix(rotate(seed, 118f), ink, 0.18f), paper, minRatio)
        val neutral = mix(paper, ink, 0.08f)
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

    private fun dominant(pixels: IntArray): Rgb? {
        val buckets = IntArray(24)
        val accR = IntArray(24)
        val accG = IntArray(24)
        val accB = IntArray(24)
        var i = 0
        while (i < pixels.size) {
            val c = unpack(pixels[i])
            if (chroma(c) >= 12f) {
                val h = hue(c)
                val b = ((h / 360f) * 24f).toInt().coerceIn(0, 23)
                buckets[b]++
                accR[b] += c.r
                accG[b] += c.g
                accB[b] += c.b
            }
            i++
        }
        var best = -1
        var idx = -1
        var b = 0
        while (b < 24) {
            if (buckets[b] > best) {
                best = buckets[b]
                idx = b
            }
            b++
        }
        if (idx < 0 || best < 8) return null
        val n = buckets[idx]
        return Rgb(accR[idx] / n, accG[idx] / n, accB[idx] / n)
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

    private fun chroma(c: Rgb): Float {
        val mx = max(c.r, max(c.g, c.b)).toFloat()
        val mn = min(c.r, min(c.g, c.b)).toFloat()
        return mx - mn
    }

    private fun luma(c: Rgb): Float = (0.2126f * c.r + 0.7152f * c.g + 0.0722f * c.b) / 255f

    private fun hue(c: Rgb): Float {
        val r = c.r / 255f
        val g = c.g / 255f
        val b = c.b / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        if (d < 1e-5f) return 0f
        val h = when (mx) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        return ((h * 60f) + 360f) % 360f
    }

    private fun hueDist(a: Rgb, b: Rgb): Float {
        val d = abs(hue(a) - hue(b))
        return min(d, 360f - d)
    }

    private fun rotate(c: Rgb, deg: Float): Rgb {
        val h = (hue(c) + deg + 360f) % 360f
        val s = chroma(c) / 255f
        val l = luma(c)
        return hsl(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    private fun hsl(h: Float, s: Float, l: Float): Rgb {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = h / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return Rgb(
            ((r1 + m) * 255f).toInt().coerceIn(0, 255),
            ((g1 + m) * 255f).toInt().coerceIn(0, 255),
            ((b1 + m) * 255f).toInt().coerceIn(0, 255)
        )
    }
}
