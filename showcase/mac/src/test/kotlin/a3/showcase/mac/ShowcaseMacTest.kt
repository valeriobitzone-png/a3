package a3.showcase.mac

import a3.showcase.LoggingSink
import a3.showcase.ShowcaseJournal
import a3.showcase.ShowcaseLevel
import a3.showcase.ShowcaseSensory
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.io.File
import javax.imageio.ImageIO
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class ShowcaseMacTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun accessibilityOff() {
        System.setProperty("a3.reduce.motion", "false")
        System.setProperty("a3.high.contrast", "false")
    }

    private fun pump(frames: Int = 3) {
        repeat(frames) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun host(
        level: ShowcaseLevel = ShowcaseLevel.ALL,
        reduced: Boolean = false,
        talkback: Boolean = false,
        journal: ShowcaseJournal = ShowcaseJournal()
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ShowcaseApp(
                formFactor = "desktop",
                initialLevel = level,
                initialReduced = reduced,
                initialTalkback = talkback,
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
    fun SC_001_all_levels_together() {
        val journal = ShowcaseJournal()
        host(ShowcaseLevel.ALL, journal = journal)
        composeRule.onNodeWithTag("showcase-host").assertIsDisplayed()
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
        assertTrue(exists("glass-surface") || exists("glass-fallback"))
        composeRule.onNodeWithTag("text_cal.hotel-held").assertIsDisplayed()
        composeRule.onNodeWithTag("animated-gradient").assertIsDisplayed()
        composeRule.onNodeWithTag("ambient-indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("glass-optics", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("particle-idle", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(journal.haptic().any { it.emitted })
    }

    @Test
    fun SC_002_level_isolation() {
        host(ShowcaseLevel.GLASS)
        assertTrue(exists("glass-surface") || exists("glass-fallback"))
        composeRule.onNodeWithTag("showcase-wallpaper").assertIsDisplayed()
        composeRule.onNodeWithTag("animated-gradient").assertDoesNotExist()

        composeRule.onNodeWithTag("nav-shaders").performClick()
        pump(4)
        composeRule.onNodeWithTag("animated-gradient").assertIsDisplayed()
        composeRule.onNodeWithTag("glass-optics", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithTag("nav-sensory").performClick()
        pump(4)
        composeRule.onNodeWithTag("glass-optics", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("animated-gradient").assertDoesNotExist()

        composeRule.onNodeWithTag("nav-all").performClick()
        pump(4)
        composeRule.onNodeWithTag("showcase-level-all", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun SC_005_reduced_zeros() {
        val journal = ShowcaseJournal()
        host(ShowcaseLevel.ALL, reduced = true, journal = journal)
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
        assertTrue(journal.audio().all { !it.emitted })
        assertTrue(journal.haptic().all { !it.emitted })
    }

    @Test
    fun SC_006_talkback() {
        val journal = ShowcaseJournal()
        host(ShowcaseLevel.ALL, talkback = true, journal = journal)
        val phrases = journal.announce().map { it.phrase }
        assertTrue(phrases.contains("pending review"), phrases.toString())
        assertTrue(phrases.contains("contradicted"), phrases.toString())
    }

    @Test
    fun SC_007_mapping() {
        assertTrue(ShowcaseSensory.emitPulse(ShowcaseSensory.Cause.HELD, reducedMotion = true, engine = true) == null)
        assertTrue(ShowcaseSensory.emitTone(ShowcaseSensory.Cause.CONFIRM, reducedMotion = false, silent = false) != null)
        val journal = ShowcaseJournal()
        host(ShowcaseLevel.ALL, journal = journal)
        composeRule.onNodeWithTag("trigger-sensory").performClick()
        pump(4)
        LoggingSink(journal).playHaptic(ShowcaseSensory.Cause.CONFIRM, reduced = false, engine = true)
        assertTrue(journal.haptic().any { it.cause == ShowcaseSensory.Cause.CONFIRM && it.emitted })
    }

    @Test
    fun SV_001_diagnosis() {
        val root = File("../..")
        val glass = File(root, "renderers/mac-compose/src/main/kotlin/a3/renderers/mac/compose/MacGlassSurface.kt").readText()
        assertTrue(glass.contains(a3.showcase.ShowcaseDiagnosis.WHITE_LAYER))
        val review = File(root, "REVIEW_SHOWCASE_V2.md").readText()
        assertTrue(review.contains(a3.showcase.ShowcaseDiagnosis.CAUSE))
    }

    @Test
    fun SV_002_wallpaper() {
        host(ShowcaseLevel.GLASS)
        composeRule.onNodeWithTag("showcase-wallpaper").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-axis").performClick(); pump(4)
        composeRule.onNodeWithTag("showcase-wallpaper").assertIsDisplayed()
    }

    @Test
    fun SV_003_blur_and_gutter() {
        val sharp = a3.showcase.ShowcaseWallpaper.fill(96, 64, 8, false)
        val blur = a3.showcase.ShowcaseWallpaper.fill(96, 64, 8, true)
        val eSharp = a3.showcase.ShowcaseGlassMath.edgeEnergy(sharp, 96, 64)
        val eBlur = a3.showcase.ShowcaseGlassMath.edgeEnergy(blur, 96, 64)
        assertTrue(eBlur / eSharp < 0.75f, "sharp=$eSharp blur=$eBlur")
        host(ShowcaseLevel.GLASS)
        composeRule.onNodeWithTag("showcase-wallpaper-gutter", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun SV_004_highlight() {
        host(ShowcaseLevel.GLASS)
        assertTrue(composeRule.onAllNodesWithTag("showcase-glass-highlight", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag("showcase-glass-plate", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val src = File("src/main/kotlin/a3/showcase/mac/ShowcaseGlass.kt").readText()
        assertTrue(src.contains("frost = true"))
        assertTrue(!src.contains("0.62f"))
    }

    @Test
    fun SV_005_badges() {
        host(ShowcaseLevel.GLASS)
        composeRule.onNodeWithTag("text_cal.hotel-held", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.dinner-contradicted").assertIsDisplayed()
    }

    @Test
    fun SC_004_mac_screenshots() {
        val assets = File("../../review-assets/showcase").absoluteFile
        val dir = File(assets, "mac")
        val frames = File(assets, "mac-frames")
        dir.mkdirs()
        frames.deleteRecursively()
        frames.mkdirs()
        var index = 0
        host(ShowcaseLevel.ALL)
        index = dumpFrame(dir, frames, index, "all.png")
        composeRule.onNodeWithTag("trigger-modal").performClick(); pump(4)
        index = dumpFrame(dir, frames, index, null)
        composeRule.onNodeWithTag("trigger-modal").performClick(); pump(4)
        index = dumpFrame(dir, frames, index, null)
        composeRule.onNodeWithTag("trigger-ambient").performClick(); pump(4)
        index = dumpFrame(dir, frames, index, null)
        composeRule.onNodeWithTag("trigger-sensory").performClick(); pump(4)
        index = dumpFrame(dir, frames, index, null)
        for (level in listOf("glass", "axis", "motion", "dynamic", "shaders", "sensory")) {
            composeRule.onNodeWithTag("nav-$level").performClick()
            pump(4)
            index = dumpFrame(dir, frames, index, "$level.png")
        }
        composeRule.onNodeWithTag("toggle-reduced").performClick(); pump(4)
        index = dumpFrame(dir, frames, index, null)
        composeRule.onNodeWithTag("toggle-talkback").performClick(); pump(4)
        index = dumpFrame(dir, frames, index, null)
        composeRule.onNodeWithTag("nav-all").performClick(); pump(4)
        dumpFrame(dir, frames, index, null)
        val ffmpeg = File(System.getProperty("user.home"), ".local/bin/ffmpeg")
        val bin = if (ffmpeg.exists()) ffmpeg.absolutePath else "ffmpeg"
        val mp4 = File(assets, "showcase-mac.mp4")
        val input = frames.absolutePath + File.separator + "%03d.png"
        val proc = ProcessBuilder(
            bin, "-y", "-framerate", "2", "-start_number", "0", "-i", input,
            "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
            "-pix_fmt", "yuv420p", "-an", mp4.absolutePath
        ).redirectErrorStream(true).start()
        val log = proc.inputStream.bufferedReader().readText()
        assertTrue(proc.waitFor() == 0, log)
        assertTrue(mp4.length() > 8_000, "mac video empty")
        frames.deleteRecursively()
    }

    private fun dumpFrame(named: File, frames: File, index: Int, name: String?): Int {
        val image = composeRule.onNodeWithTag("showcase-host").captureToImage().toAwtImage()
        if (name != null) {
            val file = File(named, name)
            ImageIO.write(image, "png", file)
            assertTrue(file.length() > 0, name)
        }
        val numbered = File(frames, String.format("%03d.png", index))
        ImageIO.write(image, "png", numbered)
        return index + 1
    }
}

