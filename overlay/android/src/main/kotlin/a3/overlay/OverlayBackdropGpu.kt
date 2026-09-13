package a3.overlay

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.ImageView

/** System/GPU blur for the overlay capture backdrop. MUST NOT box-blur on CPU. */
object OverlayBackdropGpu {
    fun apply(view: ImageView, radiusPx: Int) {
        if (Build.VERSION.SDK_INT < 31) return
        if (radiusPx <= 0) {
            view.setRenderEffect(null)
            return
        }
        val r = radiusPx.toFloat()
        view.setRenderEffect(
            RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
        )
    }
}
