package a3.overlay

/**
 * Measured frame-budget layer on top of class-signal [ProfileDetect].
 * Decision (b) in docs/PERFORMANCE.md: catalog 60 fps, overlay expanded 30 fps.
 * Never silent — caller still shows [ProfileDecision.pillText].
 */
object OverlayMeasuredBudget {
    const val CATALOG_P95_MS = 16.7
    const val OVERLAY_P95_MS = 33.3

    fun isA024(signals: DeviceSignals): Boolean {
        val n = signals.name.lowercase()
        return n.contains("a024") ||
            n.contains("phone (3)") ||
            n.contains("phone 3")
    }

    /**
     * Best auto profile that can be selected from measured HIGH/MID dumps on A024.
     * gfxinfo 2026-09-13: HIGH and MID catalog+overlay named p95 = 200 ms (GPU p95 6–8 ms).
     * Neither meets catalog 16.7 ms nor overlay 33.3 ms. BLUR_OFF was not dumped;
     * it is the remaining cheaper rung on the declared ladder, never a claim that it
     * meets 16.7/33.3.
     */
    fun applyAuto(signals: DeviceSignals, classDetected: A3UiProfile): A3UiProfile {
        if (!isA024(signals)) return classDetected
        return A3UiProfile.BLUR_OFF
    }
}
