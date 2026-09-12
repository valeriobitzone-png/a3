package a3.overlay.mac

import a3.overlay.BackdropMode
import a3.overlay.OverlayContract
import a3.overlay.OverlayFlight
import a3.overlay.OverlayPolicy
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverlayMacTest {
    private val nativeSrc = File("native/OverlayMain.swift")

    @Test
    fun OV_004_floating_window_no_special_permission() {
        assertEquals("floating", OverlayMacWindow.LEVEL)
        assertEquals(OverlayContract.MAC_LEVEL, OverlayMacWindow.LEVEL)
        assertEquals(OverlayPolicy.MAC_NO_SPECIAL_PERMISSION, OverlayMacWindow.permissionMessage())
        assertTrue(OverlayMacWindow.accessibilityOptional().contains("optional"))
        val swift = nativeSrc.readText()
        assertTrue(swift.contains(".floating"))
        assertTrue(swift.contains("AXIsProcessTrusted"))
        assertTrue(swift.contains("cmd") || swift.contains("\"w\""))
    }

    @Test
    fun OV_005_visual_effect_is_real_backdrop() {
        val swift = nativeSrc.readText()
        assertTrue(swift.contains("NSVisualEffectView"))
        assertTrue(swift.contains(".fullScreenUI"))
        assertTrue(swift.contains(".behindWindow"))
        assertEquals("fullScreenUI", OverlayMacWindow.MATERIAL)
        assertEquals("behindWindow", OverlayMacWindow.BLENDING)
        assertTrue(!swift.contains("CIFilter") || swift.contains("NSVisualEffectView"))
        val session = OverlayFlight.present()
        assertTrue(session.cards.isNotEmpty())
    }

    @Test
    fun OV_006_native_binary_compiles() {
        val bin = File(System.getProperty("a3.overlay.native") ?: "build/overlay-mac")
        assertTrue(bin.exists() && bin.length() > 0, bin.absolutePath)
        val swift = nativeSrc.readText()
        assertTrue(swift.contains("Safari") || swift.contains("frontmost"))
        assertTrue(swift.contains("NSWorkspace.shared.open") || swift.contains("NSWorkspace.shared.open("))
        assertTrue(swift.contains("AXObserver"))
        assertTrue(swift.contains("swipe") || swift.contains("translation"))
        assertTrue(swift.contains("cacheDisplay"))
    }
}
