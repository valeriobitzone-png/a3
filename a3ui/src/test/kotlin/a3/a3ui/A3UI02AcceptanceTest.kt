// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui

import a3.a3ui.engine.BindingCopy
import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.engine.SurfaceComposer
import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.Node
import a3.a3ui.schema.ModelValidator
import a3.a3ui.schema.SchemaValidator
import a3.a3ui.serialize.CanonicalJson
import a3.core.time.SequentialIdGenerator
import a3.projection.model.CausalLineage
import a3.projection.model.Density
import a3.projection.model.FormFactorHints
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.projection.model.Projection
import a3.projection.model.ProjectionStatus
import java.io.File
import java.time.Instant
import java.util.ArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class A3UI02AcceptanceTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private fun compiler() =
        DeterministicA3UICompiler(t, SequentialIdGenerator())

    private fun lineage() = CausalLineage("ctx", 1, "evj_1")

    private fun projection(req: List<String> = listOf("attend", "confirm")) = Projection(
        id = "proj_1",
        presentationId = "ps_1",
        contextRef = "ctx",
        formFactorHints = FormFactorHints("phone", Density.COMFORTABLE),
        interactionRequirements = req,
        lineage = lineage(),
        status = ProjectionStatus.PROPOSED
    )

    private fun presentation(atoms: List<PresentationAtom>, id: String = "ps_1") = PresentationState(
        id = id,
        sourceStateVersion = 1,
        producedAt = t,
        atoms = atoms,
        lineage = lineage()
    )

    private fun flatten(nodes: List<Node>): List<Node> {
        val out = ArrayList<Node>()
        fun walk(node: Node) {
            out += node
            for (child in node.children) walk(child)
        }
        for (node in nodes) walk(node)
        return out
    }

    private fun surfaceJson(nodes: String, bindings: String = "[]", extra: String = ""): String {
        val prefetch = if (extra.isEmpty()) "" else extra
        return """{"bindings":$bindings,"color_tokens":["accent"],"density_hint":"compact","gestures":{"bindings":[]},"haptics":{"events":[]},"id":"s","lineage":{"causal_event_id":"e","source_state_version":0,"state_identity":"c"},"morph":{"mode":"shared-element","motion":{"curve":"standard","damping":1,"duration_hint":1,"stiffness":1},"shared":[],"to":"ps"},"motion":{"curve":"standard","damping":1,"duration_hint":1,"stiffness":1},"nodes":$nodes$prefetch,"presentation_ref":"ps","produced_at":"2026-08-27T08:00:00Z","projection_ref":"p"}"""
    }

    private fun catalogNodes(): String =
        """[{"id":"root","role":"stack","children":[{"id":"choice","role":"row","children":[{"id":"label","role":"text","children":[]},{"id":"pick","role":"action","children":[]}]},{"id":"timetable","role":"list","children":[{"id":"slot","role":"item","children":[]}]},{"id":"pax","role":"field","children":[]}]}]"""

    @Test
    fun P57_role_catalog_is_closed() {
        SchemaValidator.validateCanonical("a3uisurface.schema.json", surfaceJson(catalogNodes()))
        for (role in SurfaceComposer.ROLES) {
            val json = surfaceJson("""[{"id":"n","role":"$role","children":[]}]""")
            SchemaValidator.validateCanonical("a3uisurface.schema.json", json)
        }
        for (banned in listOf("grid", "modal", "tooltip")) {
            val json = surfaceJson("""[{"id":"n","role":"$banned","children":[]}]""")
            assertFails { SchemaValidator.validateCanonical("a3uisurface.schema.json", json) }
        }
    }

    @Test
    fun P58_binding_copy_is_atom_value_or_empty() {
        val atom = PresentationAtom("price", "train.price", "42.00", 40)
        val present = presentation(listOf(atom))
        val surface = compiler().compile(projection(listOf("attend")), present)
        assertEquals(t, surface.producedAt)
        assertTrue(surface.id.startsWith("a3ui_"))
        val bound = surface.bindings.single { it.atomKey == "train.price" }
        assertEquals("42.00", BindingCopy.of(bound, present))
        val missing = Binding("absent.price", bound.nodeId, "placeholder")
        assertNull(BindingCopy.of(missing, present))
        assertNull(BindingCopy.of(bound, present.copy(atoms = emptyList())))
        assertNotNull(flatten(surface.nodes).singleOrNull { it.id == bound.nodeId })
        val a = DeterministicA3UICompiler(t, SequentialIdGenerator()).compile(projection(listOf("attend")), present)
        val b = DeterministicA3UICompiler(t, SequentialIdGenerator()).compile(projection(listOf("attend")), present)
        assertEquals(CanonicalJson.of(a), CanonicalJson.of(b))
        assertFalse(CanonicalJson.of(a).contains(" "))
        ModelValidator.surface(surface)
        assertFalse(CanonicalJson.of(surface).contains("42.00"))
    }

    @Test
    fun P59_hierarchy_is_children_not_layout() {
        val atoms = listOf(
            PresentationAtom("station", "train.station", "origin", 80),
            PresentationAtom("select", "train.select", "pick", 70)
        )
        val surface = compiler().compile(projection(listOf("attend", "select")), presentation(atoms))
        val row = flatten(surface.nodes).single { it.role == "row" }
        assertEquals(listOf("text", "action"), row.children.map { it.role })
        ModelValidator.surface(surface)
        val withLayout = surfaceJson(
            """[{"id":"root","role":"row","children":[{"id":"t","role":"text","children":[]},{"id":"a","role":"action","children":[]}],"layout":"split"}]"""
        )
        assertFails { SchemaValidator.validateCanonical("a3uisurface.schema.json", withLayout) }
    }

    @Test
    fun P60_gesture_target_must_exist_in_tree() {
        val atoms = listOf(PresentationAtom("confirm", "train.confirm", "ok", 90))
        val surface = compiler().compile(projection(), presentation(atoms))
        val ids = SurfaceComposer.collectIds(surface.nodes)
        assertTrue(surface.gestures.bindings.isNotEmpty())
        for (binding in surface.gestures.bindings) {
            assertTrue(ids.contains(binding.targetNodeId), binding.targetNodeId)
        }
        val missing = assertFails {
            compiler().compile(projection(listOf("confirm")), presentation(emptyList()))
        }
        assertTrue(missing.message!!.contains("target"))
    }

    @Test
    fun P61_prefetch_atom_keys_without_second_surface() {
        val prefetch = compiler().compilePrefetch(
            a3.projection.model.ProjectionCandidate(
                id = "pjc_1",
                futureStateId = "fs_1",
                forecastId = "fc_1",
                contextRef = "ctx",
                presentation = presentation(listOf(PresentationAtom("fact", "ticket.owned", true, 97))),
                baseStateVersion = 3,
                status = a3.projection.model.CandidateStatus.PREPARED,
                expiresAt = t.plusSeconds(300),
                priority = 900,
                rank = 1,
                lineage = CausalLineage("ctx", 3, "evj_1", "fs_1", "fc_1")
            )
        )
        assertEquals(listOf("ticket.owned"), prefetch.prefetch!!.atomKeys)
        ModelValidator.surface(prefetch)
        val prepared =
            """{"atom_keys":["ticket.owned"],"base_state_version":0,"candidate_ref":"x","confidence":0,"status":"prepared","ttl_ms":0}"""
        val banned = listOf(
            "prefetch" + "Content",
            "prefetch" + "Data",
            "prefetch" + "Surface"
        )
        for (field in banned) {
            val json = surfaceJson("[]", extra = ""","prefetch":$prepared,"$field":true""")
            assertFails { SchemaValidator.validateCanonical("a3uisurface.schema.json", json) }
        }
    }

    @Test
    fun P62_no_extra_spec_types() {
        val allowed = listOf("MotionSpec", "MorphSpec", "PrefetchSpec")
        val forbidden = listOf(
            "Input" + "Spec",
            "Validation" + "Spec",
            "Step" + "Graph",
            "Layout" + "Spec",
            "Content" + "Spec",
            "Node" + "Spec",
            "Binding" + "Spec",
            "prefetch" + "Surface"
        )
        val sources = File("src").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            val specClasses = Regex("""(?:data\s+)?class\s+(\w+Spec)\b""").findAll(text).map { it.groupValues[1] }
            for (name in specClasses) {
                assertTrue(name in allowed, "${file.path} declares $name")
            }
            for (token in forbidden) {
                assertFalse(text.contains(token), "${file.path} contains $token")
            }
        }
        assertTrue(a3.a3ui.model.HapticMap::class.simpleName == "HapticMap")
        assertTrue(a3.a3ui.model.HapticEvent::class.simpleName == "HapticEvent")
        assertTrue(a3.a3ui.model.GestureMap::class.simpleName == "GestureMap")
        assertTrue(A3UISurface::class.java.declaredFields.any { it.name == "nodes" })
        assertTrue(A3UISurface::class.java.declaredFields.any { it.name == "bindings" })
    }

    @Test
    fun P63_train_scenario_is_expressible_from_injected_atoms() {
        val atoms = listOf(
            PresentationAtom("timetable", "train.slot.1", "08:30 origin-destination", 90),
            PresentationAtom("timetable", "train.slot.2", "09:15 origin-destination", 89),
            PresentationAtom("station", "train.station", "Milano", 80),
            PresentationAtom("select", "train.select", "select", 70),
            PresentationAtom("passenger", "train.passenger", "1", 60),
            PresentationAtom("price", "train.price", "42.00", 50),
            PresentationAtom("confirm", "train.confirm", "confirm", 40)
        )
        val injected = presentation(atoms)
        val surface = compiler().compile(
            projection(listOf("attend", "confirm", "select")),
            injected
        )
        val nodes = flatten(surface.nodes)
        val list = nodes.single { it.role == "list" }
        assertEquals(2, list.children.size)
        assertTrue(list.children.all { it.role == "item" })
        val selectBinding = surface.bindings.single { it.atomKey == "train.select" }
        assertEquals("action", nodes.single { it.id == selectBinding.nodeId }.role)
        val passenger = surface.bindings.single { it.atomKey == "train.passenger" }
        assertEquals("value", passenger.role)
        assertEquals("field", nodes.single { it.id == passenger.nodeId }.role)
        val price = surface.bindings.single { it.atomKey == "train.price" }
        assertEquals("text", nodes.single { it.id == price.nodeId }.role)
        val confirm = surface.bindings.single { it.atomKey == "train.confirm" }
        assertEquals("action", nodes.single { it.id == confirm.nodeId }.role)
        assertEquals("select", surface.gestures.bindings.single { it.action == "select" }.action)
        assertEquals("confirm", surface.gestures.bindings.single { it.action == "confirm" }.action)
        assertEquals("Milano", BindingCopy.of(surface.bindings.single { it.atomKey == "train.station" }, injected))
        assertFalse(CanonicalJson.of(surface).contains("Milano"))
        ModelValidator.surface(surface)
        val main = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(main.isNotEmpty())
        for (file in main) {
            val text = file.readText()
            assertFalse(text.contains("widget"), file.path)
            assertFalse(text.contains("Vi" + "ew"), file.path)
            assertFalse(text.contains("@Compos" + "able"), file.path)
            assertFalse(Regex("#[0-9a-fA-F]{6}").containsMatchIn(text), file.path)
            assertFalse(text.contains("and" + "roid"), file.path)
            assertFalse(text.contains("Milano"), file.path)
        }
    }

    @Test
    fun P64_steps_are_a_tree_not_a_state_machine() {
        val atoms = listOf(
            PresentationAtom("step", "booking.step.1", "search", 30),
            PresentationAtom("step", "booking.step.2", "results", 20),
            PresentationAtom("step", "booking.step.3", "pay", 10)
        )
        val surface = compiler().compile(projection(), presentation(atoms))
        val steps = flatten(surface.nodes).single { it.id == "steps" }
        assertEquals("stack", steps.role)
        assertEquals(3, steps.children.size)
        assertTrue(steps.children.all { it.role == "item" })
        val actions = steps.children.map { it.children.single() }
        assertTrue(actions.all { it.role == "action" })
        assertTrue(surface.gestures.bindings.any { it.action == "next-step" })
        assertTrue(surface.gestures.bindings.any { it.action == "confirm" })
        ModelValidator.surface(surface)
        val graph = "Step" + "Graph"
        val sources = File("src").walkTopDown().filter { it.extension == "kt" }
        assertTrue(sources.none { it.readText().contains(graph) })
    }
}
