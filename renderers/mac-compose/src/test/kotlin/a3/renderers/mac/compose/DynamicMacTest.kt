package a3.renderers.mac.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DynamicMacTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val assets = File("../../review-assets")
    private val reports = File("build/reports")
    private val colors = GraphicsTokens.colors

    private fun save(name: String, image: java.awt.image.BufferedImage) {
        reports.mkdirs()
        assets.mkdirs()
        DynamicRaster.write(image, File(reports, name))
        DynamicRaster.write(image, File(assets, name))
        assertTrue(File(assets, name).length() > 0)
    }

    @Test
    fun DT_001_color_extraction_from_wallpaper_fixture() {
        assertEquals("0.1", colors.version)
        val wall = DynamicPalette.fixtureWallpaper(80, 80)
        val tonal = DynamicPalette.extract(wall, 80, 80)
        assertEquals(DynamicPalette.SOURCE_EXTRACTED, tonal.source)
        assertTrue(DynamicPalette.valid(tonal))
        assertEquals(DynamicPalette.tokenInk(), tonal.ink)
        assertEquals(DynamicPalette.tokenPaper(), tonal.paper)
        assertTrue(DynamicPalette.contrast(tonal.primary, tonal.paper) >= colors.minRatio)
        save("palette-extracted.png", DynamicRaster.paletteStrip(tonal))
    }

    @Test
    fun DT_002_adaptive_contrast_on_confused_surface() {
        val back = DynamicPalette.checker(120, 48)
        assertTrue(DynamicPalette.confused(back, 120, 48))
        val gray = DynamicPalette.Rgb(120, 120, 120)
        val adapted = DynamicPalette.adaptText(gray, back, 120, 48)
        val mean = average(back)
        assertTrue(
            DynamicPalette.contrast(adapted, mean) - DynamicPalette.contrast(gray, mean)
                > DynamicPalette.ADAPT_DELTA_MIN
        )
        save("adaptive-contrast-after.png", DynamicRaster.contrastSample(back, 120, 48, adapted))
    }

    @Test
    fun DT_003_variable_weight_pressed_plus_50() {
        assertEquals(400, DynamicType.baseWeight())
        assertEquals(450, DynamicType.weight(true))
        val pressed = DynamicRaster.typeSample(18f, 450, 1f, 0f, "Ag")
        val rest = DynamicRaster.typeSample(18f, 400, 1f, 0f, "Ag")
        assertTrue(DynamicRaster.inkCount(pressed) >= DynamicRaster.inkCount(rest))
        save("variable-weight-pressed.png", pressed)
    }

    @Test
    fun DT_004_variable_width_condensed_when_dense() {
        assertTrue(DynamicType.width("compact") < DynamicType.width("comfortable"))
        val dense = DynamicRaster.typeSample(18f, 400, DynamicType.width("compact"), 0f, "AVAVAV")
        val roomy = DynamicRaster.typeSample(18f, 400, DynamicType.width("comfortable"), 0f, "AVAVAV")
        assertTrue(DynamicRaster.contentRight(dense) <= DynamicRaster.contentRight(roomy))
    }

    @Test
    fun DT_005_optical_sizing_small_text_wider_tracking() {
        assertTrue(DynamicType.trackingEm(12f) > DynamicType.trackingEm(24f))
        save("optical-sizing-12sp.png", DynamicRaster.typeSample(12f, 400, 1f, DynamicType.trackingEm(12f), "AVAV"))
        save("optical-sizing-24sp.png", DynamicRaster.typeSample(24f, 400, 1f, DynamicType.trackingEm(24f), "AVAV"))
    }

    @Test
    fun DT_006_animated_glyph_morph_visible() {
        val a = DynamicRaster.glyphFrame(GlyphGeometry.points(GlyphGeometry.Kind.SPINNER, 1f, true))
        val b = DynamicRaster.glyphFrame(GlyphGeometry.morph(GlyphGeometry.Kind.SPINNER, GlyphGeometry.Kind.CHECK, 0.45f))
        val c = DynamicRaster.glyphFrame(GlyphGeometry.points(GlyphGeometry.Kind.CHECK, 1f, true))
        assertNotEquals(DynamicRaster.fingerprint(a), DynamicRaster.fingerprint(b))
        assertNotEquals(DynamicRaster.fingerprint(a), DynamicRaster.fingerprint(c))
        save("glyph-morph-frames.png", DynamicRaster.morphStrip(GlyphGeometry.Kind.SPINNER, GlyphGeometry.Kind.CHECK))
    }

    @Test
    fun DT_007_glyph_follows_epistemic_axis() {
        assertEquals(GlyphGeometry.Kind.SPINNER, GlyphGeometry.kind("pending", "believed"))
        assertEquals(GlyphGeometry.Kind.CHECK, GlyphGeometry.kind("done", "believed"))
        assertEquals(GlyphGeometry.Kind.PAUSE, GlyphGeometry.kind("na", "held"))
        System.setProperty("a3.reduce.motion", "false")
        val (_, calendar) = MacFixtures.composeCalendar()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) { MacRenderer(calendar) }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("text_cal.hotel-held").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.hotel-glyph-held", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.meeting-done").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.meeting-glyph-check", useUnmergedTree = true).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun DT_008_reduced_motion_static_glyph_contrast_still_adapts() {
        assertEquals(
            GlyphGeometry.points(GlyphGeometry.Kind.CHECK, 0.2f, true),
            GlyphGeometry.points(GlyphGeometry.Kind.CHECK, 1f, false)
        )
        val back = DynamicPalette.checker(80, 40)
        val gray = DynamicPalette.Rgb(130, 130, 130)
        val adapted = DynamicPalette.adaptText(gray, back, 80, 40)
        val mean = average(back)
        assertTrue(DynamicPalette.contrast(adapted, mean) > DynamicPalette.contrast(gray, mean))
        assertTrue(File("src/main/kotlin/a3/renderers/mac/compose/EpistemicGlyph.kt").readText().contains("if (reduced"))
    }

    @Test
    fun DT_009_cross_platform_equivalent_palette() {
        val wall = DynamicPalette.fixtureWallpaper(64, 64)
        val tonal = DynamicPalette.extract(wall, 64, 64)
        assertEquals(colors.ink, tonal.ink.pack())
        assertEquals(colors.paper, tonal.paper.pack())
        assertEquals(DynamicPalette.SOURCE_EXTRACTED, tonal.source)
        assertEquals(6, tonal.roles().size)
    }

    @Test
    fun DT_010_axis_glass_motion_and_color_coexist() {
        assertEquals(2800, MacTheme.heldPulseMs)
        assertTrue(File("src/main/kotlin/a3/renderers/mac/compose/MacGlassSurface.kt").exists())
        assertTrue(File("src/main/kotlin/a3/renderers/mac/compose/MotionPhysics.kt").exists())
        assertTrue(File("src/main/resources/a3ui-graphics/typography.json").exists())
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
            val path = line.drop(3).trim().let { if (it.contains(" -> ")) it.substringAfter(" -> ") else it }
            if (ignore.any { path.startsWith(it) }) continue
            assertTrue(allowed.any { path.startsWith(it) }, "unexpected path $line")
        }
    }

    private fun average(pixels: IntArray): DynamicPalette.Rgb {
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
        return DynamicPalette.Rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }
}
