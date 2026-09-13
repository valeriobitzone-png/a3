package a3.renderers.android.compose

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.GestureMap
import a3.a3ui.model.HapticMap
import a3.a3ui.model.MorphSpec
import a3.a3ui.model.MotionSpec
import a3.a3ui.model.Node
import a3.core.time.FixedClock
import a3.projection.model.CausalLineage
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RendererContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.Instant
import java.util.TreeMap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ShaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val assets = File("../../review-assets")
    private val reports = File("build/reports")

    private fun save(name: String, bitmap: android.graphics.Bitmap) {
        reports.mkdirs()
        assets.mkdirs()
        ShaderRaster.write(bitmap, File(reports, name))
        ShaderRaster.write(bitmap, File(assets, name))
        assertTrue(File(assets, name).length() > 0)
    }

    @Test
    fun SH_001_animated_gradient_flows() {
        assertTrue(File("src/main/resources/a3ui-graphics/shaders/animated-gradient.glsl").exists())
        assertTrue(File("src/main/resources/a3ui-graphics/shaders/animated-gradient.agsl").exists())
        assertTrue(File("src/main/resources/a3ui-graphics/shaders/animated-gradient.metal").exists())
        assertTrue(ShaderGradient.GLSL.contains("gl_FragCoord"))
        assertTrue(ShaderGradient.AGSL.contains("half4 main"))
        assertTrue(ShaderGradient.METAL.contains("fragment float4"))
        assertEquals(ShaderGradient.SPEED, ShaderGradient.speed())
        val a = ShaderRaster.gradientFrame(80, 48, 0f)
        val b = ShaderRaster.gradientFrame(80, 48, 2f)
        assertNotEquals(ShaderGradient.qualitative(8, 8, 0f), ShaderGradient.qualitative(8, 8, 2f))
        assertNotEquals(a.getPixel(20, 20), b.getPixel(20, 20))
        val dir = File(reports, "frames-gradient")
        dir.deleteRecursively()
        ShaderRaster.encodeFlow(dir, File(assets, "gradient-flow.mp4"))
        assertTrue(File(assets, "gradient-flow.mp4").length() > 0)
        composeRule.setContent {
            Box(Modifier.size(160.dp, 80.dp)) { AnimatedGradient(0f, reduced = true) }
        }
        composeRule.onNodeWithTag("animated-gradient").assertIsDisplayed()
    }

    @Test
    fun SH_002_gradient_fps_above_55() {
        val fps = ShaderRaster.fps(100, 80, 48)
        reports.mkdirs()
        File(reports, "gradient-fps.txt").writeText("fps=$fps frames=100 size=80x48")
        assertTrue(fps > 55.0, "fps $fps")
    }

    @Test
    fun SH_003_modal_backdrop_blur_and_scrim() {
        assertEquals(20f, GraphicsTokens.snapshot.acrylicBlurPx)
        assertTrue(ModalBackdrop.blurPx(1f) > 0f)
        assertTrue(ModalBackdrop.overlayAlpha(1f) > 0f)
        assertEquals(0f, ModalBackdrop.blurPx(0f))
        assertEquals(0f, ModalBackdrop.overlayAlpha(0f))
        val closed = ShaderRaster.modalFrame(0f)
        val opened = ShaderRaster.modalFrame(1f)
        assertTrue(GlassRaster.edgeEnergy(opened) < GlassRaster.edgeEnergy(closed))
        save("modal-backdrop-before.png", closed)
        save("modal-backdrop-after.png", opened)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(200.dp, 200.dp)) { ModalSurface(open = true) }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("modal-backdrop", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("modal-scrim", useUnmergedTree = true).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun SH_004_modal_transition_is_spring() {
        val compact = ModalBackdrop.springOpen(compactOvershoot = true)
        val comfortable = ModalBackdrop.springOpen(compactOvershoot = false)
        assertTrue(MotionPhysics.overshoot(compact, 0f, 1f))
        assertTrue(MotionPhysics.notLinear(comfortable, 0f, 1f, 240))
        val mid = comfortable[comfortable.size / 2].x
        assertTrue(kotlin.math.abs(ModalBackdrop.blurPx(mid) - ModalBackdrop.blurPx(0.5f)) > 0.01f || mid != 0.5f)
        val reduced = ModalBackdrop.springOpen(reduced = true)
        assertEquals(1, reduced.size)
        assertEquals(1f, reduced.first().x)
    }

    @Test
    fun SH_005_parallax_layers_move_at_different_speeds() {
        val rest = ParallaxLayers.Tilt(0f, 0f)
        val tilt = ParallaxLayers.Tilt(1f, 0.4f)
        val fg = ParallaxLayers.movement(ParallaxLayers.FG, rest, tilt, true)
        val mid = ParallaxLayers.movement(ParallaxLayers.MID, rest, tilt, true)
        val bg = ParallaxLayers.movement(ParallaxLayers.BG, rest, tilt, true)
        assertTrue(fg > mid && mid > bg, "fg $fg mid $mid bg $bg")
        assertEquals(0f, ParallaxLayers.offsetX(ParallaxLayers.FG, rest, true))
        val a = ShaderRaster.parallaxFrame(rest, true)
        val b = ShaderRaster.parallaxFrame(tilt, true)
        val black = android.graphics.Color.BLACK
        assertTrue(ShaderRaster.layerCenterX(b, black) != ShaderRaster.layerCenterX(a, black))
        val dir = File(reports, "frames-parallax")
        dir.deleteRecursively()
        ShaderRaster.encodeParallax(dir, File(assets, "parallax-tilt.gif"))
        assertTrue(File(assets, "parallax-tilt.gif").length() > 0)
        composeRule.setContent {
            ParallaxIcon(tilt, available = true)
        }
        composeRule.onNodeWithTag("parallax-icon").assertIsDisplayed()
        composeRule.onNodeWithTag("parallax-fg", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun SH_006_parallax_fallback_is_static() {
        val rest = ParallaxLayers.Tilt(0f, 0f)
        val tilt = ParallaxLayers.Tilt(1f, 1f)
        assertEquals(0f, ParallaxLayers.movement(ParallaxLayers.FG, rest, tilt, false))
        assertEquals(
            ParallaxLayers.offsetY(ParallaxLayers.FG, rest, false),
            ParallaxLayers.offsetY(ParallaxLayers.FG, tilt, false)
        )
        val still = ShaderRaster.parallaxFrame(tilt, false)
        val origin = ShaderRaster.parallaxFrame(rest, false)
        assertEquals(
            ShaderRaster.layerCenterX(still, android.graphics.Color.BLACK),
            ShaderRaster.layerCenterX(origin, android.graphics.Color.BLACK)
        )
        save("parallax-fallback.png", still)
        composeRule.setContent { ParallaxIcon(tilt, available = false) }
        composeRule.onNodeWithTag("parallax-fallback").assertIsDisplayed()
        composeRule.onNodeWithTag("parallax-bg", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun SH_007_cross_platform_qualitative_fingerprint() {
        val q = ShaderGradient.qualitative(8, 8, 1f)
        assertEquals(64, q.length)
        File(reports, "gradient-fingerprint.txt").writeText(q)
        val glsl = File("src/main/resources/a3ui-graphics/shaders/animated-gradient.glsl").readText()
        val metal = File("src/main/resources/a3ui-graphics/shaders/animated-gradient.metal").readText()
        assertTrue(glsl.contains("uv.x * 0.70"))
        assertTrue(metal.contains("uv.x * 0.70"))
        assertTrue(glsl.contains("mixAmt"))
        assertTrue(metal.contains("mixAmt"))
    }

    @Test
    fun SH_008_axis_glass_motion_color_and_shader_coexist() {
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/GlassSurface.kt").exists())
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/MotionPhysics.kt").exists())
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/DynamicPalette.kt").exists())
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/ShaderGradient.kt").exists())
        val catalog = File("src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt").readText()
        assertTrue(!catalog.contains("\"island\""))
        assertTrue(!catalog.contains("\"modal\""))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                AnimatedGradient(0f, Modifier.fillMaxSize(), reduced = true)
                ComposeRenderer(axisOut(EpistemicAxis(status = EpistemicStatus.HELD)))
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("n1-held").assertIsDisplayed()
        composeRule.onNodeWithTag("glass-surface", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("n1-glyph-held", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("animated-gradient").assertIsDisplayed()
        composeRule.mainClock.autoAdvance = true
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
            "overlay/",
            "showcase/",
            "docs/",
            "REVIEW_A3UI_PERF.md",
            "REVIEW_RENDERER_MOTION.md",
            "REVIEW_RENDERER_DYNAMIC.md",
            "REVIEW_RENDERER_SHADERS.md",
            "REVIEW_RENDERER_SENSORY.md",
            "REVIEW_RENDERER_BACKDROP.md",
            "review-assets/"
        )
        val ignore = listOf(".kotlin/", ".DS_Store")
        for (line in porcelain.lineSequence().filter { it.isNotBlank() }) {
            val path = line.drop(3).trim().removePrefix("?? ").let {
                if (it.contains(" -> ")) it.substringAfter(" -> ") else it
            }
            if (ignore.any { path.startsWith(it) }) continue
            assertTrue(allowed.any { path.startsWith(it) }, "unexpected path $line")
        }
    }

    @Test
    fun SH_010_ambient_indicator_morphs() {
        val chrome = File("src/main/kotlin/a3/renderers/android/compose/AmbientChrome.kt").readText()
        assertTrue(chrome.contains("SYSTEM_ALERT_WINDOW"))
        assertTrue(chrome.contains("picture-in-picture"))
        assertTrue(AmbientChrome.NOT_ISLAND.contains("not Apple Dynamic Island"))
        val a = AmbientChrome.morph(0f)
        val b = AmbientChrome.morph(1f)
        assertTrue(b.w > a.w && b.h > a.h)
        val spring = AmbientChrome.springExpand()
        assertTrue(MotionPhysics.notLinear(spring, 0f, 1f, 240))
        assertNotEquals(AmbientChrome.caption(AmbientChrome.Kind.TIMER), AmbientChrome.caption(AmbientChrome.Kind.AUDIO))
        assertNotEquals(AmbientChrome.caption(AmbientChrome.Kind.AUDIO), AmbientChrome.caption(AmbientChrome.Kind.CALL))
        val dir = File(reports, "frames-ambient")
        dir.deleteRecursively()
        ShaderRaster.encodeAmbient(dir, File(assets, "ambient-collapsed-expanded.gif"))
        assertTrue(File(assets, "ambient-collapsed-expanded.gif").length() > 0)
        composeRule.mainClock.autoAdvance = true
        var expanded by mutableStateOf(false)
        var kind by mutableStateOf(AmbientChrome.Kind.TIMER)
        composeRule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                Box(Modifier.size(280.dp, 100.dp)) {
                    AmbientIndicator(kind = kind, expanded = expanded, granted = true)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ambient-collapsed", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("ambient-copy", useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle {
            expanded = true
            kind = AmbientChrome.Kind.CALL
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ambient-expanded", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("ambient-copy", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun SH_011_ambient_fallback_is_honest() {
        assertEquals(AmbientChrome.Host.UNAVAILABLE, AmbientChrome.host(false, false))
        save("ambient-fallback.png", ShaderRaster.ambientFrame(1f, AmbientChrome.Kind.TIMER, granted = false))
        composeRule.setContent {
            AmbientIndicator(AmbientChrome.Kind.TIMER, expanded = true, granted = false)
        }
        composeRule.onNodeWithTag("ambient-unavailable").assertIsDisplayed()
        composeRule.onNodeWithText(AmbientChrome.UNAVAILABLE).assertIsDisplayed()
        composeRule.onNodeWithTag("ambient-indicator").assertDoesNotExist()
    }

    private fun axisOut(axis: EpistemicAxis) = A3UIInterpreter().interpret(
        A3UISurface(
            id = "s1",
            projectionRef = "p1",
            presentationRef = "ps1",
            lineage = CausalLineage("ctx", 1, "e1"),
            densityHint = "comfortable",
            colorTokens = listOf("accent"),
            motion = MotionSpec(280.0, 24.0, "standard", 240),
            morph = MorphSpec("ps1", "shared-element", emptyList(), MotionSpec(280.0, 24.0, "standard", 240)),
            gestures = GestureMap(emptyList()),
            haptics = HapticMap(emptyList()),
            nodes = listOf(Node("n1", "text", axis = axis)),
            bindings = listOf(Binding("train.price", "n1", "content")),
            producedAt = Instant.parse("2026-08-27T08:00:00Z")
        ),
        PresentationState(
            id = "ps1",
            sourceStateVersion = 1,
            producedAt = Instant.parse("2026-08-27T08:00:00Z"),
            atoms = listOf(PresentationAtom("price", "train.price", "12.40", 60)),
            lineage = CausalLineage("ctx", 1, "e1")
        ),
        RendererContext(
            formFactor = "phone",
            density = "comfortable",
            tokens = TreeMap<String, ColorValue>().apply { put("accent", ColorValue(0, 90, 200)) },
            clock = FixedClock(Instant.parse("2026-08-27T08:00:00Z")),
            reducedMotion = false
        )
    )
}
