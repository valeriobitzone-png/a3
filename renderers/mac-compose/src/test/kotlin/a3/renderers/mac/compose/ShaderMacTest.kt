package a3.renderers.mac.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ShaderMacTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val assets = File("../../review-assets")
    private val reports = File("build/reports")

    private fun save(name: String, image: java.awt.image.BufferedImage) {
        reports.mkdirs()
        assets.mkdirs()
        ShaderRaster.write(image, File(reports, name))
        ShaderRaster.write(image, File(assets, name))
        assertTrue(File(assets, name).length() > 0)
    }

    @Test
    fun SH_001_animated_gradient_flows() {
        assertTrue(File("src/main/resources/a3ui-graphics/shaders/animated-gradient.metal").exists())
        assertTrue(ShaderGradient.METAL.contains("fragment float4"))
        assertTrue(ShaderGradient.GLSL.contains("gl_FragCoord"))
        val a = ShaderRaster.gradientFrame(80, 48, 0f)
        val b = ShaderRaster.gradientFrame(80, 48, 2f)
        assertNotEquals(ShaderGradient.qualitative(8, 8, 0f), ShaderGradient.qualitative(8, 8, 2f))
        assertNotEquals(a.getRGB(20, 20), b.getRGB(20, 20))
        composeRule.setContent {
            Box(Modifier.size(160.dp, 80.dp)) { AnimatedGradient(0f, reduced = true) }
        }
        composeRule.onNodeWithTag("animated-gradient").assertIsDisplayed()
    }

    @Test
    fun SH_002_gradient_fps_above_55() {
        val fps = ShaderRaster.fps(100, 80, 48)
        reports.mkdirs()
        File(reports, "gradient-fps-mac.txt").writeText("fps=$fps frames=100 size=80x48")
        assertTrue(fps > 55.0, "fps $fps")
    }

    @Test
    fun SH_003_modal_backdrop_blur_and_scrim() {
        assertTrue(ModalBackdrop.blurPx(1f) > 0f)
        assertTrue(ModalBackdrop.overlayAlpha(1f) > 0f)
        val closed = ShaderRaster.modalFrame(0f)
        val opened = ShaderRaster.modalFrame(1f)
        assertTrue(MacGlassRaster.edgeEnergy(opened) < MacGlassRaster.edgeEnergy(closed))
        save("modal-backdrop-after.png", opened)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(200.dp, 200.dp)) { ModalSurface(open = true) }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("modal-backdrop", useUnmergedTree = true).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun SH_004_modal_transition_is_spring() {
        val compact = ModalBackdrop.springOpen(compactOvershoot = true)
        assertTrue(MotionPhysics.overshoot(compact, 0f, 1f))
        assertTrue(MotionPhysics.notLinear(ModalBackdrop.springOpen(), 0f, 1f, 240))
        assertEquals(1, ModalBackdrop.springOpen(reduced = true).size)
    }

    @Test
    fun SH_005_parallax_layers_move_at_different_speeds() {
        val rest = ParallaxLayers.Tilt(0f, 0f)
        val tilt = ParallaxLayers.Tilt(1f, 0.4f)
        val fg = ParallaxLayers.movement(ParallaxLayers.FG, rest, tilt, true)
        val mid = ParallaxLayers.movement(ParallaxLayers.MID, rest, tilt, true)
        val bg = ParallaxLayers.movement(ParallaxLayers.BG, rest, tilt, true)
        assertTrue(fg > mid && mid > bg)
        composeRule.setContent { ParallaxIcon(tilt, available = true) }
        composeRule.onNodeWithTag("parallax-icon").assertIsDisplayed()
    }

    @Test
    fun SH_006_parallax_fallback_is_static() {
        val rest = ParallaxLayers.Tilt(0f, 0f)
        val tilt = ParallaxLayers.Tilt(1f, 1f)
        assertEquals(0f, ParallaxLayers.movement(ParallaxLayers.FG, rest, tilt, false))
        save("parallax-fallback.png", ShaderRaster.parallaxFrame(tilt, false))
        composeRule.setContent { ParallaxIcon(tilt, available = false) }
        composeRule.onNodeWithTag("parallax-fallback").assertIsDisplayed()
    }

    @Test
    fun SH_007_cross_platform_qualitative_fingerprint() {
        val q = ShaderGradient.qualitative(8, 8, 1f)
        assertEquals(64, q.length)
        File(reports, "gradient-fingerprint-mac.txt").writeText(q)
        val android = File("../android-compose/build/reports/gradient-fingerprint.txt")
        if (android.exists()) {
            assertEquals(android.readText().trim(), q)
        }
        assertTrue(ShaderGradient.METAL.contains("uv.x * 0.70"))
        assertTrue(ShaderGradient.GLSL.contains("uv.x * 0.70"))
    }

    @Test
    fun SH_008_axis_glass_motion_color_and_shader_coexist() {
        assertTrue(File("src/main/kotlin/a3/renderers/mac/compose/MacGlassSurface.kt").exists())
        assertTrue(File("src/main/kotlin/a3/renderers/mac/compose/ShaderGradient.kt").exists())
        val catalog = File("src/main/kotlin/a3/renderers/mac/compose/MacCatalog.kt").readText()
        assertTrue(!catalog.contains("\"island\""))
        System.setProperty("a3.reduce.motion", "true")
        val (_, calendar) = MacFixtures.composeCalendar()
        composeRule.mainClock.autoAdvance = true
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) { MacRenderer(calendar) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("a3-material").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.hotel-held").assertIsDisplayed()
        composeRule.onAllNodesWithTag("glass-surface", useUnmergedTree = true)[0].assertIsDisplayed()
    }

    @Test
    fun SH_009_freeze_only_compose_renderers() {
        val root = File("../..")
        val proc = ProcessBuilder(
            "git", "diff", "--stat", "--",
            "core/", "core/json", "core/admission", "core/action",
            "prediction/", "projection/", "a3ui/", "renderers/android-core/",
            "adapters/", "intent-model/", "broker/", "launcher/"
        ).directory(root).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor())
        assertTrue(out.isBlank(), out)
        val status = ProcessBuilder("git", "status", "--porcelain")
            .directory(root).redirectErrorStream(true).start()
        val porcelain = status.inputStream.bufferedReader().readText()
        assertEquals(0, status.waitFor())
        val allowed = listOf(
            "renderers/android-compose/",
            "renderers/mac-compose/",
            "REVIEW_RENDERER_MOTION.md",
            "REVIEW_RENDERER_DYNAMIC.md",
            "REVIEW_RENDERER_SHADERS.md",
            "review-assets/"
        )
        val ignore = listOf(".kotlin/", ".DS_Store")
        for (line in porcelain.lineSequence().filter { it.isNotBlank() }) {
            val path = line.drop(3).trim().let { if (it.contains(" -> ")) it.substringAfter(" -> ") else it }
            if (ignore.any { path.startsWith(it) }) continue
            assertTrue(allowed.any { path.startsWith(it) }, "unexpected path $line")
        }
    }

    @Test
    fun SH_010_ambient_indicator_morphs() {
        val chrome = File("src/main/kotlin/a3/renderers/mac/compose/AmbientChrome.kt").readText()
        assertTrue(chrome.contains("NSWindow"))
        assertTrue(chrome.contains("floating"))
        assertEquals(AmbientChrome.Host.WINDOW, AmbientChrome.host(true, mac = true))
        assertTrue(AmbientChrome.morph(1f).w > AmbientChrome.morph(0f).w)
        composeRule.mainClock.autoAdvance = false
        var expanded by mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                Box(Modifier.size(280.dp, 100.dp)) {
                    AmbientIndicator(AmbientChrome.Kind.AUDIO, expanded, granted = true, mac = true)
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("ambient-collapsed", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("ambient-host-window", useUnmergedTree = true).assertIsDisplayed()
        expanded = true
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("ambient-expanded", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("ambient-copy", useUnmergedTree = true).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun SH_011_ambient_fallback_is_honest() {
        save("ambient-fallback.png", ShaderRaster.ambientFrame(1f, AmbientChrome.Kind.TIMER, granted = false))
        composeRule.setContent {
            AmbientIndicator(AmbientChrome.Kind.TIMER, expanded = true, granted = false)
        }
        composeRule.onNodeWithTag("ambient-unavailable").assertIsDisplayed()
        composeRule.onNodeWithText(AmbientChrome.UNAVAILABLE).assertIsDisplayed()
    }
}
