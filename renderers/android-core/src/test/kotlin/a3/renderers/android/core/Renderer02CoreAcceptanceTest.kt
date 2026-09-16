// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.core

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.GestureBinding
import a3.a3ui.model.GestureMap
import a3.a3ui.model.HapticEvent
import a3.a3ui.model.HapticMap
import a3.a3ui.model.MorphSpec
import a3.a3ui.model.MotionSpec
import a3.a3ui.model.Node
import a3.a3ui.model.PrefetchSpec
import a3.a3ui.model.PrefetchStatus
import a3.core.time.FixedClock
import a3.projection.model.CausalLineage
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.interp.MotionInterpreter
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RendererContext
import a3.renderers.android.core.serialize.CanonicalJson
import java.io.File
import java.time.Instant
import java.util.TreeMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Renderer02CoreAcceptanceTest {
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
        haptics: List<HapticEvent> = emptyList(),
        prefetch: PrefetchSpec? = null
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
        prefetch = prefetch,
        producedAt = t
    )

    @Test
    fun R1_node_ir_interpret_builds_tree_and_zero_one_ignores_nodes() {
        val tree = listOf(
            Node(
                "root",
                "stack",
                listOf(Node("t", "text"), Node("a", "action"))
            )
        )
        val s = surface(tree)
        val two = interpreter.interpret(s, presentation(), ctx())
        assertEquals("stack", two.nodes.single().role)
        assertEquals(listOf("text", "action"), two.nodes.single().children.map { it.role })
        val one = interpreter.interpret(s, ctx())
        assertTrue(one.nodes.isEmpty())
        val unknown = surface(listOf(Node("g", "grid")))
        val thrown = assertFails { interpreter.interpret(unknown, presentation(), ctx()) }
        assertTrue(thrown.message!!.contains("role"))
    }

    @Test
    fun R2_binding_copy_fills_text_or_hint() {
        val nodes = listOf(Node("n1", "text"))
        val content = Binding("train.departure", "n1", "content")
        val present = presentation(PresentationAtom("fact", "train.departure", "08:45", 50))
        val filled = interpreter.interpret(surface(nodes, listOf(content)), present, ctx())
        val n1 = filled.nodes.single()
        assertEquals("08:45", n1.text)
        assertEquals("", n1.hint)
        assertEquals(
            """[{"children":[],"hint":"","id":"n1","role":"text","text":"08:45"}]""",
            CanonicalJson.of(filled.nodes)
        )
        val missing = interpreter.interpret(surface(nodes, listOf(content)), presentation(), ctx())
        assertEquals("", missing.nodes.single().text)
        val placeholder = Binding("train.departure", "n1", "placeholder")
        val hinted = interpreter.interpret(surface(nodes, listOf(placeholder)), present, ctx())
        assertEquals("", hinted.nodes.single().text)
        assertEquals("08:45", hinted.nodes.single().hint)
        val emptyHint = interpreter.interpret(surface(nodes, listOf(placeholder)), presentation(), ctx())
        assertEquals("", emptyHint.nodes.single().hint)
        assertEquals("", emptyHint.nodes.single().text)
    }

    @Test
    fun R3_gesture_carries_target_and_missing_target_throws() {
        val nodes = listOf(Node("root", "stack", listOf(Node("n2", "action"))))
        val tap = GestureBinding("tap", "confirm", "n2")
        val out = interpreter.interpret(surface(nodes, gestures = listOf(tap)), presentation(), ctx())
        val action = out.gestures.actions.single()
        assertEquals("n2", action.targetNodeId)
        assertEquals("confirm", action.action)
        assertEquals("tap", action.gesture)
        val missing = GestureBinding("tap", "confirm", "ghost")
        val thrown = assertFails {
            interpreter.interpret(surface(nodes, gestures = listOf(missing)), presentation(), ctx())
        }
        assertTrue(thrown.message!!.contains("target"))
    }

    @Test
    fun R4_list_requires_item_children() {
        val items = listOf(Node("i1", "item"), Node("i2", "item"), Node("i3", "item"))
        val ok = interpreter.interpret(
            surface(listOf(Node("timetable", "list", items))),
            presentation(),
            ctx()
        )
        assertEquals(3, ok.nodes.single().children.size)
        assertTrue(ok.nodes.single().children.all { it.role == "item" })
        val bad = surface(listOf(Node("timetable", "list", listOf(Node("x", "text")))))
        val thrown = assertFails { interpreter.interpret(bad, presentation(), ctx()) }
        assertTrue(thrown.message!!.contains("item"))
    }

    @Test
    fun R5_field_value_is_atom_or_empty() {
        val field = listOf(Node("pax", "field"))
        val bound = Binding("train.passenger", "pax", "value")
        val filled = interpreter.interpret(
            surface(field, listOf(bound)),
            presentation(PresentationAtom("passenger", "train.passenger", "Ada", 40)),
            ctx()
        )
        assertEquals("Ada", filled.nodes.single().text)
        val empty = interpreter.interpret(surface(field), presentation(), ctx())
        assertEquals("", empty.nodes.single().text)
    }

    @Test
    fun R6_motion_morph_haptic_still_on_output() {
        val tree = listOf(Node("root", "stack", listOf(Node("t", "text"))))
        val haptics = listOf(HapticEvent("confirm", "double-tap", "medium"))
        val prefetch = PrefetchSpec("pjc_1", 3, 0.9, 1000, PrefetchStatus.PREPARED, listOf("train.departure"))
        val s = surface(tree, haptics = haptics, prefetch = prefetch)
        val one = interpreter.interpret(s, ctx())
        val two = interpreter.interpret(s, presentation(), ctx())
        assertEquals(one.spring, two.spring)
        assertEquals(MotionInterpreter().interpret(s.motion), two.spring)
        assertEquals(one.sharedElements, two.sharedElements)
        assertEquals(one.haptics, two.haptics)
        assertEquals(listOf("train.departure"), two.prefetch!!.atomKeys)
        assertTrue(two.nodes.isNotEmpty())
        assertTrue(one.nodes.isEmpty())
    }

    @Test
    fun R7_no_material_widgets_in_renderer_main() {
        val forbiddenCalls = listOf(
            "Button(",
            "Card(",
            "Dialog(",
            "AlertDialog(",
            "Snackbar("
        )
        val roots = listOf(
            File("src/main"),
            File("../android-compose/src/main")
        )
        var files = 0
        for (root in roots) {
            val sources = root.walkTopDown().filter { it.extension == "kt" }.toList()
            assertTrue(sources.isNotEmpty(), root.path)
            files += sources.size
            for (file in sources) {
                val text = file.readText()
                for (token in forbiddenCalls) {
                    assertFalse(text.contains(token), "${file.path} contains $token")
                }
                assertFalse(text.contains("androidx.compose.material"), file.path)
                for (line in text.lineSequence()) {
                    if (line.contains("TextField(") && !line.contains("BasicTextField(")) {
                        throw AssertionError("${file.path} material TextField: $line")
                    }
                }
            }
        }
        val coreMain = File("src/main").walkTopDown().filter { it.extension == "kt" }
        assertTrue(coreMain.none { it.readText().contains("@Compos" + "able") })
        assertTrue(files > 0)
    }
}
