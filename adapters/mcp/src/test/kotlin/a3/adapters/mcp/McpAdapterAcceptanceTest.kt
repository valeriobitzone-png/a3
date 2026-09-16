// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.adapters.mcp

import a3.core.model.*
import a3.core.planner.DeterministicPlanner
import a3.core.planner.PlanResult
import a3.core.policy.Policy
import a3.core.runtime.Executor
import a3.core.runtime.Runtime
import a3.core.serialize.CanonicalJson
import a3.core.trust.TrustGate
import a3.core.world.BeliefState
import a3.core.world.BeliefWriter
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class McpAdapterAcceptanceTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

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

    private fun grants() = mapOf(
        "train.commit" to TrustGrant(
            "tg", "train.commit", TrustTier.IRREVERSIBLE, true, t.plusSeconds(300)
        )
    )

    private fun plan() = DeterministicPlanner().plan(
        Goal("g", "i", listOf(Claim("ticket.owned", true, 0.5, "goal", t))),
        BeliefState(),
        graph(),
        t
    ) as PlanResult.Success

    private fun nativeExecutor(): Executor = Executor { cap, now ->
        Observation("o_${cap.id}", "e", now, cap.effects.map { it.copy(id = "") })
    }

    @Test
    fun S1_substitution_same_facts_yield_identical_belief_bytes() {
        val caps = graph().capabilities
        val catalog = McpToolCatalog(caps)
        val mcp = McpCapabilityExecutor(catalog, serverId = "stub")

        val nativeWorld = BeliefWriter()
        val nativeResult = Runtime(Policy(), TrustGate()).execute(
            plan().plan, nativeWorld, caps, nativeExecutor(), t, grants()
        )
        assertTrue(nativeResult.committed)
        val b1 = CanonicalJson.bytesState(nativeResult.state)

        val mcpWorld = BeliefWriter()
        val mcpResult = Runtime(Policy(), TrustGate()).execute(
            plan().plan, mcpWorld, caps, mcp, t, grants()
        )
        assertTrue(mcpResult.committed)
        val b2 = CanonicalJson.bytesState(mcpResult.state)

        assertContentEquals(b1, b2)
        assertEquals(
            CanonicalJson.ofState(nativeResult.state),
            CanonicalJson.ofState(mcpResult.state)
        )

        val sample = mcp.execute(caps.getValue("calendar.read"), t)
        assertTrue(sample.executionRef.startsWith("mcp://stub/"))
        assertTrue(sample.facts.all { it.source != "mcp://stub/calendar.read" })
        assertTrue(sample.facts.all { it.id.isEmpty() })
        assertEquals("calendar", sample.facts.single().source)

        val poisoned = Executor { cap, now ->
            Observation(
                "o_${cap.id}",
                "e",
                now,
                cap.effects.map { it.copy(id = "", source = "mcp://stub/${cap.id}") }
            )
        }
        val poisonedWorld = BeliefWriter()
        val poisonedResult = Runtime(Policy(), TrustGate()).execute(
            plan().plan, poisonedWorld, caps, poisoned, t, grants()
        )
        assertTrue(poisonedResult.committed)
        assertNotEquals(
            CanonicalJson.ofState(nativeResult.state),
            CanonicalJson.ofState(poisonedResult.state)
        )
    }

    @Test
    fun MCP_BOUND_1_call_returns_candidate_apply_without_evaluate_throws() {
        val caps = graph().capabilities
        val mcp = McpCapabilityExecutor(McpToolCatalog(caps), serverId = "stub")
        val candidate = mcp.call(caps.getValue("calendar.read"), t)
        assertEquals(a3.core.admission.ObservationCandidate::class.java, candidate::class.java)
        val before = BeliefState()
        assertFails {
            before.apply(candidate)
        }
        assertEquals(0, before.version)
        assertTrue(before.facts.isEmpty())
        assertTrue(
            McpCapabilityExecutor::class.java.methods.none { method ->
                method.returnType.name.contains("AcceptedObservation")
            }
        )
    }

    @Test
    fun MCP_BOUND_2_admission_in_the_middle_keeps_belief_bytes_and_omits_admission_metadata() {
        val caps = graph().capabilities
        val catalog = McpToolCatalog(caps)
        val mcp = McpCapabilityExecutor(catalog, serverId = "stub")

        val nativeWorld = BeliefWriter()
        val nativeResult = Runtime(Policy(), TrustGate()).execute(
            plan().plan, nativeWorld, caps, nativeExecutor(), t, grants()
        )
        val mcpWorld = BeliefWriter()
        val mcpResult = Runtime(Policy(), TrustGate()).execute(
            plan().plan, mcpWorld, caps, mcp, t, grants()
        )
        assertTrue(nativeResult.committed)
        assertTrue(mcpResult.committed)
        assertContentEquals(
            CanonicalJson.bytesState(nativeResult.state),
            CanonicalJson.bytesState(mcpResult.state)
        )
        val json = CanonicalJson.ofState(nativeResult.state)
        assertFalse(json.contains("reasonCode"))
        assertFalse(json.contains("admittedAt"))
        assertFalse(json.contains("\"policyVersion\""))
        assertFalse(json.contains("\"policyId\""))
        assertTrue(json.contains("\"facts\""))
        assertTrue(json.contains("\"version\""))
    }

    @Test
    fun receipt_becomes_action_event_and_never_writes_belief() {
        val caps = graph().capabilities
        val mcp = McpCapabilityExecutor(McpToolCatalog(caps), serverId = "stub")
        val receipt = mcp.receipt(caps.getValue("calendar.read"), t)
        val event = mcp.actionEvent(receipt)
        assertTrue(event is a3.core.action.ActionEvent.ExecutorCompleted)
        val before = BeliefState()
        assertFails {
            a3.core.runtime.applyReceiptToBelief(before, receipt)
        }
        assertEquals(0, before.version)
        assertTrue(before.facts.isEmpty())
        val src = java.io.File("src/main/kotlin/a3/adapters/mcp/McpCapabilityExecutor.kt").readText()
        assertFalse(src.contains("BeliefState"))
        assertFalse(src.contains("BeliefWriter"))
        assertFalse(src.contains("evaluate("))
    }

    @Test
    fun S3_runtime_gradle_does_not_depend_on_mcp_adapter() {
        val runtimeGradle = java.io.File("../../core/runtime/build.gradle.kts").readText()
        assertFalse(runtimeGradle.contains("project(\":adapters:mcp\")"))
        assertFalse(runtimeGradle.contains("project(\":adapters"))
        val adapterGradle = java.io.File("build.gradle.kts").readText()
        assertTrue(adapterGradle.contains("project(\":core:runtime\")"))
        assertTrue(adapterGradle.contains("project(\":core:world-api\")"))
    }
}
