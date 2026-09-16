// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackdropGlassMacTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val root = File("../..")
    private val assets = File(root, "review-assets")
    private val reports = File("build/reports")

    private fun save(name: String, image: BufferedImage) {
        reports.mkdirs()
        assets.mkdirs()
        MacGlassRaster.write(image, File(reports, name))
        MacGlassRaster.write(image, File(assets, name))
        assertTrue(File(assets, name).length() > 0)
    }

    private fun chroma(image: BufferedImage, x0: Int, y0: Int, x1: Int, y1: Int): Float {
        val pixels = IntArray((x1 - x0) * (y1 - y0))
        var i = 0
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                pixels[i++] = image.getRGB(x, y)
                x++
            }
            y++
        }
        return GlassBackdrop.chroma(pixels)
    }

    @Test
    fun GB_001_glass_blurs_scene_not_white_slab() {
        assertEquals("shared-background", GlassBackdrop.TECHNIQUE)
        val src = File("src/main/kotlin/a3/renderers/mac/compose/MacGlassSurface.kt").readText()
        assertTrue(!src.contains(".background(Color.White)"))
        val wall = MacGlassRaster.wallpaper(200, 140)
        val replica = MacGlassRaster.paint(wall, blur = true, vibrancy = true, highlights = false, shadows = false, fill = false)
        val filled = MacGlassRaster.paint(wall, blur = true, vibrancy = true, highlights = false, shadows = false, fill = true)
        val white = MacGlassRaster.paint(
            MacGlassRaster.solid(200, 140, Color.WHITE.rgb),
            blur = true,
            vibrancy = false,
            highlights = false,
            shadows = false,
            fill = true
        )
        val replicaChroma = chroma(replica, 40, 30, 160, 110)
        val filledChroma = chroma(filled, 40, 30, 160, 110)
        val whiteChroma = chroma(white, 40, 30, 160, 110)
        assertTrue(replicaChroma > 0.15f, "replica not wallpaper chroma=$replicaChroma")
        assertTrue(filledChroma > whiteChroma + 0.05f, "filled=$filledChroma white=$whiteChroma")
        val (_, calendar) = MacFixtures.composeCalendar()
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                MacRenderer(calendar)
            }
        }
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("glass-surface", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.onNodeWithTag("glass-backdrop-sharp", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("glass-backdrop-replica", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        save("catalogo-reale-dopo-mac.png", composeRule.onNodeWithTag("a3-material").captureToImage().toAwtImage())
    }

    @Test
    fun GB_002_gutter_sharp_vs_blurred_ring() {
        val wall = MacGlassRaster.wallpaper(200, 140)
        val sharp = MacGlassRaster.paint(wall, blur = false, vibrancy = false, highlights = false, shadows = false)
        val glass = MacGlassRaster.paint(wall, blur = true, vibrancy = false, highlights = false, shadows = false)
        val ratio = MacGlassRaster.edgeEnergy(glass) / MacGlassRaster.edgeEnergy(sharp)
        assertTrue(ratio < MacGlassRaster.BLUR_ENERGY_RATIO_MAX, "blur ratio $ratio")
        save("anello-sfocato-mac.png", glass)
    }

    @Test
    fun GB_003_material_color_utilities_parity_and_aa() {
        val extractSrc = File("src/main/kotlin/a3/renderers/mac/compose/DynamicPalette.kt").readText()
        assertTrue(extractSrc.contains("QuantizerCelebi"))
        val wall = DynamicPalette.fixtureWallpaper(80, 80)
        val a = DynamicPalette.extract(wall, 80, 80)
        val b = DynamicPalette.extract(wall.copyOf(), 80, 80)
        assertEquals(a, b)
        assertEquals(DynamicPalette.SOURCE_EXTRACTED, a.source)
        assertTrue(DynamicPalette.contrast(a.primary, a.paper) >= GraphicsTokens.colors.minRatio)
    }

    @Test
    fun GB_004_badges_full_opacity() {
        val (_, calendar) = MacFixtures.composeCalendar()
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                MacRenderer(calendar)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_cal.hotel-held").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.dinner-contradicted").assertIsDisplayed()
    }

    @Test
    fun GB_005_shared_background() {
        assertEquals("shared-background", GlassBackdrop.TECHNIQUE)
        val review = File(root, "REVIEW_RENDERER_BACKDROP.md").readText()
        assertTrue(review.contains("shared-background"))
        assertTrue(review.contains("scartato"))
    }
}
