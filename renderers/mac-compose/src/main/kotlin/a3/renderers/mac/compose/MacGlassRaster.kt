package a3.renderers.mac.compose

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object MacGlassRaster {
    const val BLUR_UNAVAILABLE = "blur unavailable"
    const val SATURATION_DELTA_MIN = 0.01
    const val BLUR_ENERGY_RATIO_MAX = 0.75

    fun checker(width: Int, height: Int, cell: Int = 8): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val a = ((x / cell) + (y / cell)) and 1
                val color = if (a == 0) Color(220, 40, 40).rgb else Color(40, 200, 220).rgb
                image.setRGB(x, y, color)
                x++
            }
            y++
        }
        return image
    }

    fun paint(
        backdrop: BufferedImage,
        blur: Boolean,
        vibrancy: Boolean = true,
        highlights: Boolean = true,
        shadows: Boolean = true,
        overlayInk: BufferedImage? = null,
        fill: Boolean = true,
        tokens: GraphicsTokens.Snapshot = GraphicsTokens.snapshot
    ): BufferedImage {
        val w = backdrop.width
        val h = backdrop.height
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(backdrop, 0, 0, null)
        if (shadows) {
            drawShadow(g, w, h, tokens.ambient.opacity, tokens.ambient.offsetXPx, tokens.ambient.offsetYPx, tokens.ambient.blurPx, tokens)
            drawShadow(g, w, h, tokens.key.opacity, tokens.key.offsetXPx, tokens.key.offsetYPx, tokens.key.blurPx, tokens)
        }
        val glass = processGlass(backdrop, blur, vibrancy, tokens)
        g.drawImage(clipSquircle(glass, tokens), 0, 0, null)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        if (fill) {
            g.color = Color(0, 0, 0, (tokens.fillOpacity * 255).roundToInt())
            g.fill(SquircleGeometry.awtPath(w.toFloat(), h.toFloat(), tokens.radiusPx, tokens.superellipseN))
        }
        if (!blur) {
            g.color = Color.BLACK
            g.font = Font(Font.DIALOG, Font.PLAIN, 14)
            g.drawString(BLUR_UNAVAILABLE, 12, h - 12)
        }
        g.dispose()
        if (highlights) drawRims(out, tokens)
        if (overlayInk != null) stampInk(out, overlayInk)
        return out
    }

    fun paintRoundRectFill(width: Int, height: Int, radius: Float): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), radius * 2f, radius * 2f))
        g.dispose()
        return image
    }

    fun paintSquircleFill(width: Int, height: Int, radius: Float, n: Float): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.fill(SquircleGeometry.awtPath(width.toFloat(), height.toFloat(), radius, n))
        g.dispose()
        return image
    }

    fun paintNested(width: Int, height: Int, outer: Float, padding: Float, tokens: GraphicsTokens.Snapshot = GraphicsTokens.snapshot): BufferedImage {
        val inner = SquircleGeometry.nestedRadius(outer, padding, tokens.nestedMinPx)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.fill(SquircleGeometry.awtPath(width.toFloat(), height.toFloat(), outer, tokens.superellipseN))
        g.color = Color.WHITE
        g.translate(padding.toDouble(), padding.toDouble())
        g.fill(
            SquircleGeometry.awtPath(
                width - 2 * padding,
                height - 2 * padding,
                inner,
                tokens.superellipseN
            )
        )
        g.dispose()
        return image
    }

    fun meanSaturation(image: BufferedImage, tokens: GraphicsTokens.Snapshot = GraphicsTokens.snapshot): Double {
        var sum = 0.0
        var count = 0
        val w = image.width.toFloat()
        val h = image.height.toFloat()
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                if (SquircleGeometry.contains(x.toFloat(), y.toFloat(), w, h, tokens.radiusPx, tokens.superellipseN)) {
                    sum += saturation(image.getRGB(x, y))
                    count++
                }
                x++
            }
            y++
        }
        return if (count == 0) 0.0 else sum / count
    }

    fun edgeEnergy(image: BufferedImage): Double {
        var sum = 0.0
        var count = 0
        var y = 1
        while (y < image.height - 1) {
            var x = 1
            while (x < image.width - 1) {
                val dx = luma(image.getRGB(x + 1, y)) - luma(image.getRGB(x - 1, y))
                val dy = luma(image.getRGB(x, y + 1)) - luma(image.getRGB(x, y - 1))
                sum += sqrt((dx * dx + dy * dy).toDouble())
                count++
                x++
            }
            y++
        }
        return if (count == 0) 0.0 else sum / count
    }

    fun innerHighlightPresent(image: BufferedImage): Boolean {
        val y = 4
        val x = image.width / 2
        return luma(image.getRGB(x, y)) > luma(image.getRGB(x, y + 6)) + 4
    }

    fun outerHighlightPresent(image: BufferedImage): Boolean {
        val rim = image.getRGB(image.width / 2, 0)
        return luma(rim) < 200
    }

    fun highlightLighter(with: BufferedImage, without: BufferedImage, x: Int, y: Int): Boolean =
        luma(with.getRGB(x, y)) > luma(without.getRGB(x, y)) + 2

    fun highlightDarker(with: BufferedImage, without: BufferedImage, x: Int, y: Int): Boolean =
        luma(with.getRGB(x, y)) < luma(without.getRGB(x, y)) - 2

    fun innerHighlightBand(with: BufferedImage, without: BufferedImage): Boolean {
        val x = with.width / 2
        var y = 1
        while (y <= 4) {
            if (highlightLighter(with, without, x, y)) return true
            y++
        }
        return false
    }

    fun outerHighlightBand(with: BufferedImage, without: BufferedImage): Boolean {
        val x = with.width / 2
        var y = 0
        while (y <= 3) {
            if (highlightDarker(with, without, x, y)) return true
            y++
        }
        return false
    }

    fun solid(width: Int, height: Int, color: Int = Color(128, 128, 128).rgb): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color(color, true)
        g.fillRect(0, 0, width, height)
        g.dispose()
        return image
    }

    fun write(image: BufferedImage, file: File) {
        file.parentFile?.mkdirs()
        ImageIO.write(image, "png", file)
    }

    private fun drawRims(image: BufferedImage, tokens: GraphicsTokens.Snapshot) {
        val w = image.width
        val h = image.height
        val inside = BooleanArray(w * h)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                inside[y * w + x] = SquircleGeometry.contains(
                    x.toFloat(),
                    y.toFloat(),
                    w.toFloat(),
                    h.toFloat(),
                    tokens.radiusPx,
                    tokens.superellipseN
                )
                x++
            }
            y++
        }
        val edge = BooleanArray(w * h)
        y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val idx = y * w + x
                if (inside[idx]) {
                    val outN = y == 0 || !inside[idx - w]
                    val outS = y == h - 1 || !inside[idx + w]
                    val outW = x == 0 || !inside[idx - 1]
                    val outE = x == w - 1 || !inside[idx + 1]
                    edge[idx] = outN || outS || outW || outE
                }
                x++
            }
            y++
        }
        y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val idx = y * w + x
                if (edge[idx]) {
                    image.setRGB(x, y, mix(image.getRGB(x, y), Color.BLACK.rgb, tokens.outer.startAlpha))
                } else if (inside[idx]) {
                    val near = (y > 0 && edge[idx - w]) ||
                        (y < h - 1 && edge[idx + w]) ||
                        (x > 0 && edge[idx - 1]) ||
                        (x < w - 1 && edge[idx + 1])
                    if (near) {
                        image.setRGB(x, y, mix(image.getRGB(x, y), Color.WHITE.rgb, tokens.inner.startAlpha))
                    }
                }
                x++
            }
            y++
        }
    }

    private fun mix(src: Int, tint: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        val inv = 1f - a
        val s = Color(src, true)
        val t = Color(tint, true)
        return Color(
            (s.red * inv + t.red * a).roundToInt(),
            (s.green * inv + t.green * a).roundToInt(),
            (s.blue * inv + t.blue * a).roundToInt()
        ).rgb
    }

    private fun processGlass(
        backdrop: BufferedImage,
        blur: Boolean,
        vibrancy: Boolean,
        tokens: GraphicsTokens.Snapshot
    ): BufferedImage {
        val w = backdrop.width
        val h = backdrop.height
        var pixels = IntArray(w * h)
        backdrop.getRGB(0, 0, w, h, pixels, 0, w)
        if (blur) pixels = gaussian(pixels, w, h, tokens.blurRadiusPx)
        if (vibrancy) saturate(pixels, tokens.vibrancySaturation)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        out.setRGB(0, 0, w, h, pixels, 0, w)
        return out
    }

    private fun clipSquircle(src: BufferedImage, tokens: GraphicsTokens.Snapshot): BufferedImage {
        val w = src.width
        val h = src.height
        val mask = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = mask.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fill(SquircleGeometry.awtPath(w.toFloat(), h.toFloat(), tokens.radiusPx, tokens.superellipseN))
        g.dispose()
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val a = Color(mask.getRGB(x, y), true).alpha
                if (a > 0) {
                    val c = Color(src.getRGB(x, y), true)
                    out.setRGB(x, y, Color(c.red, c.green, c.blue, (c.alpha * a) / 255).rgb)
                }
                x++
            }
            y++
        }
        return out
    }

    private fun drawShadow(
        g: Graphics2D,
        w: Int,
        h: Int,
        opacity: Float,
        ox: Float,
        oy: Float,
        blur: Float,
        tokens: GraphicsTokens.Snapshot
    ) {
        val mask = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val mg = mask.createGraphics()
        mg.color = Color.BLACK
        mg.fill(SquircleGeometry.awtPath(w.toFloat(), h.toFloat(), tokens.radiusPx, tokens.superellipseN))
        mg.dispose()
        var pixels = IntArray(w * h)
        mask.getRGB(0, 0, w, h, pixels, 0, w)
        pixels = gaussian(pixels, w, h, blur)
        val tinted = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        var i = 0
        while (i < pixels.size) {
            val a = (Color(pixels[i], true).alpha * opacity).roundToInt().coerceIn(0, 255)
            pixels[i] = Color(0, 0, 0, a).rgb
            i++
        }
        tinted.setRGB(0, 0, w, h, pixels, 0, w)
        g.drawImage(tinted, ox.roundToInt(), oy.roundToInt(), null)
    }

    private fun stampInk(dest: BufferedImage, overlay: BufferedImage) {
        val w = min(dest.width, overlay.width)
        val h = min(dest.height, overlay.height)
        val white = Color.WHITE.rgb
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val px = overlay.getRGB(x, y)
                if (px != white && Color(px, true).alpha == 255) {
                    dest.setRGB(x, y, px)
                }
                x++
            }
            y++
        }
    }

    internal fun gaussian(src: IntArray, w: Int, h: Int, radius: Float): IntArray {
        val kernel = kernel(radius)
        val tmp = FloatArray(w * h * 4)
        val out = FloatArray(w * h * 4)
        unpack(src, tmp)
        convolveH(tmp, out, w, h, kernel)
        convolveV(out, tmp, w, h, kernel)
        val pixels = IntArray(w * h)
        pack(tmp, pixels)
        return pixels
    }

    private fun kernel(radius: Float): FloatArray {
        val sigma = max(0.5f, radius)
        val size = (sigma * 3f).toInt().coerceAtLeast(1)
        val k = FloatArray(size * 2 + 1)
        val s2 = 2f * sigma * sigma
        var sum = 0f
        var i = 0
        while (i < k.size) {
            val x = (i - size).toFloat()
            val v = exp(-(x * x) / s2)
            k[i] = v
            sum += v
            i++
        }
        i = 0
        while (i < k.size) {
            k[i] /= sum
            i++
        }
        return k
    }

    private fun unpack(src: IntArray, out: FloatArray) {
        var i = 0
        while (i < src.size) {
            val c = Color(src[i], true)
            val o = i * 4
            out[o] = c.alpha.toFloat()
            out[o + 1] = c.red.toFloat()
            out[o + 2] = c.green.toFloat()
            out[o + 3] = c.blue.toFloat()
            i++
        }
    }

    private fun pack(src: FloatArray, dest: IntArray) {
        var i = 0
        while (i < dest.size) {
            val o = i * 4
            dest[i] = Color(
                src[o + 1].roundToInt().coerceIn(0, 255),
                src[o + 2].roundToInt().coerceIn(0, 255),
                src[o + 3].roundToInt().coerceIn(0, 255),
                src[o].roundToInt().coerceIn(0, 255)
            ).rgb
            i++
        }
    }

    private fun convolveH(src: FloatArray, dest: FloatArray, w: Int, h: Int, k: FloatArray) {
        val r = k.size / 2
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                var a = 0f
                var cr = 0f
                var cg = 0f
                var cb = 0f
                var i = 0
                while (i < k.size) {
                    val xx = (x + i - r).coerceIn(0, w - 1)
                    val o = (y * w + xx) * 4
                    val kv = k[i]
                    a += src[o] * kv
                    cr += src[o + 1] * kv
                    cg += src[o + 2] * kv
                    cb += src[o + 3] * kv
                    i++
                }
                val d = (y * w + x) * 4
                dest[d] = a
                dest[d + 1] = cr
                dest[d + 2] = cg
                dest[d + 3] = cb
                x++
            }
            y++
        }
    }

    private fun convolveV(src: FloatArray, dest: FloatArray, w: Int, h: Int, k: FloatArray) {
        val r = k.size / 2
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                var a = 0f
                var cr = 0f
                var cg = 0f
                var cb = 0f
                var i = 0
                while (i < k.size) {
                    val yy = (y + i - r).coerceIn(0, h - 1)
                    val o = (yy * w + x) * 4
                    val kv = k[i]
                    a += src[o] * kv
                    cr += src[o + 1] * kv
                    cg += src[o + 2] * kv
                    cb += src[o + 3] * kv
                    i++
                }
                val d = (y * w + x) * 4
                dest[d] = a
                dest[d + 1] = cr
                dest[d + 2] = cg
                dest[d + 3] = cb
                x++
            }
            y++
        }
    }

    private fun saturate(pixels: IntArray, amount: Float) {
        val inv = 1f - amount
        val rW = 0.213f * inv
        val gW = 0.715f * inv
        val bW = 0.072f * inv
        var i = 0
        while (i < pixels.size) {
            val c = Color(pixels[i], true)
            val r = c.red.toFloat()
            val g = c.green.toFloat()
            val b = c.blue.toFloat()
            val nr = (rW + amount) * r + gW * g + bW * b
            val ng = rW * r + (gW + amount) * g + bW * b
            val nb = rW * r + gW * g + (bW + amount) * b
            pixels[i] = Color(
                nr.roundToInt().coerceIn(0, 255),
                ng.roundToInt().coerceIn(0, 255),
                nb.roundToInt().coerceIn(0, 255),
                c.alpha
            ).rgb
            i++
        }
    }

    private fun luma(c: Int): Int {
        val col = Color(c, true)
        return (0.2126 * col.red + 0.7152 * col.green + 0.0722 * col.blue).roundToInt()
    }

    private fun saturation(c: Int): Double {
        val col = Color(c, true)
        val r = col.red / 255.0
        val g = col.green / 255.0
        val b = col.blue / 255.0
        val maxv = max(r, max(g, b))
        val minv = min(r, min(g, b))
        return if (maxv == 0.0) 0.0 else (maxv - minv) / maxv
    }
}
