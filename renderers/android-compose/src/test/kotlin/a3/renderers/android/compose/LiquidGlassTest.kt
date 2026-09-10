package a3.renderers.android.compose

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.EpistemicSupport
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
import android.graphics.Color
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LiquidGlassTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val assets = File("../../review-assets")
    private val reports = File("build/reports")
    private val tokens = GraphicsTokens.snapshot
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private fun save(name: String, bitmap: android.graphics.Bitmap) {
        reports.mkdirs()
        assets.mkdirs()
        GlassRaster.write(bitmap, File(reports, name))
        GlassRaster.write(bitmap, File(assets, name))
        assertTrue(File(assets, name).length() > 0)
    }

    @Test
    fun LG_001_blur_visible_and_render_effect_on_s() {
        assertEquals("0.1", tokens.version)
        assertEquals(12f, tokens.blurRadiusPx)
        assertNotNull(AndroidGlassEffect.create(tokens))
        val effectSrc = File("src/main/kotlin/a3/renderers/android/compose/AndroidGlassEffect.kt").readText()
        assertTrue(effectSrc.contains("RenderEffect.createBlurEffect"))
        val raw = GlassRaster.checker(200, 140)
        val sharp = GlassRaster.paint(raw, blur = false, vibrancy = false, highlights = false, shadows = false)
        val blurred = GlassRaster.paint(raw, blur = true, vibrancy = false, highlights = false, shadows = false)
        val ratio = GlassRaster.edgeEnergy(blurred) / GlassRaster.edgeEnergy(sharp)
        assertTrue(ratio < GlassRaster.BLUR_ENERGY_RATIO_MAX, "blur ratio $ratio")
        save("android-glass.png", GlassRaster.paint(raw, blur = true, overlayInk = axisOverlay()))
    }

    @Test
    @Config(sdk = [30])
    fun LG_001_api_below_31_fallback() {
        assertTrue(Build.VERSION.SDK_INT < 31)
        assertNull(AndroidGlassEffect.create(tokens))
        val fallback = GlassRaster.paint(GlassRaster.checker(200, 140), blur = false)
        var labeled = false
        var y = 0
        while (y < fallback.height) {
            var x = 0
            while (x < fallback.width) {
                if (fallback.getPixel(x, y) != Color.TRANSPARENT) labeled = true
                x++
            }
            y++
        }
        assertTrue(labeled)
        val rasterSrc = File("src/main/kotlin/a3/renderers/android/compose/GlassRaster.kt").readText()
        assertTrue(rasterSrc.contains(GlassRaster.BLUR_UNAVAILABLE))
        save("api-fallback.png", fallback)
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                ComposeRenderer(unknownOutput())
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("glass-fallback", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("n1-uncertain").assertIsDisplayed()
    }

    @Test
    fun LG_002_vibrancy_raises_saturation() {
        val raw = GlassRaster.checker(200, 140)
        val plain = GlassRaster.paint(raw, blur = true, vibrancy = false, highlights = false, shadows = false)
        val vibrant = GlassRaster.paint(raw, blur = true, vibrancy = true, highlights = false, shadows = false)
        val delta = GlassRaster.meanSaturation(vibrant) - GlassRaster.meanSaturation(plain)
        assertTrue(delta > GlassRaster.SATURATION_DELTA_MIN, "sat delta $delta")
        assertEquals(1.4f, tokens.vibrancySaturation)
    }

    @Test
    fun LG_003_inner_and_outer_highlights() {
        assertEquals(1, tokens.inner.widthPx)
        assertEquals(1, tokens.outer.widthPx)
        val raw = GlassRaster.solid(200, 140)
        val plain = GlassRaster.paint(raw, blur = false, vibrancy = false, highlights = false, shadows = false)
        val marked = GlassRaster.paint(raw, blur = false, vibrancy = false, highlights = true, shadows = false)
        val x = 100
        assertTrue(GlassRaster.innerHighlightBand(marked, plain), "inner 1px")
        assertTrue(GlassRaster.outerHighlightBand(marked, plain), "outer 1px")
    }

    @Test
    fun LG_004_squircle_differs_from_round_rect() {
        val round = GlassRaster.paintRoundRectFill(80, 80, 20f)
        val squircle = GlassRaster.paintSquircleFill(80, 80, 20f, tokens.superellipseN)
        assertNotEquals(GlassRaster.fingerprint(round), GlassRaster.fingerprint(squircle))
        assertEquals(Color.WHITE, round.getPixel(4, 4))
        val squirclePx = squircle.getPixel(4, 4)
        assertTrue(android.graphics.Color.red(squirclePx) < 40)
        assertTrue(android.graphics.Color.green(squirclePx) < 40)
        assertTrue(android.graphics.Color.blue(squirclePx) < 40)
        val compare = android.graphics.Bitmap.createBitmap(160, 80, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(compare)
        canvas.drawBitmap(round, 0f, 0f, null)
        canvas.drawBitmap(squircle, 80f, 0f, null)
        save("squircle-vs-round.png", compare)
    }

    @Test
    fun LG_005_nested_radius_clamped() {
        assertEquals(4f, SquircleGeometry.nestedRadius(14f, 16f, 4f))
        assertEquals(6f, SquircleGeometry.nestedRadius(14f, 8f, 4f))
        assertTrue(SquircleGeometry.nestedRadius(14f, 16f, 4f) > 0f)
        val nested = GlassRaster.paintNested(80, 80, 14f, 16f)
        assertEquals(Color.WHITE, nested.getPixel(40, 40))
        assertEquals(Color.BLACK, nested.getPixel(2, 40))
    }

    @Test
    fun LG_006_qualitative_fingerprint() {
        val raw = GlassRaster.checker(200, 140)
        val glass = GlassRaster.paint(raw, blur = true)
        val sharp = GlassRaster.paint(raw, blur = false, vibrancy = false, highlights = false, shadows = false)
        val marked = GlassRaster.paint(GlassRaster.solid(200, 140), blur = false, vibrancy = false, highlights = true, shadows = false)
        val plain = GlassRaster.paint(GlassRaster.solid(200, 140), blur = false, vibrancy = false, highlights = false, shadows = false)
        val ratio = GlassRaster.edgeEnergy(glass) / GlassRaster.edgeEnergy(sharp)
        val sat = GlassRaster.meanSaturation(glass)
        assertTrue(ratio < 1f)
        assertTrue(sat > 0.0)
        assertTrue(GlassRaster.innerHighlightBand(marked, plain))
        reports.mkdirs()
        File(reports, "android-glass-fingerprint.txt").writeText(
            "blurRatio=$ratio sat=$sat inner=true\n"
        )
    }

    @Test
    fun LG_007_axis_badge_full_opacity_through_glass() {
        val overlay = axisOverlay()
        val glass = GlassRaster.paint(GlassRaster.checker(320, 80), blur = true, overlayInk = overlay)
        var stamped = 0
        var y = 0
        while (y < overlay.height) {
            var x = 0
            while (x < overlay.width) {
                val src = overlay.getPixel(x, y)
                if (src != Color.WHITE && Color.alpha(src) == 255) {
                    val dst = glass.getPixel(x, y)
                    assertEquals(255, Color.alpha(dst), "badge alpha at $x,$y")
                    assertEquals(src, dst)
                    stamped++
                }
                x++
            }
            y++
        }
        assertTrue(stamped > 20)
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                ComposeRenderer(unknownOutput())
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("n1-uncertain").assertIsDisplayed()
        composeRule.onNodeWithTag("n1-axis").assertIsDisplayed()
        composeRule.onNodeWithTag("glass-surface", useUnmergedTree = true).assertIsDisplayed()
        val catalog = File("src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt").readText()
        val glassSrc = File("src/main/kotlin/a3/renderers/android/compose/GlassSurface.kt").readText()
        assertTrue(catalog.contains("GlassSurface"))
        assertTrue(!glassSrc.contains("refraction") && !glassSrc.contains("noiseTexture"))
    }

    @Test
    fun LG_008_frozen_trees_have_empty_diff() {
        val root = File("../..")
        val proc = ProcessBuilder(
            "git",
            "diff",
            "--stat",
            "--",
            "core/",
            "core/json",
            "core/admission",
            "core/action",
            "prediction/",
            "projection/",
            "a3ui/",
            "renderers/android-core/",
            "adapters/",
            "intent-model/",
            "broker/",
            "launcher/"
        ).directory(root).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor())
        assertTrue(out.isBlank(), out)
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/GlassSurface.kt").exists())
        assertTrue(File("src/main/resources/a3ui-graphics/surfaces.json").exists())
    }

    private fun axisOverlay(): android.graphics.Bitmap {
        val interpreter = A3UIInterpreter()
        val motion = MotionSpec(280.0, 24.0, "standard", 240)
        val node = Node("n1", "text", axis = EpistemicAxis(support = EpistemicSupport.UNKNOWN))
        val output = interpreter.interpret(
            A3UISurface(
                id = "s1",
                projectionRef = "p1",
                presentationRef = "ps1",
                lineage = CausalLineage("ctx", 1, "e1"),
                densityHint = "comfortable",
                colorTokens = listOf("accent"),
                motion = motion,
                morph = MorphSpec("ps1", "shared-element", emptyList(), motion),
                gestures = GestureMap(emptyList()),
                haptics = HapticMap(emptyList()),
                nodes = listOf(node),
                bindings = listOf(Binding("train.price", "n1", "content")),
                producedAt = t
            ),
            PresentationState(
                id = "ps1",
                sourceStateVersion = 1,
                producedAt = t,
                atoms = listOf(PresentationAtom("price", "train.price", "12.40", 60)),
                lineage = CausalLineage("ctx", 1, "e1")
            ),
            RendererContext(
                formFactor = "phone",
                density = "comfortable",
                tokens = TreeMap<String, ColorValue>().apply { put("accent", ColorValue(0, 90, 200)) },
                clock = FixedClock(t),
                reducedMotion = true
            )
        )
        return ExposureRaster.paint(output)
    }

    private fun unknownOutput() = A3UIInterpreter().interpret(
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
            nodes = listOf(Node("n1", "text", axis = EpistemicAxis(support = EpistemicSupport.UNKNOWN))),
            bindings = listOf(Binding("train.price", "n1", "content")),
            producedAt = t
        ),
        PresentationState(
            id = "ps1",
            sourceStateVersion = 1,
            producedAt = t,
            atoms = listOf(PresentationAtom("price", "train.price", "12.40", 60)),
            lineage = CausalLineage("ctx", 1, "e1")
        ),
        RendererContext(
            formFactor = "phone",
            density = "comfortable",
            tokens = TreeMap<String, ColorValue>().apply { put("accent", ColorValue(0, 90, 200)) },
            clock = FixedClock(t),
            reducedMotion = true
        )
    )
}
