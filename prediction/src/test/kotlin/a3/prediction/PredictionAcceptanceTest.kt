package a3.prediction

import a3.core.time.FixedClock
import a3.core.time.InstantSource
import a3.core.time.SequentialIdGenerator
import a3.core.world.api.Fact
import a3.core.world.api.ReadBelief
import a3.prediction.engine.PredictionEngine
import a3.prediction.engine.PredictionEventLog
import a3.prediction.engine.PredictionReplay
import a3.prediction.engine.PredictionRequest
import a3.prediction.engine.PredictionResult
import a3.prediction.engine.PreparedStateStore
import a3.prediction.engine.RuleBasedForecaster
import a3.prediction.model.CapabilityHint
import a3.prediction.model.PreparedState
import a3.prediction.model.PredictionPolicy
import a3.prediction.model.PredictionStatus
import a3.prediction.serialize.CanonicalJson
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
 * P3–P6, P8, P10. P1/P2/P7/P9 live in :core:runtime (need WorldState).
 * P11 is ArchUnit.
 */
class PredictionAcceptanceTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private class MutableClock(var instant: Instant) : InstantSource {
        override fun now(): Instant = instant
    }

    private fun loopHints() = listOf(
        CapabilityHint(
            "calendar.read", emptyList(),
            listOf(Fact("calendar.next", "work@08:30", 1.0, "calendar", t, t.plusSeconds(3600))),
            reliability = 1.0
        ),
        CapabilityHint(
            "train.search",
            listOf(Fact("calendar.next", "work@08:30", 0.5, "calendar", t)),
            listOf(Fact("train.selected", true, 0.95, "train", t)),
            reliability = 1.0
        ),
        CapabilityHint(
            "train.commit",
            listOf(Fact("train.selected", true, 0.5, "train", t)),
            listOf(Fact("ticket.owned", true, 0.97, "train", t)),
            reliability = 1.0
        )
    )

    private fun rankingHints() = listOf(
        CapabilityHint("b.high", emptyList(), listOf(Fact("b.done", true, 1.0, "pred", t)), 1.0, 0.0, 0.0),
        CapabilityHint("a.low", emptyList(), listOf(Fact("a.done", true, 1.0, "pred", t)), 1.0, 1.0, 0.0),
        CapabilityHint("z.eq", emptyList(), listOf(Fact("z.done", true, 1.0, "pred", t)), 0.5, 0.0, 0.0),
        CapabilityHint("m.eq", emptyList(), listOf(Fact("m.done", true, 1.0, "pred", t)), 0.5, 0.0, 0.0)
    )

    private fun engine(
        clock: InstantSource = FixedClock(t),
        ids: SequentialIdGenerator = SequentialIdGenerator(),
        events: PredictionEventLog = PredictionEventLog(),
        policy: PredictionPolicy = PredictionPolicy("pol"),
        store: PreparedStateStore = PreparedStateStore(clock)
    ) = PredictionEngine(clock, ids, events, policy, store)

    @Test
    fun P3_PreparedState_expires_after_TTL() {
        val clock = MutableClock(t)
        val store = PreparedStateStore(clock)
        val policy = PredictionPolicy("pol", ttlSeconds = 300)
        val result = engine(clock, policy = policy, store = store)
            .predict(PredictionRequest("ctx", ReadBelief(), loopHints()))
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
        val events = PredictionEventLog()
        val eng = engine(events = events, store = store)
        val first = eng.predict(PredictionRequest("ctx_a", ReadBelief(), loopHints()))
        val oldId = assertNotNull(first.prepared).id
        assertNotNull(store.get(oldId))

        val second = eng.predict(PredictionRequest("ctx_b", ReadBelief(), loopHints()))
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
        val request = PredictionRequest("ctx", ReadBelief(), rankingHints())
        val a = engine().predict(request)
        val b = engine().predict(request)
        val hashed = HashMap<String, CapabilityHint>()
        for (hint in rankingHints()) hashed[hint.id] = hint
        val fromHash = engine().predict(
            request.copy(capabilities = ArrayList(hashed.values))
        )

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
        val events = PredictionEventLog()
        val eng = engine(events = events)
        val first = eng.predict(PredictionRequest("ctx_a", ReadBelief(), loopHints()))
        val second = eng.predict(PredictionRequest("ctx_b", ReadBelief(), loopHints()))
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
    fun P8_prediction_may_produce_PrefetchHint_but_never_Outcome() {
        val events = PredictionEventLog()
        val result = engine(events = events).predict(PredictionRequest("ctx", ReadBelief(), loopHints()))
        assertTrue(result.projectionCandidates.isNotEmpty())
        assertTrue(result.projectionCandidates.all { it.uiStateHint.startsWith("prefetch:") })
        assertTrue(result.futureStates.isNotEmpty())
        assertTrue(events.all().none { it.type.contains("outcome") })
        assertTrue(PredictionResult::class.java.declaredFields.none { it.name == "outcome" })
        assertEquals(result.futureStates.size, result.projectionCandidates.size)
        assertTrue(
            PreparedState::class.java.declaredMethods.none {
                it.name in setOf("commitToWorldState", "toWorldState", "toBeliefState", "commit")
            }
        )
    }

    @Test
    fun P10_canonical_determinism_Forecast_FutureState() {
        val request = PredictionRequest("ctx", ReadBelief(), rankingHints())
        val a = engine().predict(request)
        val b = engine().predict(request)
        val hashed = HashMap<String, CapabilityHint>()
        for (hint in rankingHints()) hashed[hint.id] = hint
        val fromHash = engine().predict(request.copy(capabilities = ArrayList(hashed.values)))

        val fa = CanonicalJson.of(a.forecast)
        val fb = CanonicalJson.of(b.forecast)
        assertEquals(fa, fb)
        assertContentEquals(fa.toByteArray(Charsets.UTF_8), fb.toByteArray(Charsets.UTF_8))
        assertContentEquals(CanonicalJson.bytes(a.forecast), CanonicalJson.bytes(fromHash.forecast))

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
