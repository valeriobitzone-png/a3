// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File

internal object DynamicRaster {
    fun paletteStrip(tonal: DynamicPalette.Tonal): Bitmap {
        val w = 240
        val h = 48
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val roles = tonal.roles()
        val slot = w / roles.size
        val paint = Paint()
        roles.forEachIndexed { i, rgb ->
            paint.color = rgb.pack()
            canvas.drawRect((i * slot).toFloat(), 0f, ((i + 1) * slot).toFloat(), h.toFloat(), paint)
        }
        return bitmap
    }

    fun contrastSample(backdrop: IntArray, width: Int, height: Int, text: DynamicPalette.Rgb): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var i = 0
        while (i < backdrop.size) {
            bitmap.setPixel(i % width, i / width, backdrop[i])
            i++
        }
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = text.pack()
            textSize = 18f
            isFakeBoldText = true
        }
        canvas.drawText("Ag", 8f, height - 10f, paint)
        return bitmap
    }

    fun typeSample(sizeSp: Float, weight: Int, width: Float, trackingEm: Float, label: String): Bitmap {
        val w = 200
        val h = 64
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = sizeSp
            strokeWidth = ((weight - 400).coerceAtLeast(0) / 50f)
            isFakeBoldText = weight >= 450
            letterSpacing = trackingEm
            textScaleX = width
        }
        canvas.drawText(label, 8f, 40f, paint)
        return bitmap
    }

    fun glyphFrame(points: List<GlyphGeometry.Pt>, size: Int = 64): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        if (points.size >= 2) {
            var i = 1
            while (i < points.size) {
                val a = points[i - 1]
                val b = points[i]
                canvas.drawLine(a.x * size, a.y * size, b.x * size, b.y * size, paint)
                i++
            }
        }
        return bitmap
    }

    fun morphStrip(from: GlyphGeometry.Kind, to: GlyphGeometry.Kind): Bitmap {
        val cell = 64
        val frames = 5
        val bitmap = Bitmap.createBitmap(cell * frames, cell, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        var i = 0
        while (i < frames) {
            val t = i / (frames - 1f)
            val frame = glyphFrame(GlyphGeometry.morph(from, to, t), cell)
            canvas.drawBitmap(frame, (i * cell).toFloat(), 0f, null)
            i++
        }
        return bitmap
    }

    fun inkCount(bitmap: Bitmap): Int {
        var n = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val c = bitmap.getPixel(x, y)
                if (Color.red(c) + Color.green(c) + Color.blue(c) < 80) n++
                x++
            }
            y++
        }
        return n
    }

    fun meanGap(bitmap: Bitmap): Float {
        val bottoms = ArrayList<Int>()
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            var found = false
            while (y < bitmap.height) {
                val c = bitmap.getPixel(x, y)
                if (Color.red(c) + Color.green(c) + Color.blue(c) < 80) {
                    bottoms += x
                    found = true
                    break
                }
                y++
            }
            x++
        }
        if (bottoms.size < 4) return 0f
        val runs = ArrayList<Int>()
        var i = 1
        var gap = 0
        while (i < bottoms.size) {
            val d = bottoms[i] - bottoms[i - 1]
            if (d > 1) {
                runs += d
                gap++
            }
            i++
        }
        if (runs.isEmpty()) return 0f
        return runs.average().toFloat()
    }

    fun contentRight(bitmap: Bitmap): Int {
        var x = bitmap.width - 1
        while (x >= 0) {
            var y = 0
            while (y < bitmap.height) {
                val c = bitmap.getPixel(x, y)
                if (Color.red(c) + Color.green(c) + Color.blue(c) < 80) return x
                y++
            }
            x--
        }
        return 0
    }

    fun write(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    fun fingerprint(bitmap: Bitmap): Long {
        var h = 1125899906842597L
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                h = 31 * h + bitmap.getPixel(x, y)
                x++
            }
            y++
        }
        return h
    }

    fun packWallpaper(pixels: IntArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var i = 0
        while (i < pixels.size) {
            bitmap.setPixel(i % width, i / width, pixels[i])
            i++
        }
        return bitmap
    }
}
