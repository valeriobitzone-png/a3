// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.core

import a3.a3ui.engine.Epistemic
import a3.a3ui.engine.EpistemicFacts
import a3.a3ui.engine.SurfaceComposer
import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.EpistemicSupport
import a3.a3ui.model.GestureBinding
import a3.a3ui.model.GestureMap
import a3.a3ui.model.HapticMap
import a3.a3ui.model.IntentCandidate
import a3.a3ui.model.MorphSpec
import a3.a3ui.model.MotionSpec
import a3.a3ui.model.Node
import a3.core.time.FixedClock
import a3.core.world.api.Claim
import a3.projection.model.CausalLineage
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.interp.GestureInterpreter
import a3.renderers.android.core.model.CatalogProfile
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RendererContext
import java.time.Instant
import java.util.TreeMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class A3UIAxisRendererTest {
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

    private fun surface(nodes: List<Node>, bindings: List<Binding> = emptyList()) = A3UISurface(
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
        nodes = nodes,
        bindings = bindings,
        producedAt = t
    )

    @Test
    fun AX_004_non_default_axis_has_state_description_with_reduced_motion() {
        val axis = Epistemic.derive(
            EpistemicFacts(
                claim = Claim("train.price", "12.40", 0.6, "train", t, t.plusSeconds(3600))
            ),
            t
        )
        assertFalse(axis.isDefault())
        val node = Node("n1", "text", axis = axis)
        val binding = Binding("train.price", "n1", "content")
        val full = interpreter.interpret(surface(listOf(node), listOf(binding)), presentation(), ctx())
        val reduced = interpreter.interpret(
            surface(listOf(node), listOf(binding)),
            presentation(),
            ctx(reducedMotion = true)
        )
        val described = full.nodes.single()
        assertTrue(described.stateDescription.isNotEmpty())
        assertTrue(described.stateDescription.contains("support"))
        assertEquals(described.stateDescription, reduced.nodes.single().stateDescription)
        assertTrue(reduced.reducedMotion)
        assertEquals("12.40", described.text)
        assertTrue(described.accessibleName.isNotEmpty())
    }

    @Test
    fun AX_005_v1_path_renders_content_ignores_axis_declares_degradation() {
        val axis = EpistemicAxis(support = EpistemicSupport.MEDIUM)
        val node = Node("n1", "text", axis = axis)
        val binding = Binding("train.price", "n1", "content")
        val v2 = surface(listOf(node), listOf(binding))
        val out = interpreter.interpret(v2, presentation(), ctx(), CatalogProfile.V1)
        assertEquals("12.40", out.nodes.single().text)
        assertNull(out.nodes.single().axis)
        assertEquals("", out.nodes.single().stateDescription)
        assertEquals("axis-ignored", out.degradation)
        assertEquals(CatalogProfile.V1, out.catalogProfile)
        val v1Empty = interpreter.interpret(v2, ctx())
        assertTrue(v1Empty.nodes.isEmpty())
        assertEquals("axis-ignored", v1Empty.degradation)
    }

    @Test
    fun AX_006_gesture_interpreter_emits_intent_candidate() {
        val map = GestureMap(listOf(GestureBinding("confirm", "confirm", "n2")))
        val emitted = GestureInterpreter().emit(map)
        assertEquals(1, emitted.size)
        assertEquals(IntentCandidate::class, emitted.single()::class)
        assertEquals("confirm", emitted.single().action)
        assertNotNull(emitted.single())
        val src = java.io.File("src/main/kotlin/a3/renderers/android/core/interp/GestureInterpreter.kt").readText()
        assertTrue(src.contains("IntentCandidate"))
        assertFalse(src.contains("Command"))
        assertFalse(src.contains("BeliefState"))
    }
}
