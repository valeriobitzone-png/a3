package a3.renderers.mac.compose

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

internal object ShaderRaster {
    fun gradientFrame(width: Int, height: Int, timeSec: Float, pal: Triple<ShaderGradient.Stop, ShaderGradient.Stop, ShaderGradient.Stop> = ShaderGradient.palette()): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        var y = 0
        while (y < height) {
            val v = if (height <= 1) 0f else y / (height - 1f)
            var x = 0
            while (x < width) {
                val u = if (width <= 1) 0f else x / (width - 1f)
                image.setRGB(x, y, ShaderGradient.shade(u, v, timeSec, pal))
                x++
            }
            y++
        }
        return image
    }

    fun modalFrame(open: Float, width: Int = 160, height: Int = 80): BufferedImage {
        val checker = MacGlassRaster.checker(width, height, 8)
        val pixels = IntArray(width * height)
        checker.getRGB(0, 0, width, height, pixels, 0, width)
        val blur = ModalBackdrop.blurPx(open)
        val blurred = if (blur > 0.5f) {
            val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            out.setRGB(0, 0, width, height, MacGlassRaster.gaussian(pixels, width, height, blur), 0, width)
            out
        } else {
            checker
        }
        val g = blurred.createGraphics()
        val a = ModalBackdrop.overlayAlpha(open)
        if (a > 0f) {
            g.color = Color(0, 0, 0, (a * 255f).toInt().coerceIn(0, 255))
            g.fillRect(0, 0, width, height)
        }
        g.dispose()
        return blurred
    }

    fun parallaxFrame(tilt: ParallaxLayers.Tilt, available: Boolean, width: Int = 160, height: Int = 80): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        val colors = arrayOf(Color.BLACK, Color(204, 136, 0), Color(40, 40, 48))
        val sizes = floatArrayOf(28f, 40f, 52f)
        var layer = ParallaxLayers.BG
        while (layer >= ParallaxLayers.FG) {
            val cx = 80f + ParallaxLayers.offsetX(layer, tilt, available)
            val cy = 40f + ParallaxLayers.offsetY(layer, tilt, available)
            val s = sizes[layer]
            g.color = colors[layer]
            g.fill(RoundRectangle2D.Float(cx - s / 2f, cy - s / 2f, s, s, 8f, 8f))
            layer--
        }
        g.dispose()
        return image
    }

    fun ambientFrame(t: Float, kind: AmbientChrome.Kind, granted: Boolean, width: Int = 240, height: Int = 80): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.font = Font("SansSerif", Font.PLAIN, 14)
        if (!granted) {
            g.color = Color.BLACK
            g.drawString(AmbientChrome.UNAVAILABLE, 12, 44)
            g.dispose()
            return image
        }
        val r = AmbientChrome.morph(t)
        g.color = Color(0, 0, 0, (GraphicsTokens.snapshot.fillOpacity * 255).toInt())
        g.fill(RoundRectangle2D.Float(r.x, r.y, r.w, r.h, r.radius * 2f, r.radius * 2f))
        g.color = Color.WHITE
        g.drawString(AmbientChrome.caption(kind), (r.x + 12f).toInt(), (r.y + r.h * 0.62f).toInt())
        g.dispose()
        return image
    }

    fun fps(frames: Int = 100, width: Int = 80, height: Int = 48): Double {
        val pal = ShaderGradient.palette()
        val start = System.nanoTime()
        var i = 0
        while (i < frames) {
            gradientFrame(width, height, i / 30f, pal)
            i++
        }
        val sec = (System.nanoTime() - start) / 1_000_000_000.0
        return if (sec <= 0.0) 10_000.0 else frames / sec
    }

    fun write(image: BufferedImage, file: File) {
        file.parentFile?.mkdirs()
        ImageIO.write(image, "png", file)
    }

    fun encodeFlow(dir: File, out: File) {
        dir.mkdirs()
        val pal = ShaderGradient.palette()
        var i = 0
        while (i < 24) {
            write(gradientFrame(160, 80, i / 12f, pal), File(dir, "f%03d.png".format(i)))
            i++
        }
        MotionRaster.encodeFfmpeg(dir, 24, out, 12, gif = false)
    }

    fun encodeParallax(dir: File, out: File) {
        dir.mkdirs()
        val tilts = listOf(0f, 0.35f, 0.7f, 1f, 0.7f, 0.35f, 0f, 0f)
        tilts.forEachIndexed { i, x ->
            write(parallaxFrame(ParallaxLayers.Tilt(x, x * 0.4f), true), File(dir, "f%03d.png".format(i)))
        }
        MotionRaster.encodeFfmpeg(dir, tilts.size, out, 8, gif = true)
    }

    fun encodeAmbient(dir: File, out: File) {
        dir.mkdirs()
        val spring = AmbientChrome.springExpand()
        val picked = (0 until 8).map { i ->
            val idx = (i / 7f * (spring.lastIndex)).toInt()
            spring[idx].x.coerceIn(0f, 1.15f)
        }
        picked.forEachIndexed { i, t ->
            write(ambientFrame(t, AmbientChrome.Kind.TIMER, true), File(dir, "f%03d.png".format(i)))
        }
        MotionRaster.encodeFfmpeg(dir, picked.size, out, 8, gif = true)
    }

    fun layerCenterX(image: BufferedImage, rgb: Int): Int {
        var acc = 0L
        var n = 0
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                if ((image.getRGB(x, y) and 0xFFFFFF) == (rgb and 0xFFFFFF)) {
                    acc += x
                    n++
                }
                x++
            }
            y++
        }
        return if (n == 0) 0 else (acc / n).toInt()
    }
}
