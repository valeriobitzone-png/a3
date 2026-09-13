package a3.showcase

import a3.a3ui.a11y.MarkKind
import a3.a3ui.a11y.SpokenLaw
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class A11yAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<CaptureActivity>()

    private val root = File("../..")
    private val shots = File(root, "review-assets/a11y/android")

    private fun exists(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun host(
        fixtures: List<A11yFixture>,
        reduced: Boolean = false,
        highContrast: Boolean = false,
        caption: String? = null
    ) {
        composeRule.setContent {
            Box(Modifier.size(411.dp, 891.dp)) {
                ShowcaseA11yHost(
                    fixtures = fixtures,
                    reducedMotion = reduced,
                    highContrast = highContrast,
                    caption = caption
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun capture(name: String, fixtures: List<A11yFixture>, reduced: Boolean = false, highContrast: Boolean = false) {
        val caption = if (fixtures.size == 1) "LETTURA: ${fixtures.single().reading()}" else null
        host(fixtures, reduced, highContrast, caption)
        shots.mkdirs()
        val view = composeRule.activity.findViewById<View>(android.R.id.content)
        val width = if (view.width > 0) view.width else 411
        val height = if (view.height > 0) view.height else 891
        if (view.width <= 0 || view.height <= 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val png = File(shots, "$name.png")
        FileOutputStream(png).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        writeA11yTree(File(shots, "$name.tree.json"), composeRule.onNodeWithTag("a11y-host").fetchSemanticsNode())
        File(shots, "$name.spoken.txt").writeText(fixtures.joinToString("\n") { it.reading() })
        assertTrue(png.length() > 800, "empty $name.png")
    }

    private fun ctaSpoken(id: String): String {
        val node = composeRule.onNodeWithTag("a11y-cta-$id").fetchSemanticsNode()
        val state = node.config.getOrNull(SemanticsProperties.StateDescription) ?: ""
        val content = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: ""
        return "$state $content"
    }

    private fun assertReading(fixture: A11yFixture) {
        val spoken = ctaSpoken(fixture.id)
        SpokenLaw.requireNotSensoryGloss(spoken)
        assertFalse(spoken.lowercase().contains("dimmed"), spoken)
        assertFalse(spoken.lowercase().contains("bottone dimmed"), spoken)
        val reading = fixture.reading()
        assertTrue(spoken.contains(fixture.mark.wire()) || spoken.contains(reading.takeWhile { it != ',' }), spoken)
        when (fixture.mark) {
            MarkKind.CONTRADICTED -> {
                assertTrue(spoken.contains("contradicted"), spoken)
                assertTrue(spoken.contains("conferma disabilitata") || spoken.contains("forbidden"), spoken)
                assertTrue(spoken.contains("resolve conflict first"), spoken)
            }
            MarkKind.STALE -> {
                assertTrue(spoken.contains("stale 2 ore"), spoken)
                assertTrue(spoken.contains("warning"), spoken)
            }
            MarkKind.PENDING -> {
                assertTrue(spoken.contains("in verifica"), spoken)
                assertTrue(spoken.contains("slot riservato") || spoken.contains("reserved"), spoken)
            }
            MarkKind.FACT -> {
                assertTrue(spoken.contains("FACT") || spoken.contains("conferma abilitata"), spoken)
                assertFalse(spoken.contains("stale 2 ore") && spoken.contains("contradicted"))
            }
            MarkKind.UNKNOWN, MarkKind.HELD -> {
                assertTrue(spoken.contains("conferma disabilitata") || spoken.contains("forbidden"), spoken)
            }
        }
        val described = composeRule.onNodeWithTag("a11y-cta-${fixture.id}")
            .fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.StateDescription)
        assertFalse(described.isNullOrBlank(), "missing stateDescription on ${fixture.id}")
        assertTrue(described!!.contains(fixture.mark.wire()), described)
    }

    @Test
    fun AX_001_android_talkback_contradicted_full_reading() {
        capture("CONTRADICTED", listOf(ShowcaseA11y.contradicted))
        composeRule.onNodeWithTag("a11y-cta-calendar").assertIsNotEnabled()
        composeRule.onNodeWithText(SpokenLaw.FORBID_REASON).assertIsDisplayed()
        assertReading(ShowcaseA11y.contradicted)
        println("PASS AX-001 android TalkBack CONTRADICTED")
    }

    @Test
    fun AX_003_android_talkback_stale_age_and_warning() {
        capture("STALE", listOf(ShowcaseA11y.stale))
        composeRule.onNodeWithTag("a11y-cta-hotel").assertIsEnabled()
        composeRule.onNodeWithTag("stale-warning").assertIsDisplayed()
        assertReading(ShowcaseA11y.stale)
        println("PASS AX-003 android TalkBack STALE")
    }

    @Test
    fun AX_005_android_talkback_pending_reserved_slot() {
        capture("PENDING", listOf(ShowcaseA11y.pending))
        composeRule.onNodeWithTag("a11y-cta-train").assertIsNotEnabled()
        composeRule.onNodeWithTag("pending-slot").assertIsDisplayed()
        composeRule.onNodeWithText(ShowcaseScene.PENDING_LABEL).assertIsDisplayed()
        assertFalse(exists("pending-spinner"))
        assertReading(ShowcaseA11y.pending)
        println("PASS AX-005 android TalkBack PENDING")
    }

    @Test
    fun AX_007_android_talkback_fact_baseline() {
        capture("FACT", listOf(ShowcaseA11y.fact))
        composeRule.onNodeWithTag("a11y-cta-flight").assertIsEnabled()
        composeRule.onNodeWithTag("fact-baseline").assertIsDisplayed()
        assertFalse(exists("stale-warning"))
        assertFalse(exists("contradicted-badge"))
        assertReading(ShowcaseA11y.fact)
        println("PASS AX-007 android TalkBack FACT")
    }

    @Test
    fun AX_009_android_reduced_motion_keeps_marks() {
        capture("reduced-motion", ShowcaseA11y.lawful(), reduced = true)
        composeRule.onNodeWithTag("motion-zero").assertIsDisplayed()
        composeRule.onNodeWithTag("stale-warning").assertIsDisplayed()
        composeRule.onNodeWithTag("contradicted-badge").assertIsDisplayed()
        composeRule.onNodeWithTag("pending-label").assertIsDisplayed()
        composeRule.onNodeWithTag("fact-baseline").assertIsDisplayed()
        println("PASS AX-009 android reduced motion")
    }

    @Test
    fun AX_011_android_high_contrast_marks_without_color() {
        capture("high-contrast", ShowcaseA11y.lawful(), highContrast = true)
        composeRule.onNodeWithTag("high-contrast-on").assertIsDisplayed()
        for (fixture in ShowcaseA11y.lawful()) {
            composeRule.onNodeWithTag("mark-pattern-${fixture.id}").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("stale-warning").assertIsDisplayed()
        composeRule.onNodeWithTag("contradicted-badge").assertIsDisplayed()
        println("PASS AX-011 android high contrast")
    }

    @Test
    fun AX_013_android_keyboard_focus_order() {
        host(ShowcaseA11y.lawful())
        val order = collectFocusOrder(composeRule.onNodeWithTag("a11y-host").fetchSemanticsNode())
        assertTrue(order.first() == "skip-to-marks" || order.contains("skip-to-marks"), order.toString())
        val skipIndex = order.indexOf("skip-to-marks")
        val hotel = order.indexOf("fixture-hotel")
        val calendar = order.indexOf("fixture-calendar")
        val train = order.indexOf("fixture-train")
        assertTrue(skipIndex >= 0 && hotel > skipIndex, order.toString())
        assertTrue(calendar > hotel || train > hotel, order.toString())
        println("PASS AX-013 android focus order $order")
    }

    @Test
    fun AX_015_android_touch_targets_48dp() {
        host(ShowcaseA11y.lawful())
        val minPx = SpokenLaw.ANDROID_MIN_DP
        for (fixture in ShowcaseA11y.lawful()) {
            val size = composeRule.onNodeWithTag("a11y-cta-${fixture.id}").fetchSemanticsNode().size
            assertTrue(size.width >= minPx, "${fixture.id} width=${size.width}")
            assertTrue(size.height >= minPx, "${fixture.id} height=${size.height}")
        }
        println("PASS AX-015 android touch targets")
    }

    @Test
    fun AX_017_android_state_description_on_every_mark() {
        host(ShowcaseA11y.everyMark())
        val tree = dumpA11yTree(composeRule.onNodeWithTag("a11y-host").fetchSemanticsNode())
        shots.mkdirs()
        File(shots, "every-mark.tree.json").writeText(tree)
        for (fixture in ShowcaseA11y.everyMark()) {
            assertReading(fixture)
        }
        println("PASS AX-017 android every mark stateDescription")
    }

    @Test
    fun AX_019_android_frozen_trees_empty() {
        val proc = ProcessBuilder(
            "git", "diff", "--stat", "--",
            "core/", "broker/", "agent/", "launcher/", "adapters/", "conformance/a3ui/"
        ).directory(root).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        assertEquals(0, proc.waitFor())
        assertEquals("", out, "frozen tree diff not empty:\n$out")
        println("PASS AX-019 android freeze")
    }
}
