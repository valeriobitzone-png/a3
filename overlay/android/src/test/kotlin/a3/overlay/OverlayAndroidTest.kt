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
        assertTrue(service.contains("OverlayBitmapBlur.blur"))
        val chrome = File("src/main/kotlin/a3/overlay/OverlayChrome.kt").readText()
        assertTrue(chrome.contains("overlay-blur-unavailable"))
        assertTrue(chrome.contains("overlay-pill-mark"))
        assertTrue(chrome.contains("OverlayLifecycle.pillText"))
        assertTrue(chrome.contains("OverlayPhase.COLLAPSED"))
        assertTrue(!chrome.contains("session.ambient"))
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
