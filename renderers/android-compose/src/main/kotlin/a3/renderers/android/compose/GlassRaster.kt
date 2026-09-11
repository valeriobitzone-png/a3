package a3.renderers.android.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import java.io.File
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Software liquid-glass raster. Compose [GlassSurface] uses the same tokens;
 * this painter is the measurable path for LG-001..007 (clock-free).
 */
internal object GlassRaster {
    const val BLUR_UNAVAILABLE = "blur unavailable"
    const val SATURATION_DELTA_MIN = 0.01
    const val BLUR_ENERGY_RATIO_MAX = 0.75

    fun solid(width: Int, height: Int, color: Int = Color.rgb(128, 128, 128)): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    fun wallpaper(width: Int, height: Int): Bitmap {
        val pixels = DynamicPalette.fixtureWallpaper(width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun checker(width: Int, height: Int, cell: Int = 8): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val a = ((x / cell) + (y / cell)) and 1
                val color = if (a == 0) Color.rgb(220, 40, 40) else Color.rgb(40, 200, 220)
                bitmap.setPixel(x, y, color)
                x++
            }
            y++
        }
        return bitmap
    }

    fun paint(
        backdrop: Bitmap,
        blur: Boolean,
        vibrancy: Boolean = true,
        highlights: Boolean = true,
        shadows: Boolean = true,
        overlayInk: Bitmap? = null,
        fill: Boolean = true,
        tokens: GraphicsTokens.Snapshot = GraphicsTokens.snapshot
    ): Bitmap {
        val w = backdrop.width
        val h = backdrop.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(backdrop, 0f, 0f, null)
        val radius = tokens.radiusPx
        val n = tokens.superellipseN
        if (shadows) {
            drawShadow(canvas, w, h, tokens.ambient, tokens, 0f)
            drawShadow(
                canvas,
                w,
                h,
                GraphicsTokens.Shadow(
                    tokens.key.opacity,
                    tokens.key.offsetXPx,
                    tokens.key.offsetYPx,
                    tokens.key.blurPx
                ),
                tokens,
                0f
            )
        }
        val glass = processGlass(backdrop, blur, vibrancy, tokens)
        val clipped = clipSquircle(glass, radius, n)
        canvas.drawBitmap(clipped, 0f, 0f, null)
        if (fill) {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(tokens.fillColor, tokens.fillOpacity)
            }
            canvas.drawPath(SquircleGeometry.androidPath(w.toFloat(), h.toFloat(), radius, n), fillPaint)
        }
        if (highlights) {
            drawRims(out, tokens)
        }
        if (!blur) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 14f
            }
            canvas.drawText(BLUR_UNAVAILABLE, 12f, h - 12f, text)
        }
        if (overlayInk != null) {
            stampInk(out, overlayInk)
        }
        return out
    }

    fun paintRoundRectFill(width: Int, height: Int, radius: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
        return bitmap
    }

    fun paintSquircleFill(width: Int, height: Int, radius: Float, n: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        canvas.drawPath(SquircleGeometry.androidPath(width.toFloat(), height.toFloat(), radius, n), paint)
        return bitmap
    }

    fun paintNested(
        width: Int,
        height: Int,
        outer: Float,
        padding: Float,
        tokens: GraphicsTokens.Snapshot = GraphicsTokens.snapshot
    ): Bitmap {
        val inner = SquircleGeometry.nestedRadius(outer, padding, tokens.nestedMinPx)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        canvas.drawPath(SquircleGeometry.androidPath(width.toFloat(), height.toFloat(), outer, tokens.superellipseN), outerPaint)
        val inset = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }
        canvas.save()
        canvas.translate(padding, padding)
        val iw = width - 2 * padding
        val ih = height - 2 * padding
        canvas.drawPath(SquircleGeometry.androidPath(iw, ih, inner, tokens.superellipseN), inset)
        canvas.restore()
        return bitmap
    }

    fun meanSaturation(bitmap: Bitmap, tokens: GraphicsTokens.Snapshot = GraphicsTokens.snapshot): Double {
        var sum = 0.0
        var count = 0
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if (SquircleGeometry.contains(x.toFloat(), y.toFloat(), w, h, tokens.radiusPx, tokens.superellipseN)) {
                    sum += saturation(bitmap.getPixel(x, y))
                    count++
                }
                x++
            }
            y++
        }
        return if (count == 0) 0.0 else sum / count
    }

    fun edgeEnergy(bitmap: Bitmap): Double {
        var sum = 0.0
        var count = 0
        var y = 1
        while (y < bitmap.height - 1) {
            var x = 1
            while (x < bitmap.width - 1) {
                val dx = luma(bitmap.getPixel(x + 1, y)) - luma(bitmap.getPixel(x - 1, y))
                val dy = luma(bitmap.getPixel(x, y + 1)) - luma(bitmap.getPixel(x, y - 1))
                sum += sqrt((dx * dx + dy * dy).toDouble())
                count++
                x++
            }
            y++
        }
        return if (count == 0) 0.0 else sum / count
    }

    fun innerHighlightPresent(bitmap: Bitmap, tokens: GraphicsTokens.Snapshot = GraphicsTokens.snapshot): Boolean {
        val plain = paint(
            checker(bitmap.width, bitmap.height),
            blur = true,
            vibrancy = true,
            highlights = false,
            shadows = false,
            tokens = tokens
        )
        return highlightLighter(bitmap, plain, bitmap.width / 2, 1)
    }

    fun outerHighlightPresent(bitmap: Bitmap, tokens: GraphicsTokens.Snapshot = GraphicsTokens.snapshot): Boolean {
        val plain = paint(
            checker(bitmap.width, bitmap.height),
            blur = true,
            vibrancy = true,
            highlights = false,
            shadows = false,
            tokens = tokens
        )
        return highlightDarker(bitmap, plain, bitmap.width / 2, 0)
    }

    fun highlightLighter(with: Bitmap, without: Bitmap, x: Int, y: Int): Boolean =
        luma(with.getPixel(x, y)) > luma(without.getPixel(x, y)) + 2

    fun highlightDarker(with: Bitmap, without: Bitmap, x: Int, y: Int): Boolean =
        luma(with.getPixel(x, y)) < luma(without.getPixel(x, y)) - 2

    fun innerHighlightBand(with: Bitmap, without: Bitmap): Boolean {
        val x = with.width / 2
        var y = 1
        while (y <= 4) {
            if (highlightLighter(with, without, x, y)) return true
            y++
        }
        return false
    }

    fun outerHighlightBand(with: Bitmap, without: Bitmap): Boolean {
        val x = with.width / 2
        var y = 0
        while (y <= 3) {
            if (highlightDarker(with, without, x, y)) return true
            y++
        }
        return false
    }

    fun fingerprint(bitmap: Bitmap): Long {
        var hash = 17L
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                hash = 31L * hash + bitmap.getPixel(x, y).toLong()
                x++
            }
            y++
        }
        return hash xor (bitmap.width.toLong() shl 16) xor bitmap.height.toLong()
    }

    fun write(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun processGlass(
        backdrop: Bitmap,
        blur: Boolean,
        vibrancy: Boolean,
        tokens: GraphicsTokens.Snapshot
    ): Bitmap {
        var pixels = IntArray(backdrop.width * backdrop.height)
        backdrop.getPixels(pixels, 0, backdrop.width, 0, 0, backdrop.width, backdrop.height)
        if (blur) {
            pixels = gaussian(pixels, backdrop.width, backdrop.height, tokens.blurRadiusPx)
        }
        if (vibrancy) {
            saturate(pixels, tokens.vibrancySaturation)
        }
        val out = Bitmap.createBitmap(backdrop.width, backdrop.height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, backdrop.width, 0, 0, backdrop.width, backdrop.height)
        return out
    }

    private fun clipSquircle(src: Bitmap, radius: Float, n: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawPath(SquircleGeometry.androidPath(src.width.toFloat(), src.height.toFloat(), radius, n), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun drawShadow(
        canvas: Canvas,
        w: Int,
        h: Int,
        shadow: GraphicsTokens.Shadow,
        tokens: GraphicsTokens.Snapshot,
        extra: Float
    ) {
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val mc = Canvas(mask)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        mc.drawPath(
            SquircleGeometry.androidPath(w.toFloat(), h.toFloat(), tokens.radiusPx, tokens.superellipseN),
            fill
        )
        var pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)
        pixels = gaussian(pixels, w, h, shadow.blurPx)
        val tinted = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var i = 0
        while (i < pixels.size) {
            val a = (Color.alpha(pixels[i]) * shadow.opacity).roundToInt().coerceIn(0, 255)
            pixels[i] = Color.argb(a, 0, 0, 0)
            i++
        }
        tinted.setPixels(pixels, 0, w, 0, 0, w, h)
        canvas.drawBitmap(tinted, shadow.offsetXPx + extra, shadow.offsetYPx + extra, null)
    }

    private fun drawRims(bitmap: Bitmap, tokens: GraphicsTokens.Snapshot) {
        val w = bitmap.width
        val h = bitmap.height
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
                    bitmap.setPixel(x, y, mix(bitmap.getPixel(x, y), Color.BLACK, tokens.outer.startAlpha))
                } else if (inside[idx]) {
                    val near = (y > 0 && edge[idx - w]) ||
                        (y < h - 1 && edge[idx + w]) ||
                        (x > 0 && edge[idx - 1]) ||
                        (x < w - 1 && edge[idx + 1])
                    if (near) {
                        bitmap.setPixel(x, y, mix(bitmap.getPixel(x, y), Color.WHITE, tokens.inner.startAlpha))
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
        return Color.argb(
            255,
            (Color.red(src) * inv + Color.red(tint) * a).roundToInt(),
            (Color.green(src) * inv + Color.green(tint) * a).roundToInt(),
            (Color.blue(src) * inv + Color.blue(tint) * a).roundToInt()
        )
    }

    private fun stampInk(dest: Bitmap, overlay: Bitmap) {
        val w = min(dest.width, overlay.width)
        val h = min(dest.height, overlay.height)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val px = overlay.getPixel(x, y)
                if (px != Color.WHITE && Color.alpha(px) == 255) {
                    dest.setPixel(x, y, px or (0xFF shl 24))
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
            val c = src[i]
            val o = i * 4
            out[o] = Color.alpha(c).toFloat()
            out[o + 1] = Color.red(c).toFloat()
            out[o + 2] = Color.green(c).toFloat()
            out[o + 3] = Color.blue(c).toFloat()
            i++
        }
    }

    private fun pack(src: FloatArray, dest: IntArray) {
        var i = 0
        while (i < dest.size) {
            val o = i * 4
            dest[i] = Color.argb(
                src[o].roundToInt().coerceIn(0, 255),
                src[o + 1].roundToInt().coerceIn(0, 255),
                src[o + 2].roundToInt().coerceIn(0, 255),
                src[o + 3].roundToInt().coerceIn(0, 255)
            )
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
            val c = pixels[i]
            val r = Color.red(c).toFloat()
            val g = Color.green(c).toFloat()
            val b = Color.blue(c).toFloat()
            val nr = (rW + amount) * r + gW * g + bW * b
            val ng = rW * r + (gW + amount) * g + bW * b
            val nb = rW * r + gW * g + (bW + amount) * b
            pixels[i] = Color.argb(
                Color.alpha(c),
                nr.roundToInt().coerceIn(0, 255),
                ng.roundToInt().coerceIn(0, 255),
                nb.roundToInt().coerceIn(0, 255)
            )
            i++
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).roundToInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun luma(c: Int): Int =
        (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)).roundToInt()

    private fun saturation(c: Int): Double {
        val r = Color.red(c) / 255.0
        val g = Color.green(c) / 255.0
        val b = Color.blue(c) / 255.0
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        return if (max == 0.0) 0.0 else (max - min) / max
    }
}
