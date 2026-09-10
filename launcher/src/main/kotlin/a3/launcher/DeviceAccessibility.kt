package a3.launcher

import android.content.Context
import android.provider.Settings

object DeviceAccessibility {
    fun reducedMotion(context: Context): Boolean {
        val resolver = context.contentResolver
        fun scale(key: String): Float = try {
            Settings.Global.getFloat(resolver, key)
        } catch (_: Settings.SettingNotFoundException) {
            1f
        }
        return scale(Settings.Global.ANIMATOR_DURATION_SCALE) == 0f ||
            scale(Settings.Global.TRANSITION_ANIMATION_SCALE) == 0f ||
            scale(Settings.Global.WINDOW_ANIMATION_SCALE) == 0f
    }

    fun highContrast(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled") == 1
        } catch (_: Settings.SettingNotFoundException) {
            false
        }
    }
}
