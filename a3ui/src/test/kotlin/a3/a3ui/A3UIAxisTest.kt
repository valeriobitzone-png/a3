package a3.a3ui

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.engine.Epistemic
import a3.a3ui.engine.EpistemicFacts
import a3.a3ui.engine.PresentationHash
import a3.a3ui.engine.SurfaceComposer
import a3.a3ui.model.EpistemicAction
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.EpistemicFreshness
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.EpistemicSupport
import a3.a3ui.model.GestureBinding
import a3.a3ui.model.IntentCandidate
import a3.a3ui.model.MotionSpec
import a3.a3ui.model.Node
import a3.a3ui.schema.ModelValidator
import a3.a3ui.serialize.CanonicalJson
import a3.core.time.SequentialIdGenerator
import a3.core.world.api.Claim
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class A3UIAxisTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private fun compiler(producedAt: Instant = t) =
        DeterministicA3UICompiler(producedAt, SequentialIdGenerator())

    private fun lineage() = CausalLineage("ctx", 1, "evj_1")

    private fun projection() = Projection(
        id = "proj_1",
        presentationId = "ps_1",
        contextRef = "ctx",
        formFactorHints = FormFactorHints("phone", Density.COMFORTABLE),
        interactionRequirements = listOf("attend"),
        lineage = lineage(),
        status = ProjectionStatus.PROPOSED
    )

    private fun presentation() = PresentationState(
        id = "ps_1",
        sourceStateVersion = 1,
        producedAt = t,
        atoms = listOf(PresentationAtom("price", "train.price", "12.40", 60)),
        lineage = lineage()
    )

    private fun claim(
        confidence: Double = 1.0,
        expiresAt: Instant? = t.plusSeconds(3600)
    ) = Claim("train.price", "12.40", confidence, "train", t, expiresAt)

    @Test
    fun AX_001_same_presentation_and_asOf_same_hash_and_main_is_clock_free() {
        val facts = mapOf("train.price" to EpistemicFacts(claim = claim(0.6)))
        val a = SurfaceComposer.compose(presentation(), t, facts)
        val b = SurfaceComposer.compose(presentation(), t, facts)
        assertEquals(PresentationHash.of(a), PresentationHash.of(b))
        val early = compiler(t).compile(projection(), presentation())
        val late = compiler(t.plusSeconds(90)).compile(projection(), presentation())
        assertEquals(PresentationHash.of(early), PresentationHash.of(late))
        assertNotEquals(early.producedAt, late.producedAt)
        val main = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(main.isNotEmpty())
        for (file in main) {
            val text = file.readText()
            assertFalse(text.contains("Instant.now"), file.path)
            assertFalse(Regex("""\bnow\(\)""").containsMatchIn(text), file.path)
        }
    }

    @Test
    fun AX_002_omit_when_default_canonical_bytes_match() {
        val implicit = Node("n", "text")
        val explicit = Node("n", "text", axis = EpistemicAxis())
        val explicitAll = Node(
            "n",
            "text",
            axis = EpistemicAxis(
                EpistemicSupport.HIGH,
                EpistemicFreshness.FRESH,
                EpistemicStatus.BELIEVED,
                EpistemicAction.NA
            )
        )
        val implicitBytes = CanonicalJson.of(implicit)
        val explicitBytes = CanonicalJson.of(explicit)
        val allBytes = CanonicalJson.of(explicitAll)
        assertEquals(implicitBytes, explicitBytes)
        assertEquals(implicitBytes, allBytes)
        assertEquals(
            """{"children":[],"id":"n","role":"text"}""",
            implicitBytes
        )
        assertFalse(implicitBytes.contains("axis"))
        assertFalse(implicitBytes.contains("support"))
        ModelValidator.surface(
            compiler().compile(projection(), presentation())
        )
    }

    @Test
    fun AX_003_uncertain_is_exposed_and_default_axis_fails() {
        val medium = Epistemic.derive(EpistemicFacts(claim = claim(0.6)), t)
        assertTrue(
            medium.support == EpistemicSupport.MEDIUM ||
                medium.support == EpistemicSupport.LOW
        )
        assertFalse(medium.isDefault())
        val held = Epistemic.derive(EpistemicFacts(admissionHeld = true), t)
        assertEquals(EpistemicStatus.HELD, held.status)
        val contradicted = Epistemic.derive(EpistemicFacts(revisionContradicted = true), t)
        assertEquals(EpistemicStatus.CONTRADICTED, contradicted.status)
        val unknown = Epistemic.derive(EpistemicFacts(actionPhase = "unknown"), t)
        assertEquals(EpistemicAction.UNKNOWN, unknown.action)
        val stale = Epistemic.derive(
            EpistemicFacts(claim = claim(1.0, expiresAt = t.minusSeconds(1))),
            t
        )
        assertEquals(EpistemicFreshness.STALE, stale.freshness)
        val tree = SurfaceComposer.compose(
            presentation(),
            t,
            mapOf("train.price" to EpistemicFacts(claim = claim(0.6)))
        )
        val price = flatten(tree.nodes).single { it.id.contains("train.price") }
        assertFalse(price.resolvedAxis().isDefault())
        val defaultAxis = EpistemicAxis()
        val failConf = assertFails {
            Epistemic.requireExposed(defaultAxis, EpistemicFacts(claim = claim(0.6)), t)
        }
        assertTrue(failConf.message!!.contains("uncertain"))
        val failHeld = assertFails {
            Epistemic.requireExposed(defaultAxis, EpistemicFacts(admissionHeld = true), t)
        }
        assertTrue(failHeld.message!!.contains("uncertain"))
        val failContradicted = assertFails {
            Epistemic.requireExposed(defaultAxis, EpistemicFacts(revisionContradicted = true), t)
        }
        assertTrue(failContradicted.message!!.contains("uncertain"))
        val failAction = assertFails {
            Epistemic.requireExposed(defaultAxis, EpistemicFacts(actionPhase = "unknown"), t)
        }
        assertTrue(failAction.message!!.contains("uncertain"))
        val failStale = assertFails {
            Epistemic.requireExposed(
                defaultAxis,
                EpistemicFacts(claim = claim(1.0, expiresAt = t.minusSeconds(1))),
                t
            )
        }
        assertTrue(failStale.message!!.contains("uncertain"))
    }

    @Test
    fun AX_006_gesture_binding_emits_intent_candidate_only() {
        val binding = GestureBinding("confirm", "confirm", "action_ticket.owned")
        val candidate = binding.emit()
        assertEquals(IntentCandidate::class, candidate::class)
        assertEquals("confirm", candidate.action)
        assertEquals("confirm", candidate.gesture)
        assertEquals("action_ticket.owned", candidate.targetNodeId)
        val main = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(main.isNotEmpty())
        for (file in main) {
            val text = file.readText()
            assertFalse(text.contains("a3.core.action.Command"), file.path)
            assertFalse(text.contains("BeliefState"), file.path)
        }
        assertEquals(
            IntentCandidate::class.java,
            GestureBinding::class.java.getMethod("emit").returnType
        )
    }

    @Test
    fun AX_007_motion_does_not_enter_presentation_hash() {
        val facts = mapOf("train.price" to EpistemicFacts(claim = claim(0.6)))
        val tree = SurfaceComposer.compose(presentation(), t, facts)
        val compact = MotionSpec(400.0, 28.0, "standard", 180)
        val spacious = MotionSpec(180.0, 22.0, "standard", 320)
        val base = compiler().compile(projection(), presentation()).copy(nodes = tree.nodes, bindings = tree.bindings)
        val a = base.copy(motion = compact)
        val b = base.copy(motion = spacious)
        assertEquals(PresentationHash.of(a), PresentationHash.of(b))
        assertNotEquals(CanonicalJson.of(a.motion), CanonicalJson.of(b.motion))
    }

    @Test
    fun AX_008_axis_lives_in_a3ui_not_frozen_core_modules() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("project(\":projection\")"))
        assertTrue(gradle.contains("project(\":core:world-api\")"))
        assertFalse(gradle.contains("project(\":core:admission\")"))
        assertFalse(gradle.contains("project(\":core:action\")"))
        assertFalse(gradle.contains("project(\":core:json\")".replace("json", "admission")))
        assertTrue(gradle.contains("project(\":core:json\")"))
        val sources = File("src/main").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertTrue(sources.contains("EpistemicAxis"))
        assertFalse(File("../core/world-api/src/main").walkTopDown().any { it.name == "EpistemicAxis.kt" })
        assertFalse(File("../core/admission/src/main").walkTopDown().any { it.name.contains("Epistemic") })
        assertFalse(File("../core/action/src/main").walkTopDown().any { it.name.contains("Epistemic") })
        assertFalse(File("../core/json/src/main").walkTopDown().any { it.name.contains("Epistemic") })
    }

    private fun flatten(nodes: List<Node>): List<Node> {
        val out = ArrayList<Node>()
        fun walk(node: Node) {
            out += node
            for (child in node.children) walk(child)
        }
        for (node in nodes) walk(node)
        return out
    }
}
