package a3.renderers.android.compose

import a3.a3ui.engine.Epistemic
import a3.a3ui.engine.EpistemicFacts
import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.GestureMap
import a3.a3ui.model.HapticMap
import a3.a3ui.model.MorphSpec
import a3.a3ui.model.MotionSpec
import a3.a3ui.model.Node
import a3.core.time.FixedClock
import a3.core.world.api.Claim
import a3.projection.model.CausalLineage
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RendererContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.time.Instant
import java.util.TreeMap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class A3UIAxisComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val t = Instant.parse("2026-08-27T08:00:00Z")
    private val interpreter = A3UIInterpreter()
    private val motion = MotionSpec(280.0, 24.0, "standard", 240)

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

    @Test
    fun AX_004_axis_state_description_visible_with_reduced_motion() {
        val axis = Epistemic.derive(
            EpistemicFacts(
                claim = Claim("train.price", "12.40", 0.6, "train", t, t.plusSeconds(3600))
            ),
            t
        )
        val node = Node("n1", "text", axis = axis)
        val out = interpreter.interpret(surface(node), presentation(), ctx(reducedMotion = true))
        assertTrue(out.nodes.single().stateDescription.isNotEmpty())
        composeRule.setContent { ComposeRenderer(out) }
        composeRule.onNodeWithTag("n1").assertIsDisplayed()
        composeRule.onNodeWithTag("n1-axis").assertIsDisplayed()
        composeRule.onNodeWithText(out.nodes.single().stateDescription).assertIsDisplayed()
        composeRule.onNodeWithTag("n1").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                out.nodes.single().stateDescription
            )
        )
    }
}
