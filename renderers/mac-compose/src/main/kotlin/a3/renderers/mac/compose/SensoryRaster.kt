package a3.renderers.mac.compose

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.roundToInt

internal object SensoryRaster {
    const val BADGE_X = 16
    const val BADGE_Y = 16
    const val BADGE_SIZE = 20

    fun applyRefraction(src: BufferedImage): BufferedImage {
        val w = src.width
        val h = src.height
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val sx = (x + GlassOptics.offsetX(x, y)).roundToInt().coerceIn(0, w - 1)
                val sy = (y + GlassOptics.offsetY(x, y)).roundToInt().coerceIn(0, h - 1)
                out.setRGB(x, y, src.getRGB(sx, sy))
                x++
            }
            y++
        }
        return out
    }

    fun applyNoise(src: BufferedImage): BufferedImage {
        val w = src.width
        val h = src.height
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val p = Color(src.getRGB(x, y), true)
                val g = (GlassOptics.noiseAt(x, y) * 255f).roundToInt()
                val r = (p.red + g).coerceIn(0, 255)
                val green = (p.green + g).coerceIn(0, 255)
                val b = (p.blue + g).coerceIn(0, 255)
                out.setRGB(x, y, Color(r, green, b, p.alpha).rgb)
                x++
            }
            y++
        }
        return out
    }

    fun stampBadge(src: BufferedImage, x0: Int = BADGE_X, y0: Int = BADGE_Y, size: Int = BADGE_SIZE): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.color = Color.BLACK
        g.fillRect(x0, y0, size, size)
        g.dispose()
        return out
    }

    fun glass(refract: Boolean, noise: Boolean, badge: Boolean, width: Int = 160, height: Int = 80): BufferedImage {
        val painted = MacGlassRaster.paint(MacGlassRaster.checker(width, height), blur = true)
        var out = painted
        if (refract) out = applyRefraction(out)
        if (noise) out = applyNoise(out)
        if (badge) out = stampBadge(out)
        return out
    }

    fun particleFrame(trigger: Boolean, t: Float, width: Int = 80, height: Int = 48): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color(16, 16, 20)
        g.fillRect(0, 0, width, height)
        g.dispose()
        val sparks = ParticleField.frame(trigger, t)
        for (spark in sparks) {
            val x = (spark.x * (width - 1)).roundToInt().coerceIn(0, width - 1)
            val y = (spark.y * (height - 1)).roundToInt().coerceIn(0, height - 1)
            val a = (spark.life * 255f).roundToInt().coerceIn(0, 255)
            if (a > 0) image.setRGB(x, y, Color(255, 255, 240, a).rgb)
        }
        return image
    }

    fun particleStrip(): BufferedImage {
        val fw = 80
        val fh = 48
        val times = floatArrayOf(0f, 0.25f, 0.5f, 0.85f)
        val strip = BufferedImage(fw * times.size, fh, BufferedImage.TYPE_INT_ARGB)
        var i = 0
        while (i < times.size) {
            val frame = particleFrame(true, times[i], fw, fh)
            var y = 0
            while (y < fh) {
                var x = 0
                while (x < fw) {
                    strip.setRGB(i * fw + x, y, frame.getRGB(x, y))
                    x++
                }
                y++
            }
            i++
        }
        return strip
    }

    fun maskingFrame(width: Int = 160, height: Int = 48): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        var y = 12
        while (y < 36) {
            var x = 0
            while (x < width) {
                val a = (AlphaMask.edge(x + 0.5f, width.toFloat()) * 255f).roundToInt().coerceIn(0, 255)
                image.setRGB(x, y, Color(0, 0, 0, a).rgb)
                x++
            }
            y++
        }
        return image
    }

    fun differCount(a: BufferedImage, b: BufferedImage): Int {
        var n = 0
        var y = 0
        while (y < a.height) {
            var x = 0
            while (x < a.width) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) n++
                x++
            }
            y++
        }
        return n
    }

    fun meanAbs(a: BufferedImage, b: BufferedImage): Double {
        var acc = 0.0
        var n = 0
        var y = 0
        while (y < a.height) {
            var x = 0
            while (x < a.width) {
                val pa = Color(a.getRGB(x, y), true)
                val pb = Color(b.getRGB(x, y), true)
                acc += abs(pa.red - pb.red)
                acc += abs(pa.green - pb.green)
                acc += abs(pa.blue - pb.blue)
                n += 3
                x++
            }
            y++
        }
        return if (n == 0) 0.0 else acc / n
    }

    fun badgeIntact(a: BufferedImage, b: BufferedImage): Boolean {
        var y = BADGE_Y
        while (y < BADGE_Y + BADGE_SIZE) {
            var x = BADGE_X
            while (x < BADGE_X + BADGE_SIZE) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) return false
                if (Color(a.getRGB(x, y), true).alpha != 255) return false
                x++
            }
            y++
        }
        return true
    }

    fun brightCount(image: BufferedImage): Int {
        var n = 0
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val p = Color(image.getRGB(x, y), true)
                if (p.red > 180 && p.alpha > 40) n++
                x++
            }
            y++
        }
        return n
    }
}
