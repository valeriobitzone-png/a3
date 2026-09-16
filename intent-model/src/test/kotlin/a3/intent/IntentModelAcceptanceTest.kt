// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.intent

import a3.core.model.Capability
import a3.core.model.CapabilityGraph
import a3.core.model.Constraint
import a3.core.model.Claim
import a3.core.model.Goal
import a3.core.model.Intent
import a3.core.planner.DeterministicPlanner
import a3.core.planner.PlanResult
import a3.core.planner.PlannerError
import a3.core.runtime.Runtime
import a3.core.serialize.CanonicalJson
import a3.core.world.BeliefState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IntentModelAcceptanceTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private fun ctx(
        id: String = "i1",
        expression: String = "book the train",
        modality: String = "text",
        goalRef: String? = null
    ) = IntentContext(
        now = t,
        id = id,
        expression = expression,
        modality = modality,
        goalRef = goalRef
    )

    private fun graph() = CapabilityGraph(
        mapOf(
            "calendar.read" to Capability(
                "calendar.read", "calendar.read", emptyList(),
                effects = listOf(Claim("calendar.next", "work@08:30", 1.0, "calendar", t, t.plusSeconds(3600)))
            ),
            "train.search" to Capability(
                "train.search", "train.search",
                preconditions = listOf(Claim("calendar.next", "work@08:30", 0.5, "calendar", t)),
                effects = listOf(Claim("train.selected", true, 0.95, "train", t))
            ),
            "train.commit" to Capability(
                "train.commit", "train.commit",
                preconditions = listOf(Claim("train.selected", true, 0.5, "train", t)),
                effects = listOf(Claim("ticket.owned", true, 0.97, "train", t)),
                reversible = false
            )
        )
    )

    @Test
    fun I1_proposal_is_inferred_with_confidence_below_one() {
        val intent = RuleIntentProvider(0.8).infer(ctx())
        assertEquals("inferred", intent.source)
        assertTrue(intent.confidence < 1.0)
        assertEquals("i1", intent.id)
        assertEquals("book the train", intent.expression)
        assertEquals("text", intent.modality)

        assertFailsWith<IllegalArgumentException> {
            IntentProposal.validate(
                Intent("x", "e", "text", "inferred", 1.0)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            IntentProposal.validate(
                Intent("x", "e", "text", "explicit", 0.5)
            )
        }
    }

    @Test
    fun I2_infer_does_not_write_belief() {
        val belief = BeliefState(
            facts = listOf(Claim("calendar.next", "work@08:30", 1.0, "calendar", t))
        )
        val before = CanonicalJson.ofState(belief)
        val beforeBytes = CanonicalJson.bytesState(belief)
        val returned = RuleIntentProvider(0.8).infer(ctx())
        assertEquals(Intent::class.java, returned::class.java)
        assertEquals(before, CanonicalJson.ofState(belief))
        assertContentEquals(beforeBytes, CanonicalJson.bytesState(belief))
    }

    @Test
    fun I3_runtime_gradle_and_signatures_do_not_take_a_provider() {
        val runtimeGradle = java.io.File("../core/runtime/build.gradle.kts").readText()
        assertFalse(runtimeGradle.contains(":intent-model"))
        val modelGradle = java.io.File("build.gradle.kts").readText()
        assertTrue(modelGradle.contains("project(\":core:runtime\")"))

        val plan = DeterministicPlanner::class.java.methods.single { it.name == "plan" }
        assertEquals(
            listOf(
                Goal::class.java,
                BeliefState::class.java,
                CapabilityGraph::class.java,
                Instant::class.java
            ),
            plan.parameterTypes.toList()
        )
        assertFalse(plan.parameterTypes.any { it.name.startsWith("a3.intent") })

        val executes = Runtime::class.java.declaredMethods.filter { it.name == "execute" }
        assertTrue(executes.isNotEmpty())
        for (m in executes) {
            assertFalse(m.parameterTypes.any { it.name.startsWith("a3.intent") })
            assertFalse(m.parameterTypes.any { it == Intent::class.java })
            assertFalse(m.parameterTypes.any { it == IntentProvider::class.java })
        }
    }

    @Test
    fun I4_hand_built_intent_matches_stub_canonical_bytes() {
        val context = ctx(goalRef = "g1")
        val stub = RuleIntentProvider(0.8)
        val proposed = stub.infer(context)
        val hand = Intent(
            id = context.id,
            expression = context.expression,
            modality = context.modality,
            source = "inferred",
            confidence = 0.8,
            goalRef = context.goalRef
        )
        assertEquals(hand, proposed)
        assertContentEquals(CanonicalJson.bytes(hand), CanonicalJson.bytes(proposed))
        assertEquals(CanonicalJson.of(hand), CanonicalJson.of(proposed))

        val other = RuleIntentProvider(0.7).infer(context)
        assertNotEquals(CanonicalJson.of(hand), CanonicalJson.of(other))
        assertFalse(CanonicalJson.bytes(hand).contentEquals(CanonicalJson.bytes(other)))
    }

    @Test
    fun I5_goal_constraints_decide_not_the_provider() {
        val inferred = RuleIntentProvider(0.8).infer(ctx())
        val desired = listOf(Claim("ticket.owned", true, 0.5, "goal", t))
        val planner = DeterministicPlanner()

        val unconstrained = planner.plan(
            Goal("g", inferred.id, desired),
            BeliefState(),
            graph(),
            t
        )
        assertTrue(unconstrained is PlanResult.Success)

        val gated = planner.plan(
            Goal(
                "g",
                inferred.id,
                desired,
                constraints = listOf(Constraint("budget", "<=", -1))
            ),
            BeliefState(),
            graph(),
            t
        )
        assertEquals(PlannerError.ConstraintConflict, (gated as PlanResult.Failure).error)
    }
}
