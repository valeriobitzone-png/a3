package a3.overlay

import android.view.WindowManager
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayAndroidTest {
    @Test
    fun OV_001_system_alert_window_required() {
        val denied = OverlayPolicy.android(false, false)
        assertEquals(OverlayAvailability.DISABLED_NO_OVERLAY_PERMISSION, denied.availability)
        assertTrue(denied.message.contains("SYSTEM_ALERT_WINDOW"))
        assertEquals(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            OverlayAndroidGate.WINDOW_TYPE
        )
        assertEquals("TYPE_APPLICATION_OVERLAY", OverlayAndroidGate.overlayTypeName())
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("SYSTEM_ALERT_WINDOW"))
        assertTrue(manifest.contains("OverlayService"))
        val service = File("src/main/kotlin/a3/overlay/OverlayService.kt").readText()
        assertTrue(service.contains("TYPE_APPLICATION_OVERLAY"))
        assertTrue(service.contains("setViewTreeLifecycleOwner"))
        assertTrue(!service.contains("TYPE_PHONE"))
        assertTrue(service.contains("FLAG_NOT_TOUCHABLE"))
        assertTrue(service.contains("ACTION_OUTSIDE"))
        assertTrue(service.contains("OverlayLifecycle.start"))
        val windows = File("src/main/kotlin/a3/overlay/OverlayAndroidGate.kt").readText()
        assertTrue(windows.contains("FLAG_WATCH_OUTSIDE_TOUCH"))
        assertTrue(windows.contains("FLAG_NOT_TOUCHABLE"))
        val collapsed = OverlayAndroidWindows.containerFlags(OverlayPhase.COLLAPSED)
        assertTrue((collapsed and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0)
        assertFalse(OverlayWindowLaw.containerTouchable(OverlayPhase.COLLAPSED))
        val expanded = OverlayAndroidWindows.containerFlags(OverlayPhase.EXPANDED)
        assertTrue((expanded and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH) != 0)
        assertTrue((expanded and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0)
    }

    @Test
    fun OV_002_media_projection_denied_is_honest() {
        val denied = OverlayPolicy.android(true, false)
        assertEquals(BackdropMode.UNAVAILABLE, denied.backdrop)
        assertEquals(OverlayPolicy.BLUR_UNAVAILABLE, denied.message)
        val granted = OverlayPolicy.android(true, true)
        assertEquals(BackdropMode.REAL_BLUR, granted.backdrop)
        val service = File("src/main/kotlin/a3/overlay/OverlayService.kt").readText()
        assertTrue(service.contains("MediaProjection"))
        assertTrue(service.contains("registerCallback"))
        assertTrue(service.contains("OverlayBackdropGpu.apply"))
        assertTrue(service.contains("OverlayBitmapBlur.copy"))
        val gpu = File("src/main/kotlin/a3/overlay/OverlayBackdropGpu.kt").readText()
        assertTrue(gpu.contains("RenderEffect.createBlurEffect"))
        assertTrue(!gpu.contains("OverlayBlur.blur"))
        val chrome = File("src/main/kotlin/a3/overlay/OverlayChrome.kt").readText()
        assertTrue(chrome.contains("overlay-blur-unavailable"))
        assertTrue(chrome.contains("profile-pill"))
        val settings = File("src/main/kotlin/a3/overlay/OverlayPermissionActivity.kt").readText()
        assertTrue(settings.contains("Profile AUTO"))
        assertTrue(settings.contains("pillText"))
        assertTrue(chrome.contains("overlay-blur-unavailable"))
        assertTrue(chrome.contains("overlay-pill-mark"))
        assertTrue(chrome.contains("profile-pill"))
        assertTrue(chrome.contains("OverlayLifecycle.pillText"))
        assertTrue(chrome.contains("OverlayPhase.COLLAPSED"))
        assertTrue(!chrome.contains("session.ambient"))
    }

    @Test
    fun PF_002_android_gfxinfo_dumps_ingested() {
        val dir = File("../../review-assets/perf")
        val highCatalog = File(dir, "android-catalog-HIGH-gfxinfo.txt")
        val highOverlay = File(dir, "android-overlay-HIGH-gfxinfo.txt")
        assertTrue(highCatalog.isFile, highCatalog.path)
        assertTrue(highOverlay.isFile, highOverlay.path)
        val catalog = GfxInfoParser.parse(highCatalog.readText())
        val overlay = GfxInfoParser.parse(highOverlay.readText())
        assertTrue(catalog != null && catalog.count > 0, "catalog gfxinfo")
        assertTrue(overlay != null && overlay.count > 0, "overlay gfxinfo")
        println(
            "PASS PF-002 A024 catalog p50=${catalog!!.p50Ms} p95=${catalog.p95Ms} " +
                "overlay p50=${overlay!!.p50Ms} p95=${overlay.p95Ms} frames=${catalog.count}/${overlay.count}"
        )
    }

    @Test
    fun PF_003_mid_p95_not_worse_than_high() {
        val dir = File("../../review-assets/perf")
        val highCatalog = GfxInfoParser.parse(File(dir, "android-catalog-HIGH-gfxinfo.txt").readText())!!
        val midCatalog = GfxInfoParser.parse(File(dir, "android-catalog-MID-gfxinfo.txt").readText())!!
        val highOverlay = GfxInfoParser.parse(File(dir, "android-overlay-HIGH-gfxinfo.txt").readText())!!
        val midOverlay = GfxInfoParser.parse(File(dir, "android-overlay-MID-gfxinfo.txt").readText())!!
        assertTrue(
            midCatalog.p95Ms <= highCatalog.p95Ms + 0.01,
            "MID catalog p95=${midCatalog.p95Ms} HIGH=${highCatalog.p95Ms}"
        )
        assertTrue(
            midOverlay.p95Ms <= highOverlay.p95Ms + 0.01,
            "MID overlay p95=${midOverlay.p95Ms} HIGH=${highOverlay.p95Ms}"
        )
        println("PASS PF-003 MID≤HIGH catalog ${midCatalog.p95Ms}/${highCatalog.p95Ms} overlay ${midOverlay.p95Ms}/${highOverlay.p95Ms}")
    }

    @Test
    fun PF_006_measured_budget_a024_defaults_blur_off() {
        val phone = ProfileDetect.nothingPhone3().copy(name = "A024")
        assertEquals(A3UiProfile.HIGH, ProfileDetect.detect(phone))
        assertEquals(A3UiProfile.BLUR_OFF, OverlayMeasuredBudget.applyAuto(phone, A3UiProfile.HIGH))
        val other = phone.copy(name = "Pixel 8")
        assertEquals(A3UiProfile.HIGH, OverlayMeasuredBudget.applyAuto(other, A3UiProfile.HIGH))
        println("PASS PF-006 measured A024 auto BLUR_OFF")
    }

    @Test
    fun OL_collapsed_container_is_not_touchable() {
        assertFalse(OverlayWindowLaw.containerTouchable(OverlayPhase.COLLAPSED))
        assertTrue(OverlayWindowLaw.pillTouchable())
        val service = File("src/main/kotlin/a3/overlay/OverlayService.kt").readText()
        assertTrue(service.contains("attachPill"))
        assertTrue(service.contains("OverlayLifecycle.dispatch"))
        assertTrue(service.contains("EXTRA_EXPAND"))
        assertTrue(service.contains("transitionMs"))
        assertTrue(service.contains("DEFAULT_TIMEOUT_MS"))
    }
}
