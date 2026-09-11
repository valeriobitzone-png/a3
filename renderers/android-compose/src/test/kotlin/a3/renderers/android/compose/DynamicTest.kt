package a3.renderers.android.compose

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.EpistemicAction
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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DynamicTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val assets = File("../../review-assets")
    private val reports = File("build/reports")
    private val colors = GraphicsTokens.colors
    private val type = GraphicsTokens.typography

    private fun save(name: String, bitmap: android.graphics.Bitmap) {
        reports.mkdirs()
        assets.mkdirs()
        DynamicRaster.write(bitmap, File(reports, name))
        DynamicRaster.write(bitmap, File(assets, name))
        assertTrue(File(assets, name).length() > 0)
    }

    @Test
    fun DT_001_color_extraction_from_wallpaper_fixture() {
        assertEquals("0.1", colors.version)
        assertEquals(4.5f, colors.minRatio)
        val wall = DynamicPalette.fixtureWallpaper(80, 80)
        val tonal = DynamicPalette.extract(wall, 80, 80)
        assertEquals(DynamicPalette.SOURCE_EXTRACTED, tonal.source)
        assertTrue(DynamicPalette.valid(tonal))
        assertEquals(DynamicPalette.tokenInk(), tonal.ink)
        assertEquals(DynamicPalette.tokenPaper(), tonal.paper)
        assertTrue(DynamicPalette.contrast(tonal.ink, tonal.paper) >= colors.minRatio)
        assertTrue(DynamicPalette.contrast(tonal.primary, tonal.paper) >= colors.minRatio)
        val empty = DynamicPalette.extract(IntArray(0), 0, 0)
        assertEquals(DynamicPalette.SOURCE_FALLBACK, empty.source)
        assertEquals(DynamicPalette.tokenAmber(), empty.secondary)
        save("palette-extracted.png", DynamicRaster.paletteStrip(tonal))
    }

    @Test
    fun DT_002_adaptive_contrast_on_confused_surface() {
        val back = DynamicPalette.checker(120, 48)
        assertTrue(DynamicPalette.confused(back, 120, 48))
        val gray = DynamicPalette.Rgb(120, 120, 120)
        val mean = run {
            val adapted = DynamicPalette.adaptText(gray, back, 120, 48)
            adapted
        }
        val before = DynamicPalette.contrast(gray, DynamicPalette.unpack(average(back)))
        val after = DynamicPalette.contrast(mean, DynamicPalette.unpack(average(back)))
        assertTrue(after - before > DynamicPalette.ADAPT_DELTA_MIN, "delta ${after - before}")
        assertTrue(after >= colors.minRatio)
        save("adaptive-contrast-before.png", DynamicRaster.contrastSample(back, 120, 48, gray))
        save("adaptive-contrast-after.png", DynamicRaster.contrastSample(back, 120, 48, mean))
    }

    @Test
    fun DT_003_variable_weight_pressed_plus_50() {
        assertTrue(type.axes.contains("wght"))
        assertEquals(400, DynamicType.baseWeight())
        assertEquals(450, DynamicType.weight(pressed = true))
        assertEquals(50, DynamicType.weight(true) - DynamicType.weight(false))
        val rest = DynamicRaster.typeSample(18f, DynamicType.weight(false), 1f, 0f, "Ag")
        val pressed = DynamicRaster.typeSample(18f, DynamicType.weight(true), 1f, 0f, "Ag")
        assertTrue(DynamicRaster.inkCount(pressed) > DynamicRaster.inkCount(rest))
        save("variable-weight-pressed.png", pressed)
    }

    @Test
    fun DT_004_variable_width_condensed_when_dense() {
        assertTrue(DynamicType.width("compact") < DynamicType.width("comfortable"))
        assertTrue(DynamicType.condensed("compact"))
        val dense = DynamicRaster.typeSample(18f, 400, DynamicType.width("compact"), 0f, "AVAVAV")
        val roomy = DynamicRaster.typeSample(18f, 400, DynamicType.width("comfortable"), 0f, "AVAVAV")
        assertTrue(DynamicRaster.contentRight(dense) < DynamicRaster.contentRight(roomy))
    }

    @Test
    fun DT_005_optical_sizing_small_text_wider_tracking() {
        assertTrue(type.axes.contains("opsz"))
        val t12 = DynamicType.trackingEm(12f)
        val t18 = DynamicType.trackingEm(18f)
        val t24 = DynamicType.trackingEm(24f)
        assertEquals(0f, t18, 0.0001f)
        assertTrue(t12 > t24, "12sp $t12 vs 24sp $t24")
        val small = DynamicRaster.typeSample(12f, 400, 1f, t12, "AVAV")
        val large = DynamicRaster.typeSample(24f, 400, 1f, t24, "AVAV")
        save("optical-sizing-12sp.png", small)
        save("optical-sizing-24sp.png", large)
        assertTrue(DynamicRaster.meanGap(small) > DynamicRaster.meanGap(large) - 0.5f)
    }

    @Test
    fun DT_006_animated_glyph_morph_visible() {
        val start = GlyphGeometry.points(GlyphGeometry.Kind.SPINNER, 1f, true)
        val mid = GlyphGeometry.morph(GlyphGeometry.Kind.SPINNER, GlyphGeometry.Kind.CHECK, 0.45f)
        val end = GlyphGeometry.points(GlyphGeometry.Kind.CHECK, 1f, true)
        val a = DynamicRaster.glyphFrame(start)
        val b = DynamicRaster.glyphFrame(mid)
        val c = DynamicRaster.glyphFrame(end)
        assertNotEquals(DynamicRaster.fingerprint(a), DynamicRaster.fingerprint(b))
        assertNotEquals(DynamicRaster.fingerprint(b), DynamicRaster.fingerprint(c))
        assertNotEquals(DynamicRaster.fingerprint(a), DynamicRaster.fingerprint(c))
        save("glyph-morph-frames.png", DynamicRaster.morphStrip(GlyphGeometry.Kind.SPINNER, GlyphGeometry.Kind.CHECK))
    }

    @Test
    fun DT_007_glyph_follows_epistemic_axis() {
        assertEquals(
            GlyphGeometry.Kind.SPINNER,
            glyphKind(EpistemicAxis(action = EpistemicAction.PENDING))
        )
        assertEquals(
            GlyphGeometry.Kind.CHECK,
            glyphKind(EpistemicAxis(action = EpistemicAction.DONE))
        )
        assertEquals(
            GlyphGeometry.Kind.PAUSE,
            glyphKind(EpistemicAxis(status = EpistemicStatus.HELD))
        )
        composeRule.mainClock.autoAdvance = false
        var shown by mutableStateOf(axisOut(EpistemicAxis(action = EpistemicAction.PENDING)))
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) { ComposeRenderer(shown) }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("n1-pending", useUnmergedTree = true).assertIsDisplayed()
        shown = axisOut(EpistemicAxis(action = EpistemicAction.DONE))
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("n1-done").assertIsDisplayed()
        composeRule.onNodeWithTag("n1-glyph-check", useUnmergedTree = true).assertIsDisplayed()
        shown = axisOut(EpistemicAxis(status = EpistemicStatus.HELD))
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("n1-held").assertIsDisplayed()
        composeRule.onNodeWithTag("n1-glyph-held", useUnmergedTree = true).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = true
        val catalog = File("src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt").readText()
        assertTrue(catalog.contains("GlyphGeometry"))
        assertTrue(catalog.contains("EpistemicGlyph"))
    }

    @Test
    fun DT_008_reduced_motion_static_glyph_contrast_still_adapts() {
        val reducedPts = GlyphGeometry.points(GlyphGeometry.Kind.CHECK, 0.1f, reduced = true)
        val finalPts = GlyphGeometry.points(GlyphGeometry.Kind.CHECK, 1f, reduced = false)
        assertEquals(reducedPts, finalPts)
        val back = DynamicPalette.checker(80, 40)
        val gray = DynamicPalette.Rgb(130, 130, 130)
        val adapted = DynamicPalette.adaptText(gray, back, 80, 40)
        assertTrue(DynamicPalette.contrast(adapted, DynamicPalette.unpack(average(back))) > DynamicPalette.contrast(gray, DynamicPalette.unpack(average(back))))
        val morph = File("src/main/kotlin/a3/renderers/android/compose/EpistemicGlyph.kt").readText()
        assertTrue(morph.contains("if (reduced"))
    }

    @Test
    fun DT_009_palette_roles_are_token_locked() {
        val wall = DynamicPalette.fixtureWallpaper(64, 64)
        val tonal = DynamicPalette.extract(wall, 64, 64)
        assertEquals(colors.ink, tonal.ink.pack())
        assertEquals(colors.paper, tonal.paper.pack())
        assertEquals(6, tonal.roles().size)
    }

    @Test
    fun DT_010_axis_glass_motion_and_color_coexist() {
        assertEquals(2800, Theme.heldPulseMs)
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/GlassSurface.kt").exists())
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/MotionPhysics.kt").exists())
        assertTrue(File("src/main/resources/a3ui-graphics/typography.json").exists())
        assertTrue(File("src/main/resources/a3ui-graphics/colors.json").exists())
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                ComposeRenderer(axisOut(EpistemicAxis(status = EpistemicStatus.HELD)))
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("n1-held").assertIsDisplayed()
        composeRule.onNodeWithTag("glass-surface", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("n1-glyph-held", useUnmergedTree = true).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun DT_011_freeze_only_compose_renderers() {
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

    private fun average(pixels: IntArray): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        for (p in pixels) {
            val c = DynamicPalette.unpack(p)
            r += c.r
            g += c.g
            b += c.b
        }
        val n = pixels.size.coerceAtLeast(1)
        return DynamicPalette.Rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt()).pack()
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
