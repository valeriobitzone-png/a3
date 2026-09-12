package a3.overlay.mac

import a3.overlay.BackdropMode
import a3.overlay.OverlayActionMark
import a3.overlay.OverlayCompositor
import a3.overlay.OverlayContract
import a3.overlay.OverlayFlight
import a3.overlay.OverlayLifecycle
import a3.overlay.OverlayPhase
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
        assertTrue(swift.contains("layoutCollapsed"))
        assertTrue(swift.contains("ignoresMouseEvents"))
        assertTrue(swift.contains("mouse events outside the pill"))
        assertTrue(swift.contains("didActivateApplicationNotification"))
        assertTrue(swift.contains("timeout"))
        assertTrue(swift.contains("collapse(reason: \"dispatch\")") || swift.contains("dispatch"))
    }

    @Test
    fun OL_mac_default_collapsed_and_compositor_hides_cards() {
        val swift = nativeSrc.readText()
        assertTrue(swift.contains("phase: OverlayPhase = .collapsed") || swift.contains("var phase: OverlayPhase = .collapsed"))
        val session = OverlayFlight.present()
        val screen = java.awt.image.BufferedImage(640, 400, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = screen.createGraphics()
        g.color = java.awt.Color.RED
        g.fillRect(0, 0, 640, 400)
        g.dispose()
        val collapsed = OverlayCompositor.compose(
            screen,
            session,
            BackdropMode.REAL_BLUR,
            OverlayPhase.COLLAPSED,
            OverlayActionMark.UNKNOWN
        )
        val expanded = OverlayCompositor.compose(
            screen,
            session,
            BackdropMode.REAL_BLUR,
            OverlayPhase.EXPANDED,
            OverlayActionMark.PENDING
        )
        assertTrue(OverlayCompositor.variance(collapsed, screen) < OverlayCompositor.variance(expanded, screen))
        assertEquals("?", OverlayLifecycle.pillText(OverlayActionMark.UNKNOWN))
        assertTrue(!OverlayLifecycle.isLongPillText(OverlayLifecycle.pillText(OverlayActionMark.DONE)))
    }
}
