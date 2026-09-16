// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 A3 contributors

package a3.renderers.mac.compose

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object MotionRaster {
    fun springFrame(x: Float, from: Float, target: Float): BufferedImage {
        val w = 160
        val h = 80
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.BLACK
        g.fillRect(0, 0, w, h)
        val cx = 16f + (x - from) / (target - from) * 128f
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillOval((cx - 10).toInt(), 30, 20, 20)
        g.dispose()
        return image
    }

    fun sharedFrame(rect: MotionPhysics.Rect, label: String): BufferedImage {
        val image = BufferedImage(160, 80, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color(20, 20, 24)
        g.fillRect(0, 0, 160, 80)
        g.color = Color(208, 55, 255)
        g.fillRect(rect.l.toInt(), rect.t.toInt(), rect.w.toInt().coerceAtLeast(1), rect.h.toInt().coerceAtLeast(1))
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.PLAIN, 12)
        g.drawString(label, (rect.l + 4).toInt(), (rect.t + 16).toInt())
        g.dispose()
        return image
    }

    fun fluidFrame(width: Float, boxes: List<MotionPhysics.Box>): BufferedImage {
        val image = BufferedImage(400, 80, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 400, 80)
        g.color = Color.BLACK
        g.drawRect(0, 0, width.toInt(), 79)
        g.color = Color(40, 40, 40)
        for (b in boxes) {
            g.fillRect(b.x.toInt(), b.y.toInt(), b.w.toInt().coerceAtLeast(1), b.wH.toInt().coerceAtLeast(1))
        }
        g.dispose()
        return image
    }

    fun rippleFrame(originX: Int, originY: Int, tMs: Float, reduced: Boolean = false): BufferedImage {
        val image = BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color(16, 16, 20)
        g.fillRect(0, 0, 120, 120)
        if (!reduced) {
            val r = MotionPhysics.rippleRadius(tMs, 48f, 90f)
            val a = MotionPhysics.rippleAlpha(tMs, 140f)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(255, 255, 255, (a * 220).roundToInt())
            g.stroke = BasicStroke(3f)
            val d = (r * 2).toInt().coerceAtLeast(1)
            g.drawOval(originX - r.toInt(), originY - r.toInt(), d, d)
        }
        g.dispose()
        return image
    }

    fun rubberFrame(overscroll: Float, reduced: Boolean = false): BufferedImage {
        val image = BufferedImage(160, 80, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 160, 80)
        g.color = Color.LIGHT_GRAY
        g.drawRect(20, 8, 120, 64)
        val stretch = if (reduced) 0f else MotionPhysics.rubberStretch(overscroll)
        g.color = Color.BLACK
        g.fillRect(28, 16, 104, (48 + stretch).toInt())
        g.dispose()
        return image
    }

    fun peakLumaOffset(image: BufferedImage): Pair<Int, Int> {
        var best = -1
        var bx = 0
        var by = 0
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val c = Color(image.getRGB(x, y), true)
                val luma = c.red + c.green + c.blue
                if (luma > best) {
                    best = luma
                    bx = x
                    by = y
                }
                x++
            }
            y++
        }
        return bx to by
    }

    fun contentBottom(image: BufferedImage): Int {
        var y = image.height - 1
        while (y >= 0) {
            var x = 0
            while (x < image.width) {
                val c = Color(image.getRGB(x, y), true)
                if (c.red + c.green + c.blue < 40 && c.alpha > 200) return y
                x++
            }
            y--
        }
        return 0
    }

    fun write(image: BufferedImage, file: File) {
        file.parentFile?.mkdirs()
        ImageIO.write(image, "png", file)
    }

    fun dist(ax: Int, ay: Int, bx: Int, by: Int): Float {
        val dx = (ax - bx).toFloat()
        val dy = (ay - by).toFloat()
        return sqrt(dx * dx + dy * dy)
    }

    fun encodeFfmpeg(dir: File, count: Int, out: File, fps: Int, gif: Boolean) {
        out.parentFile?.mkdirs()
        require(count > 0)
        val pattern = File(dir, "f%03d.png").absolutePath
        val ffmpeg = listOf(
            "ffmpeg",
            "/opt/homebrew/bin/ffmpeg",
            "/usr/bin/ffmpeg"
        ).firstOrNull { File(it).canExecute() } ?: "ffmpeg"
        val cmd = if (gif) {
            listOf(
                ffmpeg, "-y", "-framerate", fps.toString(),
                "-i", pattern,
                "-vf", "fps=$fps,scale=160:80:flags=neighbor,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse",
                out.absolutePath
            )
        } else {
            listOf(
                ffmpeg, "-y", "-framerate", fps.toString(),
                "-i", pattern,
                "-pix_fmt", "yuv420p",
                out.absolutePath
            )
        }
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val log = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        require(code == 0 && out.length() > 0) { "ffmpeg $code $log" }
    }
}
