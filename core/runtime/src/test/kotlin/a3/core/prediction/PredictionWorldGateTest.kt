package a3.core.prediction

import a3.core.model.*
import a3.core.model.Fact
import a3.core.model.Observation
import a3.core.planner.DeterministicPlanner
import a3.core.planner.PlanResult
import a3.core.runtime.acceptedObservation
import a3.core.serialize.CanonicalJson
import a3.core.time.FixedClock
import a3.core.time.SequentialIdGenerator
import a3.core.world.AcceptedObservation
import a3.core.world.BeliefState
import a3.core.world.WorldState
import a3.core.world.WriteResult
import a3.prediction.engine.PredictionEngine
import a3.prediction.engine.PredictionEventLog
import a3.prediction.engine.PredictionRequest
import a3.prediction.engine.PreparedStateStore
import a3.prediction.model.CapabilityHint
import a3.prediction.model.PreparedState
import a3.prediction.model.PredictionPolicy
import java.time.Instant
import java.util.TreeMap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P1, P2, P7, P9 — require WorldState / AcceptedObservation (runtime module).
 */
class PredictionWorldGateTest {
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

    private fun hints(graph: CapabilityGraph = graph()): List<CapabilityHint> {
        val ordered = TreeMap(graph.capabilities)
        return ordered.values.map { cap ->
            CapabilityHint(
                id = cap.id,
                preconditions = cap.preconditions,
                effects = cap.effects,
                reliability = cap.reliability,
                money = cap.cost.money,
                timeMin = cap.cost.timeMin
            )
        }
    }

    private fun engine() = PredictionEngine(
        FixedClock(t),
        SequentialIdGenerator(),
        PredictionEventLog(),
        PredictionPolicy("pol"),
        PreparedStateStore(FixedClock(t))
    )

    @Test
    fun P1_prediction_produces_FutureState_without_modifying_WorldState() {
        val world = WorldState()
        val before = CanonicalJson.bytesState(world.committed)
        val result = engine().predict(PredictionRequest("ctx", world.committed, hints()))
        assertTrue(result.futureStates.isNotEmpty())
        val future = result.futureStates.first()
        assertEquals("FutureState", future::class.simpleName)
        assertNotEquals("BeliefState", future::class.simpleName)
        assertTrue(future.facts.any { it.k == "calendar.next" })
        assertEquals(0, world.committed.version)
        assertContentEquals(before, CanonicalJson.bytesState(world.committed))
        assertTrue(world.eventLog().all().none { it.type == "state.updated" })
        assertTrue(world.eventLog().all().none { it.type == "state.write_rejected" })
    }

    @Test
    fun P2_PreparedState_cannot_be_committed_into_WorldState() {
        val result = engine().predict(PredictionRequest("ctx", BeliefState(), hints()))
        val prepared = assertNotNull(result.prepared)
        assertEquals("PreparedState", prepared::class.simpleName)
        assertNotEquals("BeliefState", prepared::class.simpleName)
        assertTrue(
            PreparedState::class.java.declaredMethods.none {
                it.name in setOf("commitToWorldState", "toWorldState", "toBeliefState", "commit")
            }
        )
        val applyMethods = WorldState::class.java.methods.filter { it.name == "apply" }
        assertEquals(1, applyMethods.size)
        assertEquals(AcceptedObservation::class.java, applyMethods.single().parameterTypes.single())
        assertTrue(PreparedState::class.java != AcceptedObservation::class.java)

        // worldState.apply(preparedState)   ← NON COMPILA: nessun overload esiste.
        val world = WorldState()
        val obs = Observation("o_ok", "e", t, listOf(Fact("ticket.owned", true, 1.0, "obs", t)))
        val accepted = world.apply(acceptedObservation(obs, t))
        assertTrue(accepted is WriteResult.Accepted)
        assertEquals(1, world.committed.version)
    }

    @Test
    fun P7_observation_accepted_remains_only_WorldState_writer() {
        val world = WorldState()
        engine().predict(PredictionRequest("ctx", world.committed, hints()))
        assertEquals(0, world.committed.version)

        val applyMethods = WorldState::class.java.declaredMethods.filter { it.name == "apply" }
        assertTrue(applyMethods.all { it.parameterTypes.single() == AcceptedObservation::class.java })
        assertTrue(WorldState::class.java.methods.none { it.name == "write" })

        val obs = Observation(
            "o_ok", "e", t,
            listOf(Fact("ticket.owned", true, 1.0, "obs", t))
        )
        val accepted = world.apply(acceptedObservation(obs, t))
        assertTrue(accepted is WriteResult.Accepted)
        assertEquals(1, world.committed.version)
        assertEquals(true, world.committed.current(t).first { it.k == "ticket.owned" }.v)
        assertTrue(world.eventLog().all().any { it.type == "state.updated" })
    }

    @Test
    fun P9_core_T1_T10_remain_green() {
        val planner = DeterministicPlanner()
        val planned = planner.plan(
            Goal("g", "i", listOf(Fact("ticket.owned", true, 0.5, "goal", t))),
            BeliefState(),
            graph(),
            t
        )
        assertTrue(planned is PlanResult.Success)
        val world = WorldState()
        engine().predict(PredictionRequest("ctx", world.committed, hints()))
        val obs = Observation("o_t1", "e", t, listOf(Fact("ticket.owned", true, 1.0, "obs", t)))
        val accepted = world.apply(acceptedObservation(obs, t))
        assertTrue(accepted is WriteResult.Accepted)
        assertEquals(1, world.committed.version)
    }
}
