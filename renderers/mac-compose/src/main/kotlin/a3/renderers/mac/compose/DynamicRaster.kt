package a3.renderers.mac.compose

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

internal object DynamicRaster {
    fun paletteStrip(tonal: DynamicPalette.Tonal): BufferedImage {
        val w = 240
        val h = 48
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)
        val roles = tonal.roles()
        val slot = w / roles.size
        roles.forEachIndexed { i, rgb ->
            g.color = Color(rgb.r, rgb.g, rgb.b)
            g.fillRect(i * slot, 0, slot, h)
        }
        g.dispose()
        return image
    }

    fun contrastSample(backdrop: IntArray, width: Int, height: Int, text: DynamicPalette.Rgb): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        var i = 0
        while (i < backdrop.size) {
            image.setRGB(i % width, i / width, backdrop[i])
            i++
        }
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(text.r, text.g, text.b)
        g.font = Font("SansSerif", Font.BOLD, 18)
        g.drawString("Ag", 8, height - 10)
        g.dispose()
        return image
    }

    fun typeSample(sizeSp: Float, weight: Int, width: Float, trackingEm: Float, label: String): BufferedImage {
        val image = BufferedImage(200, 64, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 200, 64)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val style = if (weight >= 450) Font.BOLD else Font.PLAIN
        g.font = Font("SansSerif", style, sizeSp.toInt().coerceAtLeast(8))
        g.color = Color.BLACK
        val extra = (trackingEm * sizeSp).toInt()
        var x = 8
        for (ch in label) {
            g.drawString(ch.toString(), x, 40)
            x += g.fontMetrics.charWidth(ch) + extra
        }
        g.dispose()
        return scaleX(image, width)
    }

    private fun scaleX(src: BufferedImage, width: Float): BufferedImage {
        if (width == 1f) return src
        val w = (src.width * width).toInt().coerceAtLeast(1)
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, src.width, src.height)
        g.drawImage(src, 0, 0, w, src.height, null)
        g.dispose()
        return out
    }

    fun glyphFrame(points: List<GlyphGeometry.Pt>, size: Int = 64): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, size, size)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        if (points.size >= 2) {
            var i = 1
            while (i < points.size) {
                val a = points[i - 1]
                val b = points[i]
                g.drawLine(
                    (a.x * size).toInt(),
                    (a.y * size).toInt(),
                    (b.x * size).toInt(),
                    (b.y * size).toInt()
                )
                i++
            }
        }
        g.dispose()
        return image
    }

    fun morphStrip(from: GlyphGeometry.Kind, to: GlyphGeometry.Kind): BufferedImage {
        val cell = 64
        val frames = 5
        val image = BufferedImage(cell * frames, cell, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, image.width, image.height)
        var i = 0
        while (i < frames) {
            val t = i / (frames - 1f)
            g.drawImage(glyphFrame(GlyphGeometry.morph(from, to, t), cell), i * cell, 0, null)
            i++
        }
        g.dispose()
        return image
    }

    fun inkCount(image: BufferedImage): Int {
        var n = 0
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val c = Color(image.getRGB(x, y), true)
                if (c.red + c.green + c.blue < 80) n++
                x++
            }
            y++
        }
        return n
    }

    fun fingerprint(image: BufferedImage): Long {
        var h = 1125899906842597L
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                h = 31 * h + image.getRGB(x, y)
                x++
            }
            y++
        }
        return h
    }

    fun contentRight(image: BufferedImage): Int {
        var x = image.width - 1
        while (x >= 0) {
            var y = 0
            while (y < image.height) {
                val c = Color(image.getRGB(x, y), true)
                if (c.red + c.green + c.blue < 80) return x
                y++
            }
            x--
        }
        return 0
    }

    fun write(image: BufferedImage, file: File) {
        file.parentFile?.mkdirs()
        ImageIO.write(image, "png", file)
    }
}
