package a3.overlay

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

object OverlayProfileAndroid {
    const val PREFS = "a3.perf"
    const val KEY_OVERRIDE = "override"

    fun store(context: Context): ProfileOverrideStore = PrefsStore(context)

    fun signals(context: Context): DeviceSignals {
        val ramMb = ramMb(context)
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
        val hardware = listOfNotNull(Build.HARDWARE, Build.FINGERPRINT, Build.PRODUCT).joinToString(" ")
        val platform = Build.BOARD ?: ""
        val gles = glesVersion(context)
        return DeviceSignals(
            name = Build.MODEL ?: "android",
            ramMb = ramMb,
            screenW = dm.widthPixels,
            screenH = dm.heightPixels,
            gpuTier = ProfileDetect.gpuTier(hardware, platform, gles),
            glesVersion = gles,
            hardware = hardware,
            platform = platform
        )
    }

    fun decide(context: Context): ProfileDecision {
        val signals = signals(context)
        val classed = ProfileSession.decide(signals, store(context))
        if (classed.source == ProfileSource.MANUAL) return classed
        val measured = OverlayMeasuredBudget.applyAuto(signals, classed.detected)
        if (measured == classed.detected) return classed
        return ProfileDecision(
            active = measured,
            detected = classed.detected,
            source = ProfileSource.AUTO,
            override = null,
            reason = classed.reason +
                " measured-downgrade=${measured.wire()}" +
                " catalog_p95_budget=${OverlayMeasuredBudget.CATALOG_P95_MS}" +
                " overlay_p95_budget=${OverlayMeasuredBudget.OVERLAY_P95_MS}" +
                " a024_gfxinfo_high_mid_p95=200"
        )
    }

    private fun ramMb(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.totalMem / (1024L * 1024L)).toInt()
    }

    private fun glesVersion(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.deviceConfigurationInfo?.reqGlEsVersion ?: 0x30000
    }

    private class PrefsStore(context: Context) : ProfileOverrideStore {
        private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        override fun read(): String? = prefs.getString(KEY_OVERRIDE, null)
        override fun write(raw: String?) {
            prefs.edit().putString(KEY_OVERRIDE, raw).apply()
        }
    }
}
