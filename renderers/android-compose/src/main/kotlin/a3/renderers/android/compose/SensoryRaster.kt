package a3.renderers.android.compose

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

internal object SensoryRaster {
    const val BADGE_X = 16
    const val BADGE_Y = 16
    const val BADGE_SIZE = 20

    fun applyRefraction(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val sx = (x + GlassOptics.offsetX(x, y)).roundToInt().coerceIn(0, w - 1)
                val sy = (y + GlassOptics.offsetY(x, y)).roundToInt().coerceIn(0, h - 1)
                out.setPixel(x, y, src.getPixel(sx, sy))
                x++
            }
            y++
        }
        return out
    }

    fun applyNoise(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val p = src.getPixel(x, y)
                val g = (GlassOptics.noiseAt(x, y) * 255f).roundToInt()
                val r = (Color.red(p) + g).coerceIn(0, 255)
                val green = (Color.green(p) + g).coerceIn(0, 255)
                val b = (Color.blue(p) + g).coerceIn(0, 255)
                out.setPixel(x, y, Color.argb(Color.alpha(p), r, green, b))
                x++
            }
            y++
        }
        return out
    }

    fun stampBadge(src: Bitmap, x0: Int = BADGE_X, y0: Int = BADGE_Y, size: Int = BADGE_SIZE): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val ink = Color.BLACK
        var y = y0
        while (y < y0 + size && y < out.height) {
            var x = x0
            while (x < x0 + size && x < out.width) {
                out.setPixel(x, y, ink)
                x++
            }
            y++
        }
        return out
    }

    fun glass(refract: Boolean, noise: Boolean, badge: Boolean, width: Int = 160, height: Int = 80): Bitmap {
        val painted = GlassRaster.paint(GlassRaster.checker(width, height), blur = true)
        var out = painted
        if (refract) out = applyRefraction(out)
        if (noise) out = applyNoise(out)
        if (badge) out = stampBadge(out)
        return out
    }

    fun particleFrame(trigger: Boolean, t: Float, width: Int = 80, height: Int = 48): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(16, 16, 20))
        val sparks = ParticleField.frame(trigger, t)
        for (spark in sparks) {
            val x = (spark.x * (width - 1)).roundToInt().coerceIn(0, width - 1)
            val y = (spark.y * (height - 1)).roundToInt().coerceIn(0, height - 1)
            val a = (spark.life * 255f).roundToInt().coerceIn(0, 255)
            if (a > 0) bitmap.setPixel(x, y, Color.argb(a, 255, 255, 240))
        }
        return bitmap
    }

    fun particleStrip(): Bitmap {
        val fw = 80
        val fh = 48
        val times = floatArrayOf(0f, 0.25f, 0.5f, 0.85f)
        val strip = Bitmap.createBitmap(fw * times.size, fh, Bitmap.Config.ARGB_8888)
        var i = 0
        while (i < times.size) {
            val frame = particleFrame(true, times[i], fw, fh)
            var y = 0
            while (y < fh) {
                var x = 0
                while (x < fw) {
                    strip.setPixel(i * fw + x, y, frame.getPixel(x, y))
                    x++
                }
                y++
            }
            i++
        }
        return strip
    }

    fun maskingFrame(width: Int = 160, height: Int = 48): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        var y = 12
        while (y < 36) {
            var x = 0
            while (x < width) {
                val a = (AlphaMask.edge(x + 0.5f, width.toFloat()) * 255f).roundToInt().coerceIn(0, 255)
                bitmap.setPixel(x, y, Color.argb(a, 0, 0, 0))
                x++
            }
            y++
        }
        return bitmap
    }

    fun differCount(a: Bitmap, b: Bitmap): Int {
        var n = 0
        var y = 0
        while (y < a.height) {
            var x = 0
            while (x < a.width) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) n++
                x++
            }
            y++
        }
        return n
    }

    fun meanAbs(a: Bitmap, b: Bitmap): Double {
        var acc = 0.0
        var n = 0
        var y = 0
        while (y < a.height) {
            var x = 0
            while (x < a.width) {
                val pa = a.getPixel(x, y)
                val pb = b.getPixel(x, y)
                acc += abs(Color.red(pa) - Color.red(pb))
                acc += abs(Color.green(pa) - Color.green(pb))
                acc += abs(Color.blue(pa) - Color.blue(pb))
                n += 3
                x++
            }
            y++
        }
        return if (n == 0) 0.0 else acc / n
    }

    fun badgeIntact(a: Bitmap, b: Bitmap): Boolean {
        var y = BADGE_Y
        while (y < BADGE_Y + BADGE_SIZE) {
            var x = BADGE_X
            while (x < BADGE_X + BADGE_SIZE) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) return false
                if (Color.alpha(a.getPixel(x, y)) != 255) return false
                x++
            }
            y++
        }
        return true
    }

    fun brightCount(bitmap: Bitmap): Int {
        var n = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val p = bitmap.getPixel(x, y)
                if (Color.red(p) > 180 && Color.alpha(p) > 40) n++
                x++
            }
            y++
        }
        return n
    }
}
