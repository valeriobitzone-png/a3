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
import a3.core.world.WorldState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class McpAdapterAcceptanceTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private fun graph() = CapabilityGraph(
        mapOf(
            "calendar.read" to Capability(
                "calendar.read", "calendar.read", emptyList(),
                effects = listOf(Fact("calendar.next", "work@08:30", 1.0, "calendar", t, t.plusSeconds(3600)))
            ),
            "train.search" to Capability(
                "train.search", "train.search",
                preconditions = listOf(Fact("calendar.next", "work@08:30", 0.5, "calendar", t)),
                effects = listOf(Fact("train.selected", true, 0.95, "train", t))
            ),
            "train.commit" to Capability(
                "train.commit", "train.commit",
                preconditions = listOf(Fact("train.selected", true, 0.5, "train", t)),
                effects = listOf(Fact("ticket.owned", true, 0.97, "train", t)),
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
        Goal("g", "i", listOf(Fact("ticket.owned", true, 0.5, "goal", t))),
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

        val nativeWorld = WorldState()
        val nativeResult = Runtime(Policy(), TrustGate()).execute(
            plan().plan, nativeWorld, caps, nativeExecutor(), t, grants()
        )
        assertTrue(nativeResult.committed)
        val b1 = CanonicalJson.bytesState(nativeResult.state)

        val mcpWorld = WorldState()
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
        val poisonedWorld = WorldState()
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
    fun S3_runtime_gradle_does_not_depend_on_mcp_adapter() {
        val runtimeGradle = java.io.File("../../core/runtime/build.gradle.kts").readText()
        assertFalse(runtimeGradle.contains("project(\":adapters:mcp\")"))
        assertFalse(runtimeGradle.contains("project(\":adapters"))
        val adapterGradle = java.io.File("build.gradle.kts").readText()
        assertTrue(adapterGradle.contains("project(\":core:runtime\")"))
        assertTrue(adapterGradle.contains("project(\":core:world-api\")"))
    }
}
