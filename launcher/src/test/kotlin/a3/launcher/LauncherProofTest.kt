package a3.launcher

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.engine.PresentationHash
import a3.a3ui.engine.SurfaceComposer
import a3.a3ui.model.EpistemicAction
import a3.a3ui.model.EpistemicFreshness
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.EpistemicSupport
import a3.core.time.SequentialIdGenerator
import a3.renderers.android.compose.Exposure
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.RenderedNode
import a3.renderers.android.core.model.RenderedOutput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.io.File
import java.util.ArrayList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LauncherProofTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val ids = listOf(
        "text_cal.meeting",
        "text_cal.flight",
        "text_cal.hotel",
        "text_cal.dinner"
    )

    private fun walk(node: RenderedNode): List<RenderedNode> {
        val out = ArrayList<RenderedNode>()
        out += node
        for (child in node.children) out += walk(child)
        return out
    }

    private fun items(output: RenderedOutput): List<RenderedNode> {
        val all = output.nodes.flatMap { walk(it) }
        return ids.map { id -> all.single { it.id == id } }
    }

    private fun digest(node: RenderedNode): String {
        val axis = node.axis
        return listOf(
            axis?.support?.wire(),
            axis?.freshness?.wire(),
            axis?.status?.wire(),
            axis?.action?.wire(),
            node.stateDescription
        ).joinToString("|")
    }

    @Test
    fun LP_001_uncertain_calendar_has_four_axes() {
        val vm = A3HostViewModel.uncertain()
        val output = vm.ui.value.output!!
        val nodes = items(output)
        val meeting = nodes[0]
        val flight = nodes[1]
        val hotel = nodes[2]
        val dinner = nodes[3]
        assertEquals(EpistemicSupport.HIGH, meeting.axis!!.support)
        assertEquals(EpistemicFreshness.FRESH, meeting.axis!!.freshness)
        assertEquals(EpistemicStatus.BELIEVED, meeting.axis!!.status)
        assertEquals(EpistemicAction.DONE, meeting.axis!!.action)
        assertEquals(EpistemicSupport.MEDIUM, flight.axis!!.support)
        assertEquals(EpistemicFreshness.AGING, flight.axis!!.freshness)
        assertEquals(EpistemicStatus.BELIEVED, flight.axis!!.status)
        assertEquals(EpistemicAction.PENDING, flight.axis!!.action)
        assertEquals(EpistemicSupport.LOW, hotel.axis!!.support)
        assertEquals(EpistemicFreshness.STALE, hotel.axis!!.freshness)
        assertEquals(EpistemicStatus.HELD, hotel.axis!!.status)
        assertEquals(EpistemicAction.UNKNOWN, hotel.axis!!.action)
        assertEquals(EpistemicSupport.MEDIUM, dinner.axis!!.support)
        assertEquals(EpistemicFreshness.FRESH, dinner.axis!!.freshness)
        assertEquals(EpistemicStatus.CONTRADICTED, dinner.axis!!.status)
        assertEquals(EpistemicAction.COMPENSATED, dinner.axis!!.action)
        val d1 = digest(meeting)
        assertNotEquals(d1, digest(flight))
        assertNotEquals(d1, digest(hotel))
        assertNotEquals(d1, digest(dinner))
        assertNotEquals(digest(flight), digest(hotel))
        assertNotEquals(digest(hotel), digest(dinner))
        composeRule.setContent {
            A3Screen(output, {}, false, false, {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Riunione 09:00 — Ada").assertIsDisplayed()
        composeRule.onNodeWithText("Volo 14:30 — FCO→LIN").assertIsDisplayed()
        composeRule.onNodeWithText("Hotel Milano").assertIsDisplayed()
        composeRule.onNodeWithText("Prenotazione ristorante").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.meeting-done").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.flight-pending").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.hotel-held").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.dinner-compensated").assertIsDisplayed()
    }

    @Test
    fun LP_002_view_model_composes_hash_and_state_description() {
        val presentation = DemoFixtures.uncertainCalendar()
        val facts = DemoFixtures.uncertainCalendarFacts()
        val tree = SurfaceComposer.compose(presentation, DemoFixtures.now, facts)
        val expected = PresentationHash.of(tree)
        val compiled = DeterministicA3UICompiler(DemoFixtures.now, SequentialIdGenerator())
            .compile(DemoFixtures.uncertainCalendarProjection(), presentation)
            .copy(nodes = tree.nodes, bindings = tree.bindings)
        val again = PresentationHash.of(compiled)
        assertEquals(expected, again)
        val vm = A3HostViewModel.uncertain()
        assertEquals(expected, vm.ui.value.presentationHash)
        assertNotNull(vm.ui.value.surface)
        val output = vm.ui.value.output!!
        assertTrue(expected.matches(Regex("[0-9a-f]{64}")))
        val reports = File("build/reports")
        reports.mkdirs()
        File(reports, "lp-002-hash.txt").writeText(expected)
        File(reports, "lp-002-hotel-sd.txt").writeText(
            items(output).single { it.id == "text_cal.hotel" }.stateDescription
        )
        for (node in items(output)) {
            assertTrue(node.stateDescription.isNotEmpty(), node.id)
            assertNotNull(node.axis)
        }
        val interpreted = A3UIInterpreter().interpret(
            vm.ui.value.surface!!,
            presentation,
            DemoFixtures.rendererContext()
        )
        assertEquals(
            items(output).map { it.stateDescription },
            items(interpreted).map { it.stateDescription }
        )
    }

    @Test
    fun LP_003_non_default_nodes_have_readable_content_description() {
        val output = A3HostViewModel.uncertain().ui.value.output!!
        composeRule.setContent {
            A3Screen(output, {}, false, false, {})
        }
        composeRule.waitForIdle()
        for (node in items(output)) {
            assertTrue(node.stateDescription.isNotEmpty(), node.id)
            composeRule.onNodeWithTag(node.id).assert(
                SemanticsMatcher("contentDescription carries state") { semantics ->
                    if (!semantics.config.contains(SemanticsProperties.ContentDescription)) {
                        false
                    } else {
                        val spoken = semantics.config[SemanticsProperties.ContentDescription]
                            .joinToString(" ")
                        spoken.contains(node.text) && spoken.contains(node.stateDescription)
                    }
                }
            )
        }
        composeRule.onNodeWithTag("text_cal.hotel").assert(
            SemanticsMatcher("hotel phrases") { semantics ->
                val spoken = semantics.config[SemanticsProperties.ContentDescription]
                    .joinToString(" ")
                spoken.contains("pending review") && spoken.contains("stale")
            }
        )
        composeRule.onNodeWithTag("text_cal.dinner").assert(
            SemanticsMatcher("dinner phrases") { semantics ->
                val spoken = semantics.config[SemanticsProperties.ContentDescription]
                    .joinToString(" ")
                spoken.contains("contradicted") && spoken.contains("compensated")
            }
        )
    }

    @Test
    fun LP_004_reduced_motion_zeroes_verbs_keeps_descriptions() {
        val moving = A3HostViewModel.uncertain(reducedMotion = false).ui.value.output!!
        val still = A3HostViewModel.uncertain(reducedMotion = true).ui.value.output!!
        assertTrue(still.reducedMotion)
        val hotel = items(still).single { it.id == "text_cal.hotel" }
        val dinner = items(still).single { it.id == "text_cal.dinner" }
        val flight = items(still).single { it.id == "text_cal.flight" }
        assertEquals(0, Exposure.motionMs(hotel.axis!!, reduced = true))
        assertEquals(0, Exposure.motionMs(dinner.axis!!, reduced = true))
        assertEquals(0, Exposure.motionMs(flight.axis!!, reduced = true))
        assertEquals(2800, Exposure.motionMs(hotel.axis!!, reduced = false))
        assertEquals(240, Exposure.motionMs(dinner.axis!!, reduced = false))
        assertEquals(0, Exposure.motionMs(flight.axis!!, reduced = false))
        assertEquals(
            items(moving).map { it.stateDescription },
            items(still).map { it.stateDescription }
        )
        composeRule.setContent {
            A3Screen(still, {}, false, false, {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_cal.hotel-held").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.dinner-compensated").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.flight-pending").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.meeting-done").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.hotel-motion-pulse").assertDoesNotExist()
        composeRule.onNodeWithTag("text_cal.dinner-motion-fade-back").assertDoesNotExist()
    }

    @Test
    fun LP_006_frozen_trees_have_empty_diff() {
        val root = File("..")
        val proc = ProcessBuilder(
            "git",
            "diff",
            "--stat",
            "--",
            "core/",
            "core/json",
            "core/admission",
            "core/action",
            "prediction/",
            "projection/",
            "a3ui/",
            "renderers/",
            "adapters/",
            "intent-model/",
            "broker/"
        ).directory(root).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor())
        assertTrue(out.isBlank(), out)
    }
}
