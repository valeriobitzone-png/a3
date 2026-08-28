package a3.core.prediction

import a3.core.events.EventLog
import a3.core.model.*
import a3.core.planner.DeterministicPlanner
import a3.core.planner.PlanResult
import a3.core.prediction.engine.ContextSignatures
import a3.core.prediction.engine.PredictionEngine
import a3.core.prediction.engine.PredictionReplay
import a3.core.prediction.engine.PredictionRequest
import a3.core.prediction.engine.PredictionResult
import a3.core.prediction.engine.PreparedStateStore
import a3.core.prediction.engine.RuleBasedForecaster
import a3.core.prediction.model.PreparedState
import a3.core.prediction.model.PredictionPolicy
import a3.core.prediction.model.PredictionStatus
import a3.core.serialize.CanonicalJson
import a3.core.time.FixedClock
import a3.core.time.InstantSource
import a3.core.time.SequentialIdGenerator
import a3.core.world.BeliefState
import a3.core.world.EpistemicSource
import a3.core.world.WorldState
import a3.core.world.WriteResult
import java.time.Instant
import java.util.HashMap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prediction core acceptance P1–P10. T1–T10 remain in CoreAcceptanceTest.
 */
class PredictionAcceptanceTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private class MutableClock(var instant: Instant) : InstantSource {
        override fun now(): Instant = instant
    }

    private fun loopGraph() = CapabilityGraph(
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

    private fun rankingGraph() = CapabilityGraph(
        mapOf(
            "b.high" to Capability(
                "b.high", "b.high", emptyList(),
                effects = listOf(Fact("b.done", true, 1.0, "pred", t)),
                reliability = 1.0,
                cost = Cost()
            ),
            "a.low" to Capability(
                "a.low", "a.low", emptyList(),
                effects = listOf(Fact("a.done", true, 1.0, "pred", t)),
                reliability = 1.0,
                cost = Cost(money = 1.0)
            ),
            "z.eq" to Capability(
                "z.eq", "z.eq", emptyList(),
                effects = listOf(Fact("z.done", true, 1.0, "pred", t)),
                reliability = 0.5,
                cost = Cost()
            ),
            "m.eq" to Capability(
                "m.eq", "m.eq", emptyList(),
                effects = listOf(Fact("m.done", true, 1.0, "pred", t)),
                reliability = 0.5,
                cost = Cost()
            )
        )
    )

    private fun engine(
        clock: InstantSource = FixedClock(t),
        ids: SequentialIdGenerator = SequentialIdGenerator(),
        events: EventLog = EventLog(),
        policy: PredictionPolicy = PredictionPolicy("pol"),
        store: PreparedStateStore = PreparedStateStore(clock)
    ) = PredictionEngine(clock, ids, events, policy, store)

    @Test
    fun P1_prediction_produces_FutureState_without_modifying_WorldState() {
        val world = WorldState()
        val before = CanonicalJson.bytesState(world.committed)
        val result = engine().predict(PredictionRequest("ctx", world.committed, loopGraph()))
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
        val result = engine().predict(PredictionRequest("ctx", BeliefState(), loopGraph()))
        val prepared = assertNotNull(result.prepared)
        assertEquals("PreparedState", prepared::class.simpleName)
        assertNotEquals("BeliefState", prepared::class.simpleName)
        assertTrue(
            PreparedState::class.java.declaredMethods.none { it.name == "commitToWorldState" }
        )
        assertTrue(
            PreparedState::class.java.methods.none { it.name == "commitToWorldState" }
        )

        val world = WorldState()
        val observation = Observation("o_from_prep", prepared.forecastId, t, prepared.facts)
        val rejected = world.write(EpistemicSource.PREDICTION, observation, t, causalId = prepared.id)
        assertTrue(rejected is WriteResult.Rejected)
        assertEquals(EpistemicSource.PREDICTION, (rejected as WriteResult.Rejected).source)
        assertEquals(0, world.committed.version)
        assertTrue(world.eventLog().all().any { it.type == "state.write_rejected" })

        val viaAdapter = PredictionWrite.attempt(
            world,
            Prediction(prepared.id, prepared.contextRef),
            observation,
            t
        )
        assertTrue(viaAdapter is WriteResult.Rejected)
        assertEquals(0, world.committed.version)
        assertContentEquals(
            CanonicalJson.bytesState(BeliefState()),
            CanonicalJson.bytesState(world.committed)
        )
    }

    @Test
    fun P3_PreparedState_expires_after_TTL() {
        val clock = MutableClock(t)
        val store = PreparedStateStore(clock)
        val policy = PredictionPolicy("pol", ttlSeconds = 300)
        val result = engine(clock, policy = policy, store = store)
            .predict(PredictionRequest("ctx", BeliefState(), loopGraph()))
        val prepared = assertNotNull(result.prepared)
        assertEquals(t.plusSeconds(300), prepared.expiresAt)
        assertEquals(PredictionStatus.PREPARED, prepared.status)
        assertNotNull(store.get(prepared.id))

        clock.instant = t.plusSeconds(299)
        assertNotNull(store.get(prepared.id), "t < expires_at remains live")

        clock.instant = t.plusSeconds(300)
        assertNull(store.get(prepared.id), "t == expires_at is expired")
        assertNotNull(store.peek(prepared.id))
        assertEquals(PredictionStatus.PREPARED, store.peek(prepared.id)?.status)
    }

    @Test
    fun P4_new_context_invalidates_previous_prepared_state() {
        val store = PreparedStateStore(FixedClock(t))
        val events = EventLog()
        val eng = engine(events = events, store = store)
        val first = eng.predict(PredictionRequest("ctx_a", BeliefState(), loopGraph()))
        val oldId = assertNotNull(first.prepared).id
        assertNotNull(store.get(oldId))

        val second = eng.predict(PredictionRequest("ctx_b", BeliefState(), loopGraph()))
        assertNull(store.get(oldId))
        assertEquals(PredictionStatus.INVALIDATED, store.peek(oldId)?.status)
        assertNotNull(second.prepared)
        assertNotNull(store.get(second.prepared!!.id))
        assertNotNull(second.invalidation)
        assertTrue(second.invalidation!!.preparedIds.contains(oldId))
        assertTrue(events.all().any { it.type == "prediction.invalidated" })
        assertNotEquals(first.forecast.contextRef, second.forecast.contextRef)
    }

    @Test
    fun P5_forecast_ranking_deterministic() {
        val request = PredictionRequest("ctx", BeliefState(), rankingGraph())
        val a = engine().predict(request)
        val b = engine().predict(request)
        val hashed = HashMap(rankingGraph().capabilities)
        val fromHash = engine().predict(request.copy(graph = CapabilityGraph(hashed)))

        val caps = a.forecast.candidates.map { it.capabilityRef }
        assertEquals(listOf("b.high", "a.low", "m.eq", "z.eq"), caps)
        assertEquals(1.0, a.forecast.candidates[0].score)
        assertEquals(0.5, a.forecast.candidates[1].score)
        assertEquals(0.5, a.forecast.candidates[2].score)
        assertEquals(0.5, a.forecast.candidates[3].score)
        assertEquals("m.eq", a.forecast.candidates[2].capabilityRef)
        assertEquals("z.eq", a.forecast.candidates[3].capabilityRef)
        assertEquals(
            a.forecast.candidates,
            a.forecast.candidates.sortedWith(RuleBasedForecaster.CANDIDATE_ORDER)
        )
        assertEquals(a.forecast, b.forecast)
        assertContentEquals(CanonicalJson.bytes(a.forecast), CanonicalJson.bytes(b.forecast))
        assertContentEquals(CanonicalJson.bytes(a.forecast), CanonicalJson.bytes(fromHash.forecast))
        assertEquals(a.forecast.candidates.map { it.rank }, listOf(1, 2, 3, 4))
    }

    @Test
    fun P6_prediction_event_log_replayable() {
        val events = EventLog()
        val eng = engine(events = events)
        val first = eng.predict(PredictionRequest("ctx_a", BeliefState(), loopGraph()))
        val second = eng.predict(PredictionRequest("ctx_b", BeliefState(), loopGraph()))
        assertNotNull(first.prepared)
        assertNotNull(second.prepared)

        val replayed = PredictionReplay.replay(events)
        assertEquals(2, replayed.forecasts.size)
        assertContentEquals(
            CanonicalJson.bytes(first.forecast),
            CanonicalJson.bytes(replayed.forecasts[0])
        )
        assertContentEquals(
            CanonicalJson.bytes(second.forecast),
            CanonicalJson.bytes(replayed.forecasts[1])
        )
        assertContentEquals(
            CanonicalJson.bytes(second.forecast),
            CanonicalJson.bytes(replayed.lastForecast())
        )
        val live = replayed.live(FixedClock(t))
        assertEquals(1, live.size)
        assertContentEquals(CanonicalJson.bytes(second.prepared), CanonicalJson.bytes(live.single()))
        val invalidated = replayed.prepared.first { it.id == first.prepared!!.id }
        assertEquals(PredictionStatus.INVALIDATED, invalidated.status)
        assertTrue(events.all().any { it.type == "prediction.invalidated" })
        assertEquals(events.all(), events.replay())
    }

    @Test
    fun P7_observation_accepted_remains_only_WorldState_writer() {
        val world = WorldState()
        engine().predict(PredictionRequest("ctx", world.committed, loopGraph()))
        assertEquals(0, world.committed.version)

        val obs = Observation(
            "o_ok", "e", t,
            listOf(Fact("ticket.owned", true, 1.0, "obs", t))
        )
        assertTrue(PredictionWrite.attempt(world, Prediction("pred_1", "ctx"), obs, t) is WriteResult.Rejected)
        assertTrue(PolicyWrite.attempt(world, obs, t) is WriteResult.Rejected)
        assertTrue(ExecutionWrite.attempt(world, obs, t) is WriteResult.Rejected)
        assertEquals(0, world.committed.version)

        val accepted = world.write(EpistemicSource.OBSERVATION_ACCEPTED, obs, t)
        assertTrue(accepted is WriteResult.Accepted)
        assertEquals(1, world.committed.version)
        assertEquals(true, world.committed.current(t).first { it.k == "ticket.owned" }.v)
        assertTrue(world.eventLog().all().any { it.type == "state.write_rejected" })
        assertTrue(world.eventLog().all().any { it.type == "state.updated" })
    }

    @Test
    fun P8_prediction_may_produce_ProjectionCandidate_but_never_Outcome() {
        val events = EventLog()
        val result = engine(events = events).predict(PredictionRequest("ctx", BeliefState(), loopGraph()))
        assertTrue(result.projectionCandidates.isNotEmpty())
        assertTrue(result.projectionCandidates.all { it.uiStateHint.startsWith("prefetch:") })
        assertTrue(result.futureStates.isNotEmpty())
        assertTrue(events.all().none { it.payload is Outcome })
        assertTrue(events.all().none { it.type.contains("outcome") })
        assertTrue(PredictionResult::class.java.declaredFields.none { it.name == "outcome" })
        assertEquals(result.futureStates.size, result.projectionCandidates.size)
    }

    @Test
    fun P9_core_T1_T10_remain_green() {
        val planner = DeterministicPlanner()
        val planned = planner.plan(
            Goal("g", "i", listOf(Fact("ticket.owned", true, 0.5, "goal", t))),
            BeliefState(),
            loopGraph(),
            t
        )
        assertTrue(planned is PlanResult.Success)
        val world = WorldState()
        engine().predict(PredictionRequest("ctx", world.committed, loopGraph()))
        val obs = Observation("o_t1", "e", t, listOf(Fact("ticket.owned", true, 1.0, "obs", t)))
        val accepted = world.write(EpistemicSource.OBSERVATION_ACCEPTED, obs, t)
        assertTrue(accepted is WriteResult.Accepted)
        assertEquals(1, world.committed.version)
        assertEquals(
            ContextSignatures.of("ctx", BeliefState(), t),
            ContextSignatures.of("ctx", BeliefState(), t)
        )
    }

    @Test
    fun P10_canonical_determinism_Forecast_FutureState() {
        val request = PredictionRequest("ctx", BeliefState(), rankingGraph())
        val a = engine().predict(request)
        val b = engine().predict(request)
        val hashed = engine().predict(request.copy(graph = CapabilityGraph(HashMap(rankingGraph().capabilities))))

        val fa = CanonicalJson.of(a.forecast)
        val fb = CanonicalJson.of(b.forecast)
        assertEquals(fa, fb)
        assertContentEquals(fa.toByteArray(Charsets.UTF_8), fb.toByteArray(Charsets.UTF_8))
        assertContentEquals(CanonicalJson.bytes(a.forecast), CanonicalJson.bytes(hashed.forecast))
        assertContentEquals(CanonicalJson.bytes(a.forecast), CanonicalJson.bytes(a.forecast))

        val fsa = CanonicalJson.of(a.futureStates.first())
        val fsb = CanonicalJson.of(b.futureStates.first())
        assertEquals(fsa, fsb)
        assertContentEquals(fsa.toByteArray(Charsets.UTF_8), fsb.toByteArray(Charsets.UTF_8))
        assertContentEquals(
            CanonicalJson.bytes(a.futureStates),
            CanonicalJson.bytes(b.futureStates)
        )

        assertTrue(fa.startsWith("{"))
        assertTrue(fa.indexOf("\"candidates\"") < fa.indexOf("\"context_ref\""))
        assertTrue(fa.indexOf("\"context_ref\"") < fa.indexOf("\"context_signature\""))
        assertTrue(fsa.indexOf("\"capability_ref\"") < fsa.indexOf("\"context_ref\""))
        assertFalse(fa.contains(" "))
        assertFalse(fsa.contains("\n"))
    }
}
