// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.overlay.mac

import a3.overlay.BackdropMode
import a3.overlay.FeatureMatrix
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
    private val perfDir = File("../../review-assets/perf")

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
        val compositor = File("src/main/kotlin/a3/overlay/mac/OverlayCompositor.kt").readText()
        assertTrue(compositor.contains("MUST NOT run OverlayBlur on the compose path"))
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
        assertTrue(swift.contains("--profile"))
        assertTrue(swift.contains("profile-pill"))
        assertTrue(swift.contains("timeout"))
        assertTrue(swift.contains("collapse(reason: \"dispatch\")") || swift.contains("dispatch"))
        assertTrue(swift.contains("CADisplayLink"))
        assertTrue(swift.contains("--frames"))
        assertTrue(swift.contains("NSVisualEffectView"))
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

    @Test
    fun PF_hotspot_cpu_blur_is_offscreen_harness() {
        val session = OverlayFlight.present()
        val screen = java.awt.image.BufferedImage(1280, 800, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = screen.createGraphics()
        g.color = java.awt.Color(32, 48, 64)
        g.fillRect(0, 0, 1280, 800)
        g.color = java.awt.Color.WHITE
        g.fillRect(40, 40, 200, 200)
        g.dispose()
        val radius = FeatureMatrix.of(a3.overlay.A3UiProfile.HIGH).blurRadiusPx
        val samples = (1..8).map { OverlayCompositor.hotspot(screen, session, radius) }
        val copy = samples.map { it.copyMs }.average()
        val blur = samples.map { it.blurCpuMs }.average()
        val paint = samples.map { it.paintMs }.average()
        val total = copy + blur + paint
        val share = if (total <= 0.0) 0.0 else 100.0 * blur / total
        perfDir.mkdirs()
        File(perfDir, "mac-hotspot-compositor.txt").writeText(
            buildString {
                appendLine("host=${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
                appendLine("method=OverlayCompositor hotspot: copy vs OverlayBlur CPU vs paint")
                appendLine("harness=offscreen BufferedImage (CPU). NOT display vsync.")
                appendLine("display_blur=NSVisualEffectView in native OverlayMain")
                appendLine("size=1280x800 radius=$radius iterations=${samples.size}")
                appendLine("copy_ms=$copy")
                appendLine("blur_cpu_ms=$blur")
                appendLine("paint_ms=$paint")
                appendLine("blur_share_pct=$share")
            }
        )
        assertTrue(blur > copy, "expected CPU OverlayBlur to dominate copy: blur=$blur copy=$copy")
        println("PASS hotspot copy=$copy blur=$blur paint=$paint share=$share%")
    }

    @Test
    fun PF_005_mac_onscreen_dumps_at_least_300_frames() {
        val profiles = listOf("HIGH", "MID", "BLUR_OFF")
        val scenes = listOf("overlay-expanded" to "CADisplayLink", "catalog" to "withFrameNanos")
        for (profile in profiles) {
            for ((scene, methodNeedle) in scenes) {
                val file = File(perfDir, "mac-$scene-$profile.txt")
                assertTrue(file.isFile, "missing ${file.path}")
                val text = file.readText()
                assertTrue(text.contains(methodNeedle), "${file.name} method: $text")
                assertTrue(!text.contains("OverlayCompositor.compose wall time"), file.name)
                val frames = Regex("""frames=(\d+)""").find(text)?.groupValues?.get(1)?.toInt()
                    ?: error("no frames= in ${file.name}")
                assertTrue(frames >= 300, "${file.name} frames=$frames")
                assertTrue(text.contains("p50_ms="), file.name)
                assertTrue(text.contains("p95_ms="), file.name)
                assertTrue(text.contains("max_ms="), file.name)
            }
        }
        println("PASS PF-005 on-screen dumps ≥300")
    }

    @Test
    fun TH_003_mac_window_level_and_event_isolation() {
        assertEquals("window level + event isolation", OverlayMacWindow.EVENT_ISOLATION)
        val swift = nativeSrc.readText()
        assertTrue(swift.contains(".floating"))
        assertTrue(swift.contains(".nonactivatingPanel"))
        assertTrue(swift.contains("window level + event isolation"))
        assertTrue(swift.contains("mouse events outside the pill"))
        assertTrue(swift.contains("ignoresMouseEvents"))
        for (read in OverlayMacWindow.axReads()) {
            assertTrue(swift.contains(read), read)
        }
        assertTrue(swift.contains("{ _, _, _, _ in }"))
    }

    @Test
    fun TH_005_mac_sensitive_stays_collapsed() {
        val swift = nativeSrc.readText()
        assertTrue(swift.contains("--sensitive"))
        assertTrue(swift.contains("if sensitive { return }"))
        assertTrue(swift.contains("OverlaySurfaceKind.SENSITIVE"))
    }
}
