package a3.renderers.android.compose

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.EpistemicAction
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.EpistemicFreshness
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.EpistemicSupport
import a3.a3ui.model.GestureMap
import a3.a3ui.model.HapticMap
import a3.a3ui.model.MorphSpec
import a3.a3ui.model.MotionSpec
import a3.a3ui.model.Node
import a3.core.time.FixedClock
import a3.projection.model.CausalLineage
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RenderedOutput
import a3.renderers.android.core.model.RendererContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.Instant
import java.util.LinkedHashMap
import java.util.TreeMap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RendererExposureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val t = Instant.parse("2026-08-27T08:00:00Z")
    private val interpreter = A3UIInterpreter()
    private val motion = MotionSpec(280.0, 24.0, "standard", 240)

    private data class Case(val name: String, val axis: EpistemicAxis, val mark: String)

    private val nonDefault = listOf(
        Case("medium", EpistemicAxis(support = EpistemicSupport.MEDIUM), "n1-medium"),
        Case("low", EpistemicAxis(support = EpistemicSupport.LOW), "n1-low"),
        Case("uncertain", EpistemicAxis(support = EpistemicSupport.UNKNOWN), "n1-uncertain"),
        Case("aging", EpistemicAxis(freshness = EpistemicFreshness.AGING), "n1-aging"),
        Case("stale", EpistemicAxis(freshness = EpistemicFreshness.STALE), "n1-stale"),
        Case("held", EpistemicAxis(status = EpistemicStatus.HELD), "n1-held"),
        Case("contradicted", EpistemicAxis(status = EpistemicStatus.CONTRADICTED), "n1-contradicted"),
        Case("pending", EpistemicAxis(action = EpistemicAction.PENDING), "n1-pending"),
        Case("unknown", EpistemicAxis(action = EpistemicAction.UNKNOWN), "n1-unknown"),
        Case("done", EpistemicAxis(action = EpistemicAction.DONE), "n1-done"),
        Case("compensated", EpistemicAxis(action = EpistemicAction.COMPENSATED), "n1-compensated")
    )

    private fun ctx(reducedMotion: Boolean = false) = RendererContext(
        formFactor = "phone",
        density = "comfortable",
        tokens = TreeMap<String, ColorValue>().apply { put("accent", ColorValue(0, 90, 200)) },
        clock = FixedClock(t),
        reducedMotion = reducedMotion
    )

    private fun lineage() = CausalLineage("ctx", 1, "e1")

    private fun presentation() = PresentationState(
        id = "ps1",
        sourceStateVersion = 1,
        producedAt = t,
        atoms = listOf(PresentationAtom("price", "train.price", "12.40", 60)),
        lineage = lineage()
    )

    private fun surface(node: Node) = A3UISurface(
        id = "s1",
        projectionRef = "p1",
        presentationRef = "ps1",
        lineage = lineage(),
        densityHint = "comfortable",
        colorTokens = listOf("accent"),
        motion = motion,
        morph = MorphSpec("ps1", "shared-element", emptyList(), motion),
        gestures = GestureMap(emptyList()),
        haptics = HapticMap(emptyList()),
        nodes = listOf(node),
        bindings = listOf(Binding("train.price", "n1", "content")),
        producedAt = t
    )

    private fun output(axis: EpistemicAxis?, reduced: Boolean = true): RenderedOutput {
        val node = Node("n1", "text", axis = axis)
        return interpreter.interpret(surface(node), presentation(), ctx(reduced))
    }

    @Test
    fun RX_001_non_default_axis_paints_distinguishable_pixels() {
        val hashes = LinkedHashMap<String, Long>()
        var shown by mutableStateOf(output(null))
        composeRule.setContent {
            Box(
                Modifier
                    .size(320.dp, 80.dp)
                    .background(Color.White)
                    .testTag("shot")
            ) {
                ComposeRenderer(shown)
            }
        }
        composeRule.waitForIdle()
        hashes["default"] = ExposureRaster.fingerprint(ExposureRaster.paint(shown))
        for (case in nonDefault) {
            composeRule.runOnIdle { shown = output(case.axis) }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("n1").assertIsDisplayed()
            composeRule.onNodeWithTag(case.mark).assertIsDisplayed()
            hashes[case.name] = ExposureRaster.fingerprint(ExposureRaster.paint(shown))
        }
        val stale = output(EpistemicAxis(freshness = EpistemicFreshness.STALE))
        val sample = File("build/reports/rx-001-stale.png")
        ExposureRaster.write(ExposureRaster.paint(stale), sample)
        assertTrue(sample.exists() && sample.length() > 0, "sample bitmap missing")
        assertEquals(nonDefault.size + 1, hashes.size)
        assertEquals(hashes.size, hashes.values.toSet().size, "pixel collision: $hashes")
    }

    @Test
    fun RX_002_non_default_state_description_in_content_description() {
        var shown by mutableStateOf(output(nonDefault.first().axis))
        composeRule.setContent { ComposeRenderer(shown) }
        for (case in nonDefault) {
            val out = output(case.axis)
            val node = out.nodes.single()
            assertTrue(node.stateDescription.isNotEmpty(), case.name)
            composeRule.runOnIdle { shown = out }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("n1").assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    node.stateDescription
                )
            )
            composeRule.onNodeWithTag("n1").assert(
                SemanticsMatcher("stateDescription in contentDescription") { semantics ->
                    if (!semantics.config.contains(SemanticsProperties.ContentDescription)) {
                        false
                    } else {
                        semantics.config[SemanticsProperties.ContentDescription]
                            .joinToString(" ")
                            .contains(node.stateDescription)
                    }
                }
            )
            composeRule.onNodeWithTag("n1-axis").assertIsDisplayed()
            composeRule.onNodeWithTag(case.mark).assertIsDisplayed()
        }
        val reduced = output(EpistemicAxis(status = EpistemicStatus.HELD), reduced = true)
        assertEquals(0, Exposure.motionMs(reduced.nodes.single().axis!!, reduced = true))
        composeRule.runOnIdle { shown = reduced }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("n1-held").assertIsDisplayed()
        composeRule.onNodeWithTag("n1-motion-pulse").assertDoesNotExist()
    }

    @Test
    fun RX_003_critical_transition_records_announce() {
        val sink = CountingAnnounce()
        var shown by mutableStateOf(output(EpistemicAxis(), reduced = true))
        composeRule.setContent { ComposeRenderer(shown, announce = sink) }
        composeRule.waitForIdle()
        val critical = listOf(
            EpistemicAxis(support = EpistemicSupport.UNKNOWN) to "uncertain",
            EpistemicAxis(status = EpistemicStatus.HELD) to "pending review",
            EpistemicAxis(status = EpistemicStatus.CONTRADICTED) to "contradicted",
            EpistemicAxis(freshness = EpistemicFreshness.STALE) to "stale",
            EpistemicAxis(action = EpistemicAction.COMPENSATED) to "compensated"
        )
        for ((axis, phrase) in critical) {
            composeRule.runOnIdle {
                sink.phrases.clear()
                shown = output(EpistemicAxis(), reduced = true)
            }
            composeRule.waitForIdle()
            assertTrue(sink.phrases.none { it.second == phrase }, phrase)
            composeRule.runOnIdle { shown = output(axis, reduced = true) }
            composeRule.waitForIdle()
            assertTrue(
                sink.phrases.any { it.first == "n1" && it.second == phrase },
                "missing $phrase in ${sink.phrases}"
            )
        }
    }

    @Test
    fun RX_004_high_contrast_uses_pattern_not_color_alone() {
        val hashes = LinkedHashMap<String, Long>()
        var shown by mutableStateOf(output(nonDefault.first().axis))
        composeRule.setContent { ComposeRenderer(shown, highContrast = true) }
        for (case in nonDefault) {
            composeRule.runOnIdle { shown = output(case.axis) }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(case.mark).assertIsDisplayed()
            hashes[case.name] = ExposureRaster.fingerprint(
                ExposureRaster.paint(shown, highContrast = true)
            )
        }
        assertEquals(nonDefault.size, hashes.values.toSet().size, "mono collision: $hashes")
        val catalog = File("src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt").readText()
        val exposure = File("src/main/kotlin/a3/renderers/android/compose/Exposure.kt").readText()
        assertTrue(exposure.contains("dashPathEffect"))
        assertTrue(exposure.contains("TextDecoration.LineThrough"))
        assertTrue(catalog.contains("[held]"))
        assertTrue(catalog.contains("||"))
        assertFalse(catalog.contains("loading"))
    }

    @Test
    fun RX_005_five_motion_verbs_bind_only_to_epistemic_states() {
        assertEquals(2800, Theme.heldPulseMs)
        assertEquals(800, Theme.unknownShimmerMs)
        assertEquals(240, Theme.contradictedCrackMs)
        assertEquals(180, Theme.staleFadeMs)
        assertEquals(220, Theme.compensatedFadeMs)
        val believed = EpistemicAxis()
        assertNull(Exposure.verb(believed))
        assertEquals(0, Exposure.motionMs(believed, reduced = false))
        assertEquals(ExposureVerb.PULSE, Exposure.verb(EpistemicAxis(status = EpistemicStatus.HELD)))
        assertEquals(ExposureVerb.SHIMMER, Exposure.verb(EpistemicAxis(support = EpistemicSupport.UNKNOWN)))
        assertEquals(ExposureVerb.SHIMMER, Exposure.verb(EpistemicAxis(action = EpistemicAction.UNKNOWN)))
        assertEquals(
            ExposureVerb.CRACK_REVERSE,
            Exposure.verb(EpistemicAxis(status = EpistemicStatus.CONTRADICTED))
        )
        assertEquals(ExposureVerb.FADE_OUT, Exposure.verb(EpistemicAxis(freshness = EpistemicFreshness.STALE)))
        assertEquals(
            ExposureVerb.FADE_BACK,
            Exposure.verb(EpistemicAxis(action = EpistemicAction.COMPENSATED))
        )
        assertNull(Exposure.verb(EpistemicAxis(support = EpistemicSupport.MEDIUM)))
        assertNull(Exposure.verb(EpistemicAxis(support = EpistemicSupport.LOW)))
        assertNull(Exposure.verb(EpistemicAxis(freshness = EpistemicFreshness.AGING)))
        assertNull(Exposure.verb(EpistemicAxis(action = EpistemicAction.PENDING)))
        assertNull(Exposure.verb(EpistemicAxis(action = EpistemicAction.DONE)))
        val verbs = listOf(
            EpistemicAxis(status = EpistemicStatus.HELD) to "pulse",
            EpistemicAxis(support = EpistemicSupport.UNKNOWN) to "shimmer",
            EpistemicAxis(status = EpistemicStatus.CONTRADICTED) to "crack-reverse",
            EpistemicAxis(freshness = EpistemicFreshness.STALE) to "fade-out",
            EpistemicAxis(action = EpistemicAction.COMPENSATED) to "fade-back"
        )
        composeRule.mainClock.autoAdvance = false
        var shown by mutableStateOf(output(verbs.first().first, reduced = false))
        composeRule.setContent { ComposeRenderer(shown) }
        composeRule.mainClock.advanceTimeByFrame()
        for ((axis, wire) in verbs) {
            shown = output(axis, reduced = false)
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithTag("n1-motion-$wire", useUnmergedTree = true).fetchSemanticsNode()
        }
        shown = output(EpistemicAxis(), reduced = false)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("n1-motion-pulse").assertDoesNotExist()
        composeRule.onNodeWithTag("n1-motion-shimmer").assertDoesNotExist()
        composeRule.onNodeWithTag("n1-motion-crack-reverse").assertDoesNotExist()
        composeRule.onNodeWithTag("n1-motion-fade-out").assertDoesNotExist()
        composeRule.onNodeWithTag("n1-motion-fade-back").assertDoesNotExist()
        composeRule.mainClock.autoAdvance = true
        val motionSource = File("src/main/kotlin/a3/renderers/android/compose/ExposureMotion.kt").readText()
        assertTrue(motionSource.contains("Theme.heldPulseMs"))
        assertTrue(motionSource.contains("Theme.unknownShimmerMs"))
        assertTrue(motionSource.contains("Theme.contradictedCrackMs"))
        assertTrue(motionSource.contains("Theme.staleFadeMs"))
        assertTrue(motionSource.contains("Theme.compensatedFadeMs"))
        assertTrue(motionSource.contains("rememberInfiniteTransition(label = node.id + \"-pulse\")"))
        assertTrue(!motionSource.contains("shimmer") || motionSource.contains("Animatable(0.4f)"))
        assertEquals(1, Regex("rememberInfiniteTransition\\(").findAll(motionSource).count())
    }

    @Test
    fun RX_006_reduced_motion_zeroes_duration_and_keeps_paint() {
        var shown by mutableStateOf(output(nonDefault.first().axis, reduced = true))
        composeRule.setContent { ComposeRenderer(shown) }
        for (case in nonDefault) {
            val axis = case.axis
            assertEquals(0, Exposure.motionMs(axis, reduced = true), case.name)
            if (Exposure.verb(axis) != null) {
                assertNotEquals(0, Exposure.motionMs(axis, reduced = false), case.name)
            }
            val out = output(axis, reduced = true)
            assertTrue(out.reducedMotion)
            composeRule.runOnIdle { shown = out }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("n1").assertIsDisplayed()
            composeRule.onNodeWithTag(case.mark).assertIsDisplayed()
            composeRule.onNodeWithTag("n1-axis").assertIsDisplayed()
            val verb = Exposure.verb(axis)
            if (verb != null) {
                composeRule.onNodeWithTag("n1-motion-" + verb.wire()).assertDoesNotExist()
            }
        }
    }

    @Test
    fun RX_focus_non_default_precedes_default() {
        var shown by mutableStateOf(output(EpistemicAxis(status = EpistemicStatus.HELD)))
        composeRule.setContent { ComposeRenderer(shown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("n1").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 0f)
        )
        composeRule.runOnIdle { shown = output(null) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("n1").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f)
        )
    }
}
