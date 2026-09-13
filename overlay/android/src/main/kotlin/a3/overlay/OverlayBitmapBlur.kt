package a3.overlay

import android.graphics.Bitmap

/**
 * Capture copy only. Display blur is [OverlayBackdropGpu] (RenderEffect, API 31+).
 * MUST NOT box-blur bitmaps on CPU.
 */
object OverlayBitmapBlur {
    fun copy(src: Bitmap): Bitmap = src.copy(Bitmap.Config.ARGB_8888, false) ?: src
}
