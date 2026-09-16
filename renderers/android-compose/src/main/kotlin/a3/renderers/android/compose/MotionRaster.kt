// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 A3 contributors

package a3.renderers.android.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object MotionRaster {
    fun springStrip(samples: List<MotionPhysics.State>, from: Float, target: Float): Bitmap {
        val w = 240
        val h = 80
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val axis = Paint().apply { color = Color.GRAY }
        canvas.drawLine(0f, 40f, w.toFloat(), 40f, axis)
        val fromX = 20f
        val span = 200f
        fun map(x: Float) = fromX + (x - from) / (target - from) * span
        val line = Paint().apply {
            color = Color.YELLOW
            strokeWidth = 2f
        }
        var i = 1
        while (i < samples.size) {
            val x0 = (i - 1f) / (samples.size - 1f) * (w - 1)
            val x1 = i.toFloat() / (samples.size - 1f) * (w - 1)
            val y0 = 70f - (map(samples[i - 1].x) - fromX) / span * 50f
            val y1 = 70f - (map(samples[i].x) - fromX) / span * 50f
            canvas.drawLine(x0, y0, x1, y1, line)
            i++
        }
        val targetPaint = Paint().apply { color = Color.CYAN }
        canvas.drawLine(map(target), 10f, map(target), 70f, targetPaint)
        return bitmap
    }

    fun springFrame(x: Float, from: Float, target: Float): Bitmap {
        val w = 160
        val h = 80
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val cx = 16f + (x - from) / (target - from) * 128f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(cx, 40f, 10f, p)
        return bitmap
    }

    fun sharedFrame(rect: MotionPhysics.Rect, label: String): Bitmap {
        val w = 160
        val h = 80
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(20, 20, 24))
        val fill = Paint().apply { color = Color.rgb(208, 55, 255) }
        canvas.drawRect(rect.l, rect.t, rect.r, rect.b, fill)
        val text = Paint().apply {
            color = Color.WHITE
            textSize = 14f
        }
        canvas.drawText(label, rect.l + 4f, rect.t + 16f, text)
        return bitmap
    }

    fun fluidFrame(width: Float, boxes: List<MotionPhysics.Box>): Bitmap {
        val w = 400
        val h = 80
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val border = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(0f, 0f, width, h.toFloat(), border)
        val fill = Paint().apply { color = Color.rgb(40, 40, 40) }
        for (b in boxes) {
            canvas.drawRect(b.x, b.y, b.x + b.w, b.y + b.wH, fill)
        }
        return bitmap
    }

    fun rippleFrame(originX: Int, originY: Int, tMs: Float, reduced: Boolean = false): Bitmap {
        val w = 120
        val h = 120
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(16, 16, 20))
        if (reduced) return bitmap
        val r = MotionPhysics.rippleRadius(tMs, 48f, 90f)
        val a = MotionPhysics.rippleAlpha(tMs, 140f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((a * 220).roundToInt(), 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(originX.toFloat(), originY.toFloat(), r, p)
        return bitmap
    }

    fun rubberFrame(overscroll: Float, reduced: Boolean = false): Bitmap {
        val w = 160
        val h = 80
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val clip = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
        }
        canvas.drawRect(20f, 8f, 140f, 72f, clip)
        val stretch = if (reduced) 0f else MotionPhysics.rubberStretch(overscroll)
        val fill = Paint().apply { color = Color.BLACK }
        canvas.drawRect(28f, 16f, 132f, 64f + stretch, fill)
        return bitmap
    }

    fun peakLumaOffset(bitmap: Bitmap): Pair<Int, Int> {
        var best = -1
        var bx = 0
        var by = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val c = bitmap.getPixel(x, y)
                val luma = Color.red(c) + Color.green(c) + Color.blue(c)
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

    fun contentBottom(bitmap: Bitmap): Int {
        var y = bitmap.height - 1
        while (y >= 0) {
            var x = 0
            while (x < bitmap.width) {
                val c = bitmap.getPixel(x, y)
                if (Color.red(c) + Color.green(c) + Color.blue(c) < 40 && Color.alpha(c) > 200) {
                    return y
                }
                x++
            }
            y--
        }
        return 0
    }

    fun write(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    fun comparisonFrame(xMotion: Float, xReduced: Float, from: Float, target: Float): Bitmap {
        val w = 160
        val h = 80
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val divider = Paint().apply { color = Color.GRAY }
        canvas.drawLine(80f, 0f, 80f, h.toFloat(), divider)
        fun map(x: Float, origin: Float) = origin + (x - from) / (target - from) * 56f
        canvas.drawCircle(map(xMotion, 12f), 40f, 8f, p)
        canvas.drawCircle(map(xReduced, 92f), 40f, 8f, p)
        return bitmap
    }

    fun encodeFfmpeg(dir: File, count: Int, out: File, fps: Int, gif: Boolean) {
        out.parentFile?.mkdirs()
        require(count > 0)
        val pattern = File(dir, "f%03d.png").absolutePath
        val ffmpeg = ffmpegBin()
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

    private fun ffmpegBin(): String {
        val candidates = listOf(
            "ffmpeg",
            "/opt/homebrew/bin/ffmpeg",
            "/usr/bin/ffmpeg"
        )
        return candidates.firstOrNull { File(it).canExecute() } ?: "ffmpeg"
    }

    fun dist(ax: Int, ay: Int, bx: Int, by: Int): Float {
        val dx = (ax - bx).toFloat()
        val dy = (ay - by).toFloat()
        return sqrt(dx * dx + dy * dy)
    }
}
