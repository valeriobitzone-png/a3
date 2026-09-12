package a3.overlay

import android.view.WindowManager
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
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
    }
}
