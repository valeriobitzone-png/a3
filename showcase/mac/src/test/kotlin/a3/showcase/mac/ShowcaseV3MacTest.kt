// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.showcase.mac

import a3.showcase.LoggingSink
import a3.showcase.ShowcaseJournal
import a3.showcase.ShowcaseLevel
import a3.showcase.ShowcaseScene
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.io.File
import javax.imageio.ImageIO
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShowcaseV3MacTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val root = File("../..")
    private val assets = File(root, "review-assets/showcase-v3")

    @Before
    fun accessibilityOff() {
        System.setProperty("a3.reduce.motion", "false")
        System.setProperty("a3.high.contrast", "false")
    }

    private fun pump(frames: Int = 3) {
        repeat(frames) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun host(
        reduced: Boolean = false,
        glass: Boolean = true,
        silent: Boolean = false,
        ambient: Boolean = false,
        talkback: Boolean = false,
        journal: ShowcaseJournal = ShowcaseJournal()
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ShowcaseApp(
                formFactor = "desktop",
                initialLevel = ShowcaseLevel.ALL,
                initialReduced = reduced,
                initialTalkback = talkback,
                initialGlass = glass,
                initialSilent = silent,
                initialAmbient = ambient,
                staticChrome = true,
                journal = journal,
                sink = LoggingSink(journal)
            )
        }
        pump(4)
    }

    private fun exists(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun SV3_001_three_surfaces() {
        host()
        composeRule.onNodeWithTag("scene-train").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-hotel").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-calendar").assertIsDisplayed()
        composeRule.onNodeWithText(ShowcaseScene.INTENT).assertIsDisplayed()
    }

    @Test
    fun SV3_002_pending_stable() {
        host()
        composeRule.onNodeWithTag("scene-train-outline").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-train-label").assertIsDisplayed()
        assertTrue(!exists("scene-train-spinner"))
        val train = composeRule.onNodeWithTag("scene-train").fetchSemanticsNode()
        val hotel = composeRule.onNodeWithTag("scene-hotel").fetchSemanticsNode()
        assertTrue(abs(train.size.height - hotel.size.height) < 4f)
    }

    @Test
    fun SV3_003_stale_age() {
        host()
        composeRule.onNodeWithText(ShowcaseScene.HOTEL_PRICE).assertIsDisplayed()
        composeRule.onNodeWithText("2h fa").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-hotel-decay").assertIsDisplayed()
    }

    @Test
    fun SV3_004_contradicted() {
        host()
        composeRule.onNodeWithTag("scene-calendar-confirm").assertIsNotEnabled()
        composeRule.onNodeWithText(ShowcaseScene.FORBID_REASON).assertIsDisplayed()
    }

    @Test
    fun SV3_005_provenance() {
        host()
        composeRule.onNodeWithText("API firmata").assertIsDisplayed()
        composeRule.onNodeWithText("modello").assertIsDisplayed()
        composeRule.onNodeWithText("inferita").assertIsDisplayed()
    }

    @Test
    fun SV3_006_reduced() {
        host(reduced = true)
        composeRule.onNodeWithTag("scene-calendar-reason").assertIsDisplayed()
        composeRule.onNodeWithText("2h fa").assertIsDisplayed()
    }

    @Test
    fun SV3_007_blur_off() {
        host(glass = false)
        composeRule.onNodeWithTag("scene-blur-off", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("scene-hotel-stale").assertIsDisplayed()
    }

    @Test
    fun SV3_008_silent() {
        val journal = ShowcaseJournal()
        host(silent = true, journal = journal)
        composeRule.onNodeWithTag("scene-train-label").assertIsDisplayed()
        assertTrue(journal.audio().all { !it.emitted })
    }

    @Test
    fun SV3_009_ambient() {
        host(ambient = true)
        composeRule.onNodeWithTag("ambient-expanded", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(ShowcaseScene.DEADLINE).assertIsDisplayed()
    }

    @Test
    fun SV3_010_forbid() {
        host()
        composeRule.onNodeWithTag("scene-calendar-confirm").assertIsNotEnabled()
        composeRule.onNodeWithText(ShowcaseScene.FORBID_REASON).assertIsDisplayed()
    }

    @Test
    fun SV3_011_glass_level_catalog() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ShowcaseApp(formFactor = "desktop", initialLevel = ShowcaseLevel.GLASS, staticChrome = true)
        }
        pump(4)
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.dinner-contradicted").assertIsDisplayed()
    }

    @Test
    fun SV3_012_freeze() {
        val proc = ProcessBuilder(
            "git", "diff", "--stat", "--",
            "core/", "broker/", "agent/", "conformance/a3ui/"
        ).directory(root).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor())
        assertTrue(out.isBlank(), out)
        val review = File(root, "REVIEW_SHOWCASE_V3.md").readText()
        assertTrue(review.contains("si capisce in silenzio"))
        assertTrue(File(assets, "scena-completa.png").length() > 0)
        assertTrue(File(assets, "showcase-v3-android.mp4").length() > 8_000)
    }

    @Test
    fun SV3_dump_assets() {
        val frames = File(assets, "mac-frames")
        frames.deleteRecursively()
        frames.mkdirs()
        val namedDir = File(assets, "mac")
        namedDir.mkdirs()
        var index = 0
        fun frame(named: String?) {
            val image = composeRule.onNodeWithTag("showcase-host").captureToImage().toAwtImage()
            if (named != null) {
                ImageIO.write(image, "png", File(namedDir, named))
            }
            ImageIO.write(image, "png", File(frames, String.format("%03d.png", index)))
            index++
        }
        host(ambient = true)
        frame("scena-completa.png")
        composeRule.onNodeWithTag("trigger-ambient").performClick(); pump(4)
        frame("provenance.png")
        composeRule.onNodeWithTag("toggle-reduced").performClick(); pump(4)
        frame("reduced-motion.png")
        composeRule.onNodeWithTag("toggle-blur").performClick(); pump(4)
        frame("blur-off.png")
        composeRule.onNodeWithTag("toggle-silent").performClick(); pump(4)
        frame(null)
        val ffmpeg = File(System.getProperty("user.home"), ".local/bin/ffmpeg")
        val bin = if (ffmpeg.exists()) ffmpeg.absolutePath else "ffmpeg"
        val mp4 = File(assets, "showcase-v3-mac.mp4")
        val input = frames.absolutePath + File.separator + "%03d.png"
        val proc = ProcessBuilder(
            bin, "-y", "-framerate", "1", "-start_number", "0", "-i", input,
            "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
            "-pix_fmt", "yuv420p", "-an", mp4.absolutePath
        ).redirectErrorStream(true).start()
        val log = proc.inputStream.bufferedReader().readText()
        assertTrue(proc.waitFor() == 0, log)
        assertTrue(mp4.length() > 4_000, "v3 mac video empty")
        mp4.copyTo(File(namedDir, "showcase-v3-mac.mp4"), overwrite = true)
        frames.deleteRecursively()
        assertTrue(File(namedDir, "scena-completa.png").length() > 0)
        assertTrue(File(namedDir, "reduced-motion.png").length() > 0)
        assertTrue(File(namedDir, "blur-off.png").length() > 0)
        assertTrue(File(namedDir, "provenance.png").length() > 0)
    }
}
