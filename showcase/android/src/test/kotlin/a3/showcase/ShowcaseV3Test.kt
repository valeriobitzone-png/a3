package a3.showcase

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ShowcaseV3Test {
    @get:Rule
    val composeRule = createComposeRule()

    private val root = File("../..")
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
                formFactor = "phone",
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
    fun SV3_001_three_surfaces_simultaneous() {
        host()
        composeRule.onNodeWithTag("scene-intent").assertIsDisplayed()
        composeRule.onNodeWithText(ShowcaseScene.INTENT).assertIsDisplayed()
        composeRule.onNodeWithTag("scene-train").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-hotel").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-calendar").assertIsDisplayed()
        assertEquals(ShowcaseScene.State.PENDING, ShowcaseScene.train().state)
        assertEquals(ShowcaseScene.State.STALE, ShowcaseScene.hotel().state)
        assertEquals(ShowcaseScene.State.CONTRADICTED, ShowcaseScene.calendar().state)
    }

    @Test
    fun SV3_002_pending_reserved_slot_stable_no_spinner() {
        host()
        composeRule.onNodeWithTag("scene-train-outline").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-train-label").assertIsDisplayed()
        composeRule.onNodeWithText(ShowcaseScene.PENDING_LABEL).assertIsDisplayed()
        assertTrue(!exists("scene-train-spinner"))
        assertTrue(!exists("scene-train-price"))
        val ui = File("../shared/ShowcaseSceneUi.kt").readText()
        assertTrue(!ShowcaseScene.hasSpinner(ui), ui.take(80))
        assertTrue(!ui.contains("CircularProgressIndicator"))
        val train = composeRule.onNodeWithTag("scene-train").fetchSemanticsNode()
        val hotel = composeRule.onNodeWithTag("scene-hotel").fetchSemanticsNode()
        val cal = composeRule.onNodeWithTag("scene-calendar").fetchSemanticsNode()
        assertTrue(abs(train.size.height - hotel.size.height) < 4f, "train=${train.size.height} hotel=${hotel.size.height}")
        assertTrue(abs(hotel.size.height - cal.size.height) < 4f, "hotel=${hotel.size.height} cal=${cal.size.height}")
    }

    @Test
    fun SV3_003_stale_age_visible_not_just_bit() {
        host()
        composeRule.onNodeWithTag("scene-hotel-price").assertIsDisplayed()
        composeRule.onNodeWithText(ShowcaseScene.HOTEL_PRICE).assertIsDisplayed()
        composeRule.onNodeWithTag("scene-hotel-stale").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-hotel-age").assertIsDisplayed()
        composeRule.onNodeWithText("2h fa").assertIsDisplayed()
        assertEquals("2h fa", ShowcaseScene.ageLabel(ShowcaseScene.HOTEL_AGE_MS))
        assertEquals("ora", ShowcaseScene.ageLabel(0))
        val decay = ShowcaseScene.decay(ShowcaseScene.HOTEL_AGE_MS)
        assertTrue(decay > 0f && decay < 1f, "decay=$decay")
        composeRule.onNodeWithTag("scene-hotel-decay").assertIsDisplayed()
    }

    @Test
    fun SV3_004_contradicted_strikethrough_and_disabled_confirm() {
        host()
        composeRule.onNodeWithTag("scene-calendar-slot-0900").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-calendar-slot-0930").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-calendar-badge").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-calendar-confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("scene-calendar-reason").assertIsDisplayed()
        composeRule.onNodeWithText(ShowcaseScene.FORBID_REASON).assertIsDisplayed()
        val ui = File("../shared/ShowcaseSceneUi.kt").readText()
        assertTrue(ui.contains("TextDecoration.LineThrough"), ui)
        assertTrue(ui.contains(ShowcaseScene.FORBID_REASON) || File("../shared/ShowcaseScene.kt").readText().contains(ShowcaseScene.FORBID_REASON))
    }

    @Test
    fun SV3_005_provenance_on_every_surface() {
        host()
        for (surface in ShowcaseScene.surfaces()) {
            composeRule.onNodeWithTag("scene-${surface.id}-provenance").assertIsDisplayed()
            composeRule.onNodeWithTag("scene-${surface.id}-provenance-icon", useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithTag("scene-${surface.id}-provenance-label").assertIsDisplayed()
        }
        composeRule.onNodeWithText("API firmata").assertIsDisplayed()
        composeRule.onNodeWithText("modello").assertIsDisplayed()
        composeRule.onNodeWithText("inferita").assertIsDisplayed()
    }

    @Test
    fun SV3_006_reduced_motion_keeps_marks() {
        host(reduced = true)
        composeRule.onNodeWithTag("scene-train-label").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-hotel-stale").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-calendar-reason").assertIsDisplayed()
        composeRule.onNodeWithText("2h fa").assertIsDisplayed()
        composeRule.onNodeWithText("API firmata").assertIsDisplayed()
    }

    @Test
    fun SV3_007_blur_off_keeps_marks() {
        host(glass = false)
        composeRule.onNodeWithTag("scene-blur-off", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("scene-train-label").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-hotel-stale").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-calendar-reason").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle-blur").performClick()
        pump(4)
        composeRule.onNodeWithTag("scene-frost-on", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun SV3_008_silence_keeps_comprehension() {
        val journal = ShowcaseJournal()
        host(silent = true, journal = journal)
        composeRule.onNodeWithTag("scene-train-label").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-hotel-age").assertIsDisplayed()
        composeRule.onNodeWithTag("scene-calendar-reason").assertIsDisplayed()
        assertTrue(journal.audio().isNotEmpty())
        assertTrue(journal.audio().all { !it.emitted })
        assertTrue(journal.haptic().all { !it.emitted })
    }

    @Test
    fun SV3_009_ambient_expanded_deadline() {
        host(ambient = true)
        composeRule.onNodeWithTag("ambient-expanded", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("ambient-deadline").assertIsDisplayed()
        composeRule.onNodeWithText(ShowcaseScene.DEADLINE).assertIsDisplayed()
        composeRule.onNodeWithTag("scene-train-label").assertIsDisplayed()
        val ambient = composeRule.onNodeWithTag("ambient-indicator").fetchSemanticsNode()
        val label = composeRule.onNodeWithTag("scene-train-label").fetchSemanticsNode()
        assertTrue(
            label.boundsInRoot.top >= ambient.boundsInRoot.bottom - 2f,
            "ambient=${ambient.boundsInRoot} label=${label.boundsInRoot}"
        )
    }

    @Test
    fun SV3_010_forbidden_action_reason_visible() {
        host()
        composeRule.onNodeWithTag("scene-calendar-confirm").assertIsNotEnabled()
        composeRule.onNodeWithText(ShowcaseScene.FORBID_REASON).assertIsDisplayed()
        assertTrue(!ShowcaseScene.calendar().confirmEnabled)
    }

    @Test
    fun SV3_011_regression_catalog_levels_still_work() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ShowcaseApp(
                formFactor = "phone",
                initialLevel = ShowcaseLevel.GLASS,
                staticChrome = true
            )
        }
        pump(4)
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.hotel-held", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.dinner-contradicted").assertIsDisplayed()
        composeRule.onNodeWithTag("showcase-wallpaper").assertIsDisplayed()
    }

    @Test
    fun SV3_012_freeze_only_showcase() {
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
            "core/", "broker/", "agent/", "launcher/", "adapters/",
            "prediction/", "projection/", "intent-model/",
            "conformance/a3ui/"
        )
        assertTrue(frozen.isBlank(), frozen)
        val status = ProcessBuilder("git", "status", "--porcelain")
            .directory(root).redirectErrorStream(true).start()
        val porcelain = status.inputStream.bufferedReader().readText()
        assertEquals(0, status.waitFor())
        val allowed = listOf(
            "showcase/",
            "a3ui/",
            "renderers/android-compose/",
            "renderers/mac-compose/",
            "settings.gradle.kts",
            "REVIEW_SHOWCASE_V3.md",
            "REVIEW_SHOWCASE_V2.md",
            "REVIEW_SHOWCASE.md",
            "REVIEW_REAL_OVERLAY.md",
            "REVIEW_OVERLAY_LIFECYCLE.md",
            "REVIEW_A3UI_A11Y.md",
            "REVIEW_A3UI_PERF.md",
            "docs/",
            "overlay/",
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
        val review = File(root, "REVIEW_SHOWCASE_V3.md").readText()
        assertTrue(review.contains("si capisce in silenzio"), review.take(200))
        val dir = File(root, "review-assets/showcase-v3")
        for (name in listOf("scena-completa.png", "reduced-motion.png", "blur-off.png", "provenance.png")) {
            assertTrue(File(dir, name).length() > 0, "missing $name")
        }
        assertTrue(File(dir, "showcase-v3-android.mp4").length() > 8_000)
        assertTrue(File(dir, "showcase-v3-mac.mp4").length() > 4_000)
        assertTrue(ShowcaseChromeMath.expanded().w > ShowcaseChromeMath.collapsed().w)
    }
}
