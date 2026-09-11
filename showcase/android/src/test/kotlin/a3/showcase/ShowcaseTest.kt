package a3.showcase

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.io.File
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ShowcaseTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val root = File("../..")
    private val assets = File(root, "review-assets/showcase")

    private fun pump(frames: Int = 3) {
        repeat(frames) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun host(
        level: ShowcaseLevel = ShowcaseLevel.ALL,
        reduced: Boolean = false,
        talkback: Boolean = false,
        journal: ShowcaseJournal = ShowcaseJournal(),
        sink: ShowcaseSink = LoggingSink(journal)
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ShowcaseApp(
                formFactor = "phone",
                initialLevel = level,
                initialReduced = reduced,
                initialTalkback = talkback,
                staticChrome = true,
                journal = journal,
                sink = sink
            )
        }
        pump(4)
    }

    private fun exists(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun go(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        pump(4)
    }

    @Test
    fun SC_001_all_levels_together() {
        val journal = ShowcaseJournal()
        host(ShowcaseLevel.ALL, journal = journal)
        composeRule.onNodeWithTag("showcase-host").assertIsDisplayed()
        composeRule.onNodeWithTag("showcase-level-all", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
        assertTrue(exists("glass-surface") || exists("glass-fallback"))
        composeRule.onNodeWithTag("text_cal.hotel-held", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.dinner-contradicted").assertIsDisplayed()
        assertTrue(exists("text_cal.hotel-motion-pulse") || exists("text_cal.hotel-motion-pulse"))
        composeRule.onNodeWithTag("animated-gradient").assertIsDisplayed()
        composeRule.onNodeWithTag("ambient-indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("glass-optics", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("particle-idle", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("parallax-layers", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(journal.audio().isNotEmpty())
        assertTrue(journal.haptic().isNotEmpty())
        assertTrue(journal.haptic().any { it.emitted })
    }

    @Test
    fun SC_002_level_isolation() {
        host(ShowcaseLevel.GLASS)
        assertTrue(exists("glass-surface") || exists("glass-fallback"))
        composeRule.onNodeWithTag("animated-gradient").assertDoesNotExist()
        composeRule.onNodeWithTag("ambient-indicator").assertDoesNotExist()
        composeRule.onNodeWithTag("glass-optics", useUnmergedTree = true).assertDoesNotExist()

        go("nav-axis")
        composeRule.onNodeWithTag("text_cal.hotel-held", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.hotel-low").assertIsDisplayed()
        composeRule.onNodeWithTag("animated-gradient").assertDoesNotExist()

        go("nav-motion")
        assertTrue(exists("text_cal.hotel-motion-pulse"))
        composeRule.onNodeWithTag("animated-gradient").assertDoesNotExist()

        go("nav-dynamic")
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
        composeRule.onNodeWithTag("animated-gradient").assertDoesNotExist()

        go("nav-shaders")
        composeRule.onNodeWithTag("animated-gradient").assertIsDisplayed()
        composeRule.onNodeWithTag("ambient-indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("parallax-layers", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("glass-optics", useUnmergedTree = true).assertDoesNotExist()

        go("nav-sensory")
        composeRule.onNodeWithTag("glass-optics", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("particle-idle", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("animated-gradient").assertDoesNotExist()
        composeRule.onNodeWithTag("ambient-indicator").assertDoesNotExist()

        go("nav-all")
        composeRule.onNodeWithTag("showcase-level-all", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("animated-gradient").assertIsDisplayed()
    }

    @Test
    fun SC_003_real_videos() {
        assertVideo(File(assets, "showcase-android.mp4"))
        assertVideo(File(assets, "showcase-mac.mp4"))
    }

    @Test
    fun SC_004_screenshot_gallery() {
        val names = listOf("all.png", "glass.png", "axis.png", "motion.png", "dynamic.png", "shaders.png", "sensory.png")
        for (name in names) {
            val android = File(assets, name)
            val mac = File(assets, "mac/$name")
            assertTrue(android.exists() && android.length() > 0, "missing android $name")
            assertTrue(mac.exists() && mac.length() > 0, "missing mac $name")
        }
    }

    @Test
    fun SC_005_reduced_motion_zeros_sensory_keeps_paint() {
        val journal = ShowcaseJournal()
        host(ShowcaseLevel.ALL, reduced = true, journal = journal)
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.hotel-held", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("animated-gradient").assertIsDisplayed()
        assertTrue(journal.audio().isNotEmpty())
        assertTrue(journal.haptic().isNotEmpty())
        assertTrue(journal.audio().all { !it.emitted })
        assertTrue(journal.haptic().all { !it.emitted })
        composeRule.onNodeWithTag("toggle-reduced").performClick()
        pump(4)
        composeRule.onNodeWithTag("showcase-status").assertIsDisplayed()
        assertTrue(journal.haptic().any { it.emitted } || journal.audio().any { it.emitted })
    }

    @Test
    fun SC_006_talkback_announces_critical() {
        val journal = ShowcaseJournal()
        host(ShowcaseLevel.ALL, talkback = true, journal = journal)
        val phrases = journal.announce().map { it.phrase }
        assertTrue(phrases.contains("pending review"), phrases.toString())
        assertTrue(phrases.contains("stale"), phrases.toString())
        assertTrue(phrases.contains("contradicted"), phrases.toString())
        composeRule.onNodeWithTag("toggle-talkback").performClick()
        pump(2)
        composeRule.onNodeWithTag("showcase-status").assertIsDisplayed()
    }

    @Test
    fun SC_007_audio_haptic_mapping_and_log() {
        for (cause in ShowcaseSensory.audioCauses()) {
            assertTrue(ShowcaseSensory.tone(cause).durationMs > 0)
            assertTrue(ShowcaseSensory.emitTone(cause, reducedMotion = true, silent = false) == null)
        }
        for (cause in ShowcaseSensory.hapticCauses()) {
            val p = ShowcaseSensory.pulse(cause)
            assertTrue(p.durationMs > 0)
            assertTrue(ShowcaseSensory.emitPulse(cause, reducedMotion = true, engine = true) == null)
        }
        val journal = ShowcaseJournal()
        val sink = LoggingSink(journal)
        host(ShowcaseLevel.ALL, journal = journal, sink = sink)
        composeRule.onNodeWithTag("trigger-sensory").performClick()
        pump(4)
        sink.playAudio(ShowcaseSensory.Cause.CONFIRM, reduced = false, silent = false)
        sink.playHaptic(ShowcaseSensory.Cause.CONFIRM, reduced = false, engine = true)
        sink.playHaptic(ShowcaseSensory.Cause.TAP, reduced = false, engine = true)
        val live = File(assets, "showcase-haptic.json")
        assertTrue(live.exists() && live.length() > 0, "missing live haptic log")
        val liveText = live.readText()
        assertTrue(liveText.contains("\"HELD\""), liveText)
        assertTrue(liveText.contains("\"CONTRADICTED\""), liveText)
        assertTrue(liveText.contains("\"CONFIRM\""), liveText)
        assertTrue(liveText.contains("\"reducedMotion\": true"), liveText)
        assertTrue(liveText.contains("\"emitted\": false"), liveText)
        assertTrue(journal.haptic().any { it.cause == ShowcaseSensory.Cause.CONFIRM && it.emitted })
        val mapped = ShowcaseSensory.causesOf(CalendarFixture.output("phone", false)).toSet()
        assertTrue(mapped.contains(ShowcaseSensory.Cause.HELD))
        assertTrue(mapped.contains(ShowcaseSensory.Cause.DONE))
        assertTrue(mapped.contains(ShowcaseSensory.Cause.CONTRADICTED))
        assertTrue(mapped.contains(ShowcaseSensory.Cause.UNKNOWN))
    }

    @Test
    fun SC_008_freeze_renderers_and_core() {
        fun diff(vararg paths: String): String {
            val proc = ProcessBuilder("git", "diff", "--stat", "--", *paths)
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals(0, proc.waitFor())
            return out
        }
        val frozen = diff(
            "core/", "core/json", "core/admission", "core/action",
            "a3ui/", "broker/", "launcher/", "adapters/",
            "prediction/", "projection/", "intent-model/"
        )
        assertTrue(frozen.isBlank(), frozen)
        val renderers = diff(
            "renderers/android-compose/",
            "renderers/mac-compose/",
            "renderers/android-core/"
        )
        assertTrue(renderers.isBlank(), renderers)
        val settings = File(root, "settings.gradle.kts").readText()
        assertTrue(settings.contains(":showcase"))
        assertTrue(settings.contains(":showcase:mac"))
    }

    private fun assertVideo(file: File) {
        assertTrue(file.exists() && file.length() > 8_000, "missing or empty ${file.path}")
        val ffmpeg = ffmpegBin()
        val probe = ProcessBuilder(
            ffmpeg, "-hide_banner", "-i", file.absolutePath
        ).redirectErrorStream(true).start()
        val text = probe.inputStream.bufferedReader().readText()
        probe.waitFor()
        assertTrue(text.contains("Duration:"), text)
        assertTrue(!text.contains("Duration: 00:00:00.00"), text)
        val match = Regex("""Duration: (\d+):(\d+):(\d+\.\d+)""").find(text)
        assertTrue(match != null, text)
        val seconds = match!!.groupValues[1].toInt() * 3600 +
            match.groupValues[2].toInt() * 60 +
            match.groupValues[3].toDouble()
        assertTrue(seconds > 0.5, "duration too short ${file.name}: $seconds")
        val a = File.createTempFile("sc-a", ".png")
        val b = File.createTempFile("sc-b", ".png")
        fun grab(at: Double, dest: File) {
            ProcessBuilder(
                ffmpeg, "-y", "-ss", String.format(Locale.US, "%.2f", at),
                "-i", file.absolutePath, "-vframes", "1", dest.absolutePath
            ).redirectErrorStream(true).start().waitFor()
        }
        grab(0.25, a)
        grab((seconds * 0.72).coerceAtLeast(0.8), b)
        assertTrue(a.length() > 0 && b.length() > 0, "could not extract frames from ${file.name}")
        assertTrue(!a.readBytes().contentEquals(b.readBytes()), "video frames identical ${file.name}")
        a.delete()
        b.delete()
    }

    private fun ffmpegBin(): String {
        val local = File(System.getProperty("user.home"), ".local/bin/ffmpeg")
        if (local.exists()) return local.absolutePath
        return "ffmpeg"
    }
}
