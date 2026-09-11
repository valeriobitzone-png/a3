package a3.renderers.android.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.File

internal object ShaderRaster {
    fun gradientFrame(width: Int, height: Int, timeSec: Float, pal: Triple<ShaderGradient.Stop, ShaderGradient.Stop, ShaderGradient.Stop> = ShaderGradient.palette()): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var y = 0
        while (y < height) {
            val v = if (height <= 1) 0f else y / (height - 1f)
            var x = 0
            while (x < width) {
                val u = if (width <= 1) 0f else x / (width - 1f)
                bitmap.setPixel(x, y, ShaderGradient.shade(u, v, timeSec, pal))
                x++
            }
            y++
        }
        return bitmap
    }

    fun modalFrame(open: Float, width: Int = 160, height: Int = 80): Bitmap {
        val checker = GlassRaster.checker(width, height, 8)
        val pixels = IntArray(width * height)
        checker.getPixels(pixels, 0, width, 0, 0, width, height)
        val blur = ModalBackdrop.blurPx(open)
        val blurred = if (blur > 0.5f) {
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            out.setPixels(GlassRaster.gaussian(pixels, width, height, blur), 0, width, 0, 0, width, height)
            out
        } else {
            checker
        }
        val canvas = Canvas(blurred)
        val a = ModalBackdrop.overlayAlpha(open)
        if (a > 0f) {
            canvas.drawColor(Color.argb((a * 255f).toInt().coerceIn(0, 255), 0, 0, 0))
        }
        return blurred
    }

    fun parallaxFrame(tilt: ParallaxLayers.Tilt, available: Boolean, width: Int = 160, height: Int = 80): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val colors = intArrayOf(Color.BLACK, Color.rgb(204, 136, 0), Color.rgb(40, 40, 48))
        val sizes = floatArrayOf(28f, 40f, 52f)
        var layer = ParallaxLayers.BG
        while (layer >= ParallaxLayers.FG) {
            val cx = 80f + ParallaxLayers.offsetX(layer, tilt, available)
            val cy = 40f + ParallaxLayers.offsetY(layer, tilt, available)
            val s = sizes[layer]
            val p = Paint().apply { color = colors[layer] }
            canvas.drawRoundRect(RectF(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f), 8f, 8f, p)
            layer--
        }
        return bitmap
    }

    fun ambientFrame(t: Float, kind: AmbientChrome.Kind, granted: Boolean, width: Int = 240, height: Int = 80): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 14f
        }
        if (!granted) {
            canvas.drawText(AmbientChrome.UNAVAILABLE, 12f, 44f, text)
            return bitmap
        }
        val r = AmbientChrome.morph(t)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((GraphicsTokens.snapshot.fillOpacity * 255).toInt(), 0, 0, 0)
        }
        canvas.drawRoundRect(RectF(r.x, r.y, r.x + r.w, r.y + r.h), r.radius, r.radius, fill)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f
        }
        canvas.drawText(AmbientChrome.caption(kind), r.x + 12f, r.y + r.h * 0.62f, ink)
        return bitmap
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

    fun write(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
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

    fun layerCenterX(bitmap: Bitmap, color: Int): Int {
        var acc = 0L
        var n = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if (bitmap.getPixel(x, y) == color) {
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
