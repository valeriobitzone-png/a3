package a3.renderers.mac.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LiquidGlassMacTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val assets = File("../../review-assets")
    private val reports = File("build/reports")
    private val tokens = GraphicsTokens.snapshot

    private fun save(name: String, image: BufferedImage) {
        reports.mkdirs()
        assets.mkdirs()
        MacGlassRaster.write(image, File(reports, name))
        MacGlassRaster.write(image, File(assets, name))
        assertTrue(File(assets, name).length() > 0)
    }

    private fun luma(c: Int): Int {
        val col = Color(c, true)
        return (0.2126 * col.red + 0.7152 * col.green + 0.0722 * col.blue).roundToInt()
    }

    @Test
    fun LG_001_blur_visible() {
        assertEquals("0.1", tokens.version)
        val raw = MacGlassRaster.checker(200, 140)
        val sharp = MacGlassRaster.paint(raw, blur = false, vibrancy = false, highlights = false, shadows = false)
        val blurred = MacGlassRaster.paint(raw, blur = true, vibrancy = false, highlights = false, shadows = false)
        val ratio = MacGlassRaster.edgeEnergy(blurred) / MacGlassRaster.edgeEnergy(sharp)
        assertTrue(ratio < MacGlassRaster.BLUR_ENERGY_RATIO_MAX, "blur ratio $ratio")
        val overlay = MacRaster.paint(unknownOutput())
        save("mac-glass.png", MacGlassRaster.paint(raw, blur = true, overlayInk = overlay))
        val surface = File("src/main/kotlin/a3/renderers/mac/compose/GlassBackdrop.kt").readText()
        assertTrue(surface.contains(".blur(") && surface.contains("setToSaturation"))
        assertTrue(!File("src/main/kotlin/a3/renderers/mac/compose/MacGlassSurface.kt").readText().contains(".background(Color.White)"))
    }

    @Test
    fun LG_002_vibrancy_raises_saturation() {
        val raw = MacGlassRaster.checker(200, 140)
        val plain = MacGlassRaster.paint(raw, blur = true, vibrancy = false, highlights = false, shadows = false)
        val vibrant = MacGlassRaster.paint(raw, blur = true, vibrancy = true, highlights = false, shadows = false)
        val delta = MacGlassRaster.meanSaturation(vibrant) - MacGlassRaster.meanSaturation(plain)
        assertTrue(delta > MacGlassRaster.SATURATION_DELTA_MIN, "sat delta $delta")
    }

    @Test
    fun LG_003_highlights() {
        assertEquals(1, tokens.inner.widthPx)
        assertEquals(1, tokens.outer.widthPx)
        val raw = MacGlassRaster.solid(200, 140)
        val plain = MacGlassRaster.paint(raw, blur = false, vibrancy = false, highlights = false, shadows = false)
        val marked = MacGlassRaster.paint(raw, blur = false, vibrancy = false, highlights = true, shadows = false)
        assertTrue(MacGlassRaster.innerHighlightBand(marked, plain))
        assertTrue(MacGlassRaster.outerHighlightBand(marked, plain))
    }

    @Test
    fun LG_004_squircle_differs_from_round_rect() {
        val round = MacGlassRaster.paintRoundRectFill(80, 80, 20f)
        val squircle = MacGlassRaster.paintSquircleFill(80, 80, 20f, tokens.superellipseN)
        assertTrue(luma(round.getRGB(4, 4)) > 200)
        assertTrue(luma(squircle.getRGB(4, 4)) < 40)
        assertNotEquals(round.getRGB(4, 4), squircle.getRGB(4, 4))
    }

    @Test
    fun LG_005_nested_radius() {
        assertEquals(4f, SquircleGeometry.nestedRadius(14f, 16f, 4f))
        assertEquals(6f, SquircleGeometry.nestedRadius(14f, 8f, 4f))
        val nested = MacGlassRaster.paintNested(80, 80, 14f, 16f)
        assertTrue(luma(nested.getRGB(40, 40)) > 200)
        assertTrue(luma(nested.getRGB(2, 40)) < 40)
    }

    @Test
    fun LG_006_qualitative_fingerprint_matches_android_semantics() {
        val raw = MacGlassRaster.checker(200, 140)
        val glass = MacGlassRaster.paint(raw, blur = true)
        val sharp = MacGlassRaster.paint(raw, blur = false, vibrancy = false, highlights = false, shadows = false)
        val marked = MacGlassRaster.paint(MacGlassRaster.solid(200, 140), blur = false, vibrancy = false, highlights = true, shadows = false)
        val plainHi = MacGlassRaster.paint(MacGlassRaster.solid(200, 140), blur = false, vibrancy = false, highlights = false, shadows = false)
        val ratio = MacGlassRaster.edgeEnergy(glass) / MacGlassRaster.edgeEnergy(sharp)
        assertTrue(ratio < 1f)
        assertTrue(MacGlassRaster.meanSaturation(glass) > 0.0)
        assertTrue(MacGlassRaster.innerHighlightBand(marked, plainHi))
        reports.mkdirs()
        File(reports, "mac-glass-fingerprint.txt").writeText(
            "blurRatio=$ratio sat=${MacGlassRaster.meanSaturation(glass)} inner=true\n"
        )
    }

    @Test
    fun LG_007_axis_badge_full_opacity_through_glass() {
        val overlay = MacRaster.paint(unknownOutput())
        val glass = MacGlassRaster.paint(MacGlassRaster.checker(320, 80), blur = true, overlayInk = overlay)
        val white = Color.WHITE.rgb
        var stamped = 0
        var y = 0
        while (y < overlay.height) {
            var x = 0
            while (x < overlay.width) {
                val src = overlay.getRGB(x, y)
                if (src != white && Color(src, true).alpha == 255) {
                    assertEquals(255, Color(glass.getRGB(x, y), true).alpha)
                    assertEquals(src, glass.getRGB(x, y))
                    stamped++
                }
                x++
            }
            y++
        }
        assertTrue(stamped > 20)
        val (_, calendar) = MacFixtures.composeCalendar()
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                MacRenderer(calendar)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_cal.hotel-held").assertIsDisplayed()
        composeRule.onAllNodesWithTag("glass-surface", useUnmergedTree = true)[0].assertIsDisplayed()
        val catalog = File("src/main/kotlin/a3/renderers/mac/compose/MacCatalog.kt").readText()
        assertTrue(catalog.contains("MacGlassSurface"))
        val glassSrc = File("src/main/kotlin/a3/renderers/mac/compose/MacGlassSurface.kt").readText()
        assertTrue(!glassSrc.contains("refraction") && !glassSrc.contains("noiseTexture"))
    }

    private fun unknownOutput() = MacFixtures.interpret(
        MacFixtures.pricePresentation(),
        MacFixtures.priceProjection(),
        MacFixtures.unknownSupportFacts()
    ).second
}
