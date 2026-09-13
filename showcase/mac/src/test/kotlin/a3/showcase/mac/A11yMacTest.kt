package a3.showcase.mac

import a3.a3ui.a11y.MarkKind
import a3.a3ui.a11y.SpokenLaw
import a3.showcase.A11yFixture
import a3.showcase.ShowcaseA11y
import a3.showcase.ShowcaseA11yHost
import a3.showcase.ShowcaseScene
import a3.showcase.collectFocusOrder
import a3.showcase.dumpA11yTree
import a3.showcase.writeA11yTree
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class A11yMacTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val root = File("../..")
    private val shots = File(root, "review-assets/a11y/mac")

    @Before
    fun accessibilityOff() {
        System.setProperty("a3.reduce.motion", "false")
        System.setProperty("a3.high.contrast", "false")
    }

    private fun exists(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun host(
        fixtures: List<A11yFixture>,
        reduced: Boolean = false,
        highContrast: Boolean = false,
        caption: String? = null
    ) {
        composeRule.setContent {
            Box(Modifier.size(720.dp, 900.dp)) {
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
        val png = File(shots, "$name.png")
        val image = composeRule.onNodeWithTag("a11y-host").captureToImage().toAwtImage()
        ImageIO.write(image, "png", png)
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
    fun AX_002_mac_voiceover_contradicted_full_reading() {
        capture("CONTRADICTED", listOf(ShowcaseA11y.contradicted))
        composeRule.onNodeWithTag("a11y-cta-calendar").assertIsNotEnabled()
        composeRule.onNodeWithText(SpokenLaw.FORBID_REASON).assertIsDisplayed()
        assertReading(ShowcaseA11y.contradicted)
        println("PASS AX-002 mac VoiceOver CONTRADICTED")
    }

    @Test
    fun AX_004_mac_voiceover_stale_age_and_warning() {
        capture("STALE", listOf(ShowcaseA11y.stale))
        composeRule.onNodeWithTag("a11y-cta-hotel").assertIsEnabled()
        composeRule.onNodeWithTag("stale-warning").assertIsDisplayed()
        assertReading(ShowcaseA11y.stale)
        println("PASS AX-004 mac VoiceOver STALE")
    }

    @Test
    fun AX_006_mac_voiceover_pending_reserved_slot() {
        capture("PENDING", listOf(ShowcaseA11y.pending))
        composeRule.onNodeWithTag("a11y-cta-train").assertIsNotEnabled()
        composeRule.onNodeWithTag("pending-slot").assertIsDisplayed()
        composeRule.onNodeWithText(ShowcaseScene.PENDING_LABEL).assertIsDisplayed()
        assertFalse(exists("pending-spinner"))
        assertReading(ShowcaseA11y.pending)
        println("PASS AX-006 mac VoiceOver PENDING")
    }

    @Test
    fun AX_008_mac_voiceover_fact_baseline() {
        capture("FACT", listOf(ShowcaseA11y.fact))
        composeRule.onNodeWithTag("a11y-cta-flight").assertIsEnabled()
        composeRule.onNodeWithTag("fact-baseline").assertIsDisplayed()
        assertReading(ShowcaseA11y.fact)
        println("PASS AX-008 mac VoiceOver FACT")
    }

    @Test
    fun AX_010_mac_reduced_motion_keeps_marks() {
        capture("reduced-motion", ShowcaseA11y.lawful(), reduced = true)
        composeRule.onNodeWithTag("motion-zero").assertIsDisplayed()
        composeRule.onNodeWithTag("stale-warning").assertIsDisplayed()
        composeRule.onNodeWithTag("contradicted-badge").assertIsDisplayed()
        composeRule.onNodeWithTag("pending-label").assertIsDisplayed()
        composeRule.onNodeWithTag("fact-baseline").assertIsDisplayed()
        println("PASS AX-010 mac reduced motion")
    }

    @Test
    fun AX_012_mac_high_contrast_marks_without_color() {
        capture("high-contrast", ShowcaseA11y.lawful(), highContrast = true)
        composeRule.onNodeWithTag("high-contrast-on").assertIsDisplayed()
        for (fixture in ShowcaseA11y.lawful()) {
            composeRule.onNodeWithTag("mark-pattern-${fixture.id}").assertIsDisplayed()
        }
        println("PASS AX-012 mac high contrast")
    }

    @Test
    fun AX_014_mac_keyboard_focus_order_and_skip_link() {
        host(ShowcaseA11y.lawful())
        composeRule.onNodeWithTag("skip-to-marks").assertIsDisplayed()
        val order = collectFocusOrder(composeRule.onNodeWithTag("a11y-host").fetchSemanticsNode())
        assertTrue(order.contains("skip-to-marks"), order.toString())
        assertTrue(order.indexOf("skip-to-marks") < order.indexOf("fixture-hotel"), order.toString())
        File(shots, "focus-order.txt").apply {
            parentFile.mkdirs()
            writeText(order.joinToString("\n"))
        }
        println("PASS AX-014 mac skip link + focus $order")
    }

    @Test
    fun AX_016_mac_touch_targets_44pt() {
        host(ShowcaseA11y.lawful())
        val minPx = SpokenLaw.MAC_MIN_PT
        for (fixture in ShowcaseA11y.lawful()) {
            val size = composeRule.onNodeWithTag("a11y-cta-${fixture.id}").fetchSemanticsNode().size
            assertTrue(size.width >= minPx, "${fixture.id} width=${size.width}")
            assertTrue(size.height >= minPx, "${fixture.id} height=${size.height}")
        }
        println("PASS AX-016 mac touch targets")
    }

    @Test
    fun AX_017_mac_state_description_on_every_mark() {
        host(ShowcaseA11y.everyMark())
        shots.mkdirs()
        File(shots, "every-mark.tree.json").writeText(
            dumpA11yTree(composeRule.onNodeWithTag("a11y-host").fetchSemanticsNode())
        )
        for (fixture in ShowcaseA11y.everyMark()) {
            assertReading(fixture)
        }
        println("PASS AX-017 mac every mark stateDescription")
    }

    @Test
    fun AX_019_mac_frozen_trees_empty() {
        val proc = ProcessBuilder(
            "git", "diff", "--stat", "--",
            "core/", "broker/", "agent/", "launcher/", "overlay/", "adapters/", "conformance/a3ui/"
        ).directory(root).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        assertEquals(0, proc.waitFor())
        assertEquals("", out, "frozen tree diff not empty:\n$out")
        println("PASS AX-019 mac freeze")
    }
}
