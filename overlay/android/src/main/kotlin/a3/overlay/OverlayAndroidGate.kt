package a3.overlay

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.WindowManager

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
