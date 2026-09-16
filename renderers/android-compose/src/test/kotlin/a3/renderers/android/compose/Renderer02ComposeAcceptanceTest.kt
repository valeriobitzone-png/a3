// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.GestureBinding
import a3.a3ui.model.GestureMap
import a3.a3ui.model.HapticEvent
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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.io.File
import java.time.Instant
import java.util.ArrayList
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
class Renderer02ComposeAcceptanceTest {
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
        gestures: List<GestureBinding> = emptyList(),
        haptics: List<HapticEvent> = emptyList()
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
        haptics = HapticMap(haptics),
        nodes = nodes,
        bindings = bindings,
        producedAt = t
    )

    @Test
    fun R1_node_ir_compose_maps_stack_text_action() {
        val s = surface(
            listOf(Node("root", "stack", listOf(Node("t", "text"), Node("a", "action")))),
            bindings = listOf(Binding("copy.t", "t", "content")),
            gestures = listOf(GestureBinding("tap", "confirm", "a"))
        )
        val out = interpreter.interpret(
            s,
            presentation(PresentationAtom("fact", "copy.t", "label", 1)),
            ctx()
        )
        val emitted = ArrayList<String>()
        composeRule.setContent { ComposeRenderer(out, onAction = { emitted += it }) }
        composeRule.onNodeWithTag("root").assertIsDisplayed()
        composeRule.onNodeWithTag("t").assertIsDisplayed()
        composeRule.onNodeWithTag("a").assertIsDisplayed()
        composeRule.onNodeWithTag("a").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("confirm"), emitted)
        val catalog = File("src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt").readText()
        assertTrue(catalog.contains("Column"))
        assertTrue(catalog.contains("BasicText"))
        assertTrue(catalog.contains("clickable"))
        assertTrue(catalog.contains("Box"))
    }

    @Test
    fun R3_gesture_clickable_emits_action_name() {
        val s = surface(
            listOf(Node("root", "stack", listOf(Node("n2", "action")))),
            gestures = listOf(GestureBinding("tap", "confirm", "n2"))
        )
        val out = interpreter.interpret(s, presentation(), ctx())
        val emitted = ArrayList<String>()
        composeRule.setContent { ComposeRenderer(out, emitted::add) }
        composeRule.onNodeWithTag("n2").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("confirm"), emitted)
    }

    @Test
    fun R4_list_maps_to_lazy_column_of_boxes() {
        val items = listOf(Node("i1", "item"), Node("i2", "item"), Node("i3", "item"))
        val s = surface(listOf(Node("timetable", "list", items)))
        val out = interpreter.interpret(s, presentation(), ctx())
        composeRule.setContent { ComposeRenderer(out) }
        composeRule.onNodeWithTag("timetable").assertIsDisplayed()
        composeRule.onNodeWithTag("i1").assertIsDisplayed()
        composeRule.onNodeWithTag("i2").assertIsDisplayed()
        composeRule.onNodeWithTag("i3").assertIsDisplayed()
        val catalog = File("src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt").readText()
        assertTrue(catalog.contains("LazyColumn"))
        assertTrue(catalog.contains("Box"))
    }

    @Test
    fun R5_field_maps_to_basic_text_field() {
        val s = surface(
            listOf(Node("pax", "field")),
            listOf(Binding("train.passenger", "pax", "value"))
        )
        val out = interpreter.interpret(
            s,
            presentation(PresentationAtom("passenger", "train.passenger", "Ada", 40)),
            ctx()
        )
        composeRule.setContent { ComposeRenderer(out) }
        composeRule.onNodeWithTag("pax").assertIsDisplayed()
        composeRule.onNodeWithTag("pax").assertTextContains("Ada")
        val catalog = File("src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt").readText()
        assertTrue(catalog.contains("BasicTextField"))
        assertFalse(catalog.contains("androidx.compose.material"))
    }

    @Test
    fun R6_compose_appliers_still_wrap_the_node_tree() {
        val s = surface(
            listOf(Node("root", "stack", listOf(Node("t", "text")))),
            haptics = listOf(HapticEvent("confirm", "double-tap", "medium"))
        )
        val out = interpreter.interpret(s, presentation(), ctx())
        composeRule.setContent { ComposeRenderer(out) }
        composeRule.onNodeWithTag("root").assertIsDisplayed()
        val renderer = File("src/main/kotlin/a3/renderers/android/compose/ComposeRenderer.kt").readText()
        assertTrue(renderer.contains("ComposeMotionApplier"))
        assertTrue(renderer.contains("ComposeMorphApplier"))
        assertTrue(renderer.contains("ComposeGestureApplier"))
        assertTrue(renderer.contains("ComposeHapticApplier"))
        assertTrue(out.spring.stiffness > 0.0)
        assertEquals("shared-element", out.sharedElements.mode)
        assertTrue(out.haptics.events.any { it.event == "confirm" })
    }

    @Test
    fun R7_compose_main_stays_on_foundation_catalog() {
        val forbidden = listOf(
            "Button(",
            "Card(",
            "Dialog(",
            "AlertDialog(",
            "Snackbar("
        )
        val sources = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            for (token in forbidden) {
                assertFalse(text.contains(token), "${file.path} $token")
            }
            assertFalse(text.contains("androidx.compose.material"), file.path)
            for (line in text.lineSequence()) {
                if (line.contains("TextField(") && !line.contains("BasicTextField(")) {
                    throw AssertionError("${file.path} material TextField: $line")
                }
            }
        }
        val catalog = File("src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt").readText()
        assertTrue(catalog.contains("Column"))
        assertTrue(catalog.contains("Row"))
        assertTrue(catalog.contains("LazyColumn"))
        assertTrue(catalog.contains("Box"))
        assertTrue(catalog.contains("BasicTextField"))
        assertTrue(catalog.contains("clickable"))
        assertTrue(catalog.contains("pointerInput"))
    }
}
