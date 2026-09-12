package a3.overlay

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.WindowManager

object OverlayAndroidWindows {
    fun containerFlags(phase: OverlayPhase): Int {
        val layout = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return if (phase == OverlayPhase.COLLAPSED) {
            layout or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            layout or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
    }

    fun pillFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    fun backdropFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
}

object OverlayAndroidGate {
    const val WINDOW_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    fun state(context: Context, mediaProjectionGranted: Boolean): OverlayPermissionState {
        return OverlayPolicy.android(Settings.canDrawOverlays(context), mediaProjectionGranted)
    }

    fun overlayTypeName(): String {
        check(WINDOW_TYPE == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        check(Build.VERSION.SDK_INT >= 26)
        return OverlayContract.ANDROID_WINDOW_TYPE
    }
}
