// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.GestureBinding
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
import a3.renderers.android.core.model.RendererContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.io.File
import java.time.Instant
import java.util.TreeMap
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
@Config(sdk = [34])
class Renderer03ComposeAcceptanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val t = Instant.parse("2026-08-27T08:00:00Z")
    private val interpreter = A3UIInterpreter()
    private val motion = MotionSpec(280.0, 24.0, "standard", 240)

    private fun ctx() = RendererContext(
        formFactor = "phone",
        density = "comfortable",
        tokens = TreeMap<String, ColorValue>().apply { put("accent", ColorValue(0, 90, 200)) },
        clock = FixedClock(t)
    )

    private fun lineage() = CausalLineage("ctx", 1, "e1")

    private fun presentation(vararg atoms: PresentationAtom) = PresentationState(
        id = "ps1",
        sourceStateVersion = 1,
        producedAt = t,
        atoms = atoms.toList(),
        lineage = lineage()
    )

    private fun surface(
        nodes: List<Node>,
        bindings: List<Binding> = emptyList(),
        gestures: List<GestureBinding> = emptyList()
    ) = A3UISurface(
        id = "s1",
        projectionRef = "p1",
        presentationRef = "ps1",
        lineage = lineage(),
        densityHint = "comfortable",
        colorTokens = listOf("accent"),
        motion = motion,
        morph = MorphSpec("ps1", "shared-element", listOf("confirm"), motion),
        gestures = GestureMap(gestures),
        haptics = HapticMap(emptyList()),
        nodes = nodes,
        bindings = bindings,
        producedAt = t
    )

    @Test
    fun R9_item_and_action_paint_interpreter_copy() {
        val s = surface(
            nodes = listOf(
                Node(
                    "timetable",
                    "list",
                    listOf(Node("item_train.slot.b", "item"), Node("item_train.slot.c", "item"))
                ),
                Node("action_ticket.owned", "action")
            ),
            bindings = listOf(
                Binding("train.slot.b", "item_train.slot.b", "content"),
                Binding("train.slot.c", "item_train.slot.c", "content"),
                Binding("ticket.owned", "action_ticket.owned", "content")
            ),
            gestures = listOf(GestureBinding("confirm", "confirm", "action_ticket.owned"))
        )
        val out = interpreter.interpret(
            s,
            presentation(
                PresentationAtom("timetable", "train.slot.b", "09:12", 50),
                PresentationAtom("timetable", "train.slot.c", "10:03", 50),
                PresentationAtom("confirm", "ticket.owned", true, 97)
            ),
            ctx()
        )
        assertEquals("09:12", find(out.nodes, "item_train.slot.b")!!.text)
        assertEquals("10:03", find(out.nodes, "item_train.slot.c")!!.text)
        assertEquals("true", find(out.nodes, "action_ticket.owned")!!.text)
        composeRule.setContent { ComposeRenderer(out) }
        composeRule.onNodeWithText("09:12").assertIsDisplayed()
        composeRule.onNodeWithText("10:03").assertIsDisplayed()
        composeRule.onNodeWithText("true").assertIsDisplayed()
        composeRule.onNodeWithTag("item_train.slot.b").assertIsDisplayed()
        composeRule.onNodeWithTag("item_train.slot.c").assertIsDisplayed()
        composeRule.onNodeWithTag("action_ticket.owned").assertIsDisplayed()
        composeRule.onNodeWithTag("item_train.slot.b").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("09:12"))
        )
        // SpokenLaw (f0aeac5) appends CTA reading for actions — painted text remains the first token.
        composeRule.onNodeWithTag("action_ticket.owned").assert(
            SemanticsMatcher("action contentDescription starts with painted text") { node ->
                val desc = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                desc.isNotEmpty() && (desc.first() == "true" || desc.any { it.startsWith("true") })
            }
        )
        composeRule.onNodeWithText("Confirm").assertDoesNotExist()
    }

    @Test
    fun R10_unbound_item_and_action_do_not_invent_copy() {
        val s = surface(
            nodes = listOf(
                Node("timetable", "list", listOf(Node("bare-item", "item"))),
                Node("bare-action", "action")
            )
        )
        val out = interpreter.interpret(s, presentation(), ctx())
        assertEquals("", find(out.nodes, "bare-item")!!.text)
        assertEquals("", find(out.nodes, "bare-action")!!.text)
        composeRule.setContent { ComposeRenderer(out) }
        composeRule.onNodeWithTag("bare-item").assertIsDisplayed()
        composeRule.onNodeWithTag("bare-action").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").assertDoesNotExist()
        composeRule.onNodeWithText("Conferma").assertDoesNotExist()
        composeRule.onNodeWithText("loading").assertDoesNotExist()
        composeRule.onNodeWithTag("bare-item").assert(
            SemanticsMatcher("no invented copy") { node ->
                val texts = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                texts.all { it.text.isEmpty() } &&
                    !node.config.contains(SemanticsProperties.ContentDescription)
            }
        )
        val catalog = File("src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt").readText()
        assertFalse(catalog.contains("Confirm"))
        assertFalse(catalog.contains("Conferma"))
        assertFalse(catalog.contains("loading"))
        for (line in catalog.lineSequence()) {
            if (line.contains("Text(") && !line.contains("BasicText")) {
                throw AssertionError("material Text: $line")
            }
        }
        assertFalse(catalog.contains("androidx.compose.material"))
        assertFalse(catalog.contains("Button("))
    }

    private fun find(
        nodes: List<a3.renderers.android.core.model.RenderedNode>,
        id: String
    ): a3.renderers.android.core.model.RenderedNode? {
        for (node in nodes) {
            if (node.id == id) return node
            val child = find(node.children, id)
            if (child != null) return child
        }
        return null
    }
}
