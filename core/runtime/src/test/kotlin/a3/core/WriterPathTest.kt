package a3.core

import a3.core.capability.Transition
import a3.core.model.*
import a3.core.planner.DeterministicPlanner
import a3.core.planner.PlanResult
import a3.core.policy.Policy
import a3.core.runtime.Executor
import a3.core.runtime.Runtime
import a3.core.runtime.WorldStateReplay
import a3.core.serialize.CanonicalJson
import a3.core.trust.TrustGate
import a3.core.world.BeliefState
import a3.core.world.WorldState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WriterPathTest {
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

    @Test
    fun W1_commit_goes_through_world_state_apply() {
        val world = WorldState()
        val prev = world.committed.version
        val result = Runtime(Policy(), TrustGate()).execute(
            plan().plan,
            world,
            graph().capabilities,
            Executor { cap, now -> Observation("o_${cap.id}", "e", now, cap.effects) },
            t,
            grants()
        )
        assertTrue(result.committed)
        assertTrue(result.state === world.committed)

        val accepted = world.eventLog().all().filter { it.type == "observation.accepted" }
        assertEquals(3, accepted.size)
        assertEquals(prev + accepted.size, world.committed.version)

        var expectedVersion = prev
        for (event in accepted) {
            expectedVersion += 1
            assertEquals(expectedVersion, event.stateVersion)
            val bump = world.eventLog().all().single {
                it.type == "state.updated" && it.stateVersion == event.stateVersion
            }
            assertEquals(event.id, bump.causalId)
        }

        val viaGate = WorldStateReplay.replay(world.eventLog())
        assertEquals(viaGate.version, world.committed.version)
        assertContentEquals(
            CanonicalJson.bytesState(viaGate),
            CanonicalJson.bytesState(result.state)
        )
        assertEquals(true, result.state.current(t).first { it.k == "ticket.owned" }.v)
    }

    @Test
    fun W2_rollback_mints_compensating_accepted_and_bumps_monotonically() {
        var calls = 0
        val executor = Executor { cap, now ->
            calls += 1
            val facts = if (calls <= 2) cap.effects else cap.effects.map { it.copy(v = false) }
            Observation("o_${cap.id}", "e", now, facts)
        }
        val world = WorldState()
        val prev = world.committed.version
        val result = Runtime(Policy(), TrustGate()).execute(
            plan().plan, world, graph().capabilities, executor, t, grants()
        )
        assertTrue(result.rolledBack)
        assertFalse(result.committed)
        assertTrue(result.state === world.committed)
        assertEquals(3, result.state.version)
        assertTrue(result.state.version > prev)
        assertTrue(result.state.facts.any { it.supersededBy != null })
        assertTrue(result.state.facts.any { it.source == "rollback" || it.supersededBy != null })
        assertTrue(result.state.current(t).none { it.k == "ticket.owned" && it.v == true })

        val versions = world.eventLog().all()
            .filter { it.type == "observation.accepted" }
            .map { it.stateVersion!! }
        assertEquals(listOf(1L, 2L, 3L), versions)
        assertTrue(versions.zipWithNext().all { (a, b) -> b == a + 1 })
        assertTrue(versions.none { it < prev })

        val compensatingEvent = world.eventLog().all()
            .filter { it.type == "observation.accepted" }
            .single { it.integrateMode == "COMPENSATE" }
        val compensating = compensatingEvent.payload as a3.core.world.Observation
        assertEquals("obs_rollback_${plan().plan.id}", compensating.id)
        assertTrue(result.state.facts.any { it.k == "calendar.next" && it.supersededBy != null })
        assertTrue(result.state.facts.any { it.k == "train.selected" && it.supersededBy != null })

        val viaGate = WorldStateReplay.replay(world.eventLog())
        assertEquals(3, viaGate.version)
        assertContentEquals(CanonicalJson.bytesState(viaGate), CanonicalJson.bytesState(result.state))
        assertNotNull(world.eventLog().all().first { it.type == "execution.rolled_back" })
    }

    @Test
    fun W5_planner_stays_on_copy_and_does_not_mint_or_apply() {
        val world = WorldState()
        val before = CanonicalJson.bytesState(world.committed)
        val planned = DeterministicPlanner().plan(
            Goal("g", "i", listOf(Fact("ticket.owned", true, 0.5, "goal", t))),
            world.committed,
            graph(),
            t
        )
        assertTrue(planned is PlanResult.Success)
        assertEquals(0, world.committed.version)
        assertContentEquals(before, CanonicalJson.bytesState(world.committed))
        assertTrue(world.eventLog().all().isEmpty())

        val simulated = Transition.apply(
            BeliefState(),
            graph().capabilities.getValue("calendar.read"),
            t
        )
        assertEquals(1, simulated.version)
        assertEquals(0, world.committed.version)
        assertContentEquals(before, CanonicalJson.bytesState(world.committed))
        assertTrue(world.eventLog().all().none { it.type == "observation.accepted" })
    }
}
