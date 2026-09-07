package a3.core

import a3.core.capability.Transition
import a3.core.model.*
import a3.core.model.Claim
import a3.core.model.Observation
import a3.core.planner.*
import a3.core.policy.Policy
import a3.core.runtime.*
import a3.core.serialize.CanonicalJson
import a3.core.trust.TrustGate
import a3.core.admission.AcceptedObservation
import a3.core.world.BeliefState
import a3.core.world.BeliefWriter
import a3.core.world.WriteResult
import java.time.Instant
import kotlin.test.*

/**
 * Review 8/8 + epistemic invariant. Cited by REVIEW_T1.md.
 */
class InvariantReviewTest {
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
                effects = listOf(Claim("train.selected", true, 0.95, "train", t)),
                reliability = 0.8
            ),
            "train.commit" to Capability(
                "train.commit", "train.commit",
                preconditions = listOf(Claim("train.selected", true, 0.5, "train", t)),
                effects = listOf(Claim("ticket.owned", true, 0.97, "train", t)),
                reversible = false
            )
        )
    )

    private fun planner() = DeterministicPlanner()

    private fun grants() = mapOf(
        "train.commit" to TrustGrant("tg", "train.commit", TrustTier.IRREVERSIBLE, true, t.plusSeconds(300))
    )

    @Test
    fun R1_preconditions_canApply_iff_validAt() {
        val cap = graph().capabilities.getValue("train.search")
        val missing = BeliefState()
        assertFalse(Transition.canApply(missing, cap, t))

        val valid = BeliefState(
            facts = listOf(Claim("calendar.next", "work@08:30", 1.0, "calendar", t, t.plusSeconds(3600)))
        )
        assertTrue(Transition.canApply(valid, cap, t))
        assertTrue(valid.current(t).any { it.k == "calendar.next" && valid.validAt(it, t) })

        val expired = BeliefState(
            facts = listOf(Claim("calendar.next", "work@08:30", 1.0, "calendar", t.minusSeconds(7200), t.minusSeconds(1)))
        )
        assertFalse(Transition.canApply(expired, cap, t))
        assertTrue(expired.facts.none { expired.validAt(it, t) })

        val lowConfidence = BeliefState(
            facts = listOf(Claim("calendar.next", "work@08:30", 0.1, "calendar", t, t.plusSeconds(3600)))
        )
        assertTrue(
            Transition.canApply(lowConfidence, cap, t),
            "canApply is k=v ∧ validAt(now); confidence is not a gate"
        )
        val wrongValue = BeliefState(
            facts = listOf(Claim("calendar.next", "home@09:00", 1.0, "calendar", t, t.plusSeconds(3600)))
        )
        assertFalse(Transition.canApply(wrongValue, cap, t))
    }

    @Test
    fun R2_confidence_min_precond_times_reliability() {
        val cap = graph().capabilities.getValue("train.search")
        val state = BeliefState(
            facts = listOf(Claim("calendar.next", "work@08:30", 0.5, "calendar", t, t.plusSeconds(3600), id = "p1"))
        )
        val expected = 0.5 * 0.8
        assertEquals(expected, Transition.propagatedConfidence(state, cap, t))
        val next = Transition.apply(state, cap, t)
        val selected = next.current(t).first { it.k == "train.selected" }
        assertEquals(expected, selected.confidence)
    }

    @Test
    fun R3_contradictory_effect_supersedes_never_silent_overwrite() {
        val first = Observation(
            "o1", "e", t,
            listOf(Claim("ticket.owned", true, 0.9, "train", t, id = "a"))
        )
        val second = Observation(
            "o2", "e", t,
            listOf(Claim("ticket.owned", false, 0.8, "obs", t))
        )
        val world = BeliefWriter()
        world.apply(acceptedObservation(first, t))
        world.apply(acceptedObservation(second, t))
        val s2 = world.committed
        val history = s2.facts.filter { it.k == "ticket.owned" }
        assertEquals(2, history.size)
        val old = history.first { it.v == true }
        val neu = history.first { it.v == false }
        assertNotNull(old.supersededBy)
        assertEquals(neu.id, old.supersededBy)
        assertNull(neu.supersededBy)
        assertEquals(false, s2.current(t).first { it.k == "ticket.owned" }.v)
        assertEquals(2, s2.version)
    }

    @Test
    fun R4_compensatory_rollback_never_decrements_version() {
        val plan = planner().plan(
            Goal("g", "i", listOf(Claim("ticket.owned", true, 0.5, "goal", t))),
            BeliefState(), graph(), t
        ) as PlanResult.Success
        var calls = 0
        val executor = Executor { cap, now ->
            calls += 1
            val facts = if (calls <= 2) cap.effects else cap.effects.map { it.copy(v = false) }
            Observation("o_${cap.id}", "e", now, facts)
        }
        val world = BeliefWriter()
        val result = Runtime(Policy(), TrustGate()).execute(
            plan.plan, world, graph().capabilities, executor, t, grants()
        )
        assertTrue(result.rolledBack)
        assertFalse(result.committed)
        assertEquals(3, result.state.version)
        assertTrue(result.state.facts.any { it.supersededBy != null })
        assertTrue(result.state.current(t).none { it.k == "ticket.owned" && it.v == true })
        assertTrue(result.state.facts.any { it.source == "rollback" || it.supersededBy != null })
        assertTrue(result.state.version > 2)
    }

    @Test
    fun R5_stale_state_only_when_expires_at_before_now() {
        val stale = Claim("calendar.next", "work@08:30", 1.0, "calendar", t.minusSeconds(7200), t.minusSeconds(3600))
        val staleResult = planner().plan(
            Goal("g", "i", listOf(Claim("ticket.owned", true, 0.5, "goal", t))),
            BeliefState(facts = listOf(stale)), graph(), t
        )
        assertEquals(PlannerError.StaleState, (staleResult as PlanResult.Failure).error)

        val futureObserved = Claim("calendar.next", "work@08:30", 1.0, "calendar", t.plusSeconds(60), null)
        val futureResult = planner().plan(
            Goal("g", "i", listOf(Claim("ticket.owned", true, 0.5, "goal", t))),
            BeliefState(facts = listOf(futureObserved)), graph(), t
        )
        assertTrue(futureResult is PlanResult.Success, "observed_at > now must not emit STALE_STATE, got $futureResult")

        val live = Claim("calendar.next", "work@08:30", 1.0, "calendar", t, t.plusSeconds(3600))
        val liveResult = planner().plan(
            Goal("g", "i", listOf(Claim("ticket.owned", true, 0.5, "goal", t))),
            BeliefState(facts = listOf(live)), graph(), t
        )
        assertTrue(liveResult is PlanResult.Success)

        val unrelatedStale = Claim(
            "weather.temp", 3, 1.0, "weather", t.minusSeconds(7200), t.minusSeconds(3600)
        )
        val unrelated = planner().plan(
            Goal("g", "i", listOf(Claim("ticket.owned", true, 0.5, "goal", t))),
            BeliefState(facts = listOf(unrelatedStale)), graph(), t
        )
        assertTrue(
            unrelated is PlanResult.Success,
            "STALE_STATE only when a required fact has expires_at < now, got $unrelated"
        )

        val atExpiry = Claim("calendar.next", "work@08:30", 1.0, "calendar", t.minusSeconds(60), t)
        val atExpiryResult = planner().plan(
            Goal("g", "i", listOf(Claim("ticket.owned", true, 0.5, "goal", t))),
            BeliefState(facts = listOf(atExpiry)), graph(), t
        )
        assertTrue(
            atExpiryResult is PlanResult.Success,
            "expires_at == now is excluded from validAt but is not STALE_STATE (< now), got $atExpiryResult"
        )
    }

    @Test
    fun unmet_precondition_when_producer_blocked() {
        val onlySearch = CapabilityGraph(
            mapOf("train.search" to graph().capabilities.getValue("train.search"))
        )
        val result = planner().plan(
            Goal("g", "i", listOf(Claim("train.selected", true, 0.5, "goal", t))),
            BeliefState(), onlySearch, t
        )
        assertEquals(PlannerError.UnmetPrecondition, (result as PlanResult.Failure).error)
    }

    @Test
    fun R6_constraint_violation_is_constraint_conflict() {
        val result = planner().plan(
            Goal(
                "g", "i", listOf(Claim("ticket.owned", true, 0.5, "goal", t)),
                constraints = listOf(Constraint("budget", "<=", -1))
            ),
            BeliefState(), graph(), t
        )
        assertEquals(PlannerError.ConstraintConflict, (result as PlanResult.Failure).error)
    }

    @Test
    fun R7_determinism_canonical_bytes() {
        val goal = Goal("g", "i", listOf(Claim("ticket.owned", true, 0.5, "goal", t)))
        val a = planner().plan(goal, BeliefState(), graph(), t)
        val b = planner().plan(goal, BeliefState(), graph(), t)
        val ca = CanonicalJson.planResult(a)
        val cb = CanonicalJson.planResult(b)
        assertEquals(ca, cb)
        assertContentEquals(ca.toByteArray(Charsets.UTF_8), cb.toByteArray(Charsets.UTF_8))
        assertEquals(a, b)

        val hashed = java.util.HashMap(graph().capabilities)
        val fromHash = planner().plan(goal, BeliefState(), CapabilityGraph(hashed), t)
        assertEquals(ca, CanonicalJson.planResult(fromHash))
        assertContentEquals(
            ca.toByteArray(Charsets.UTF_8),
            CanonicalJson.planResult(fromHash).toByteArray(Charsets.UTF_8)
        )
    }

    @Test
    fun R8_replay_state_equals_original() {
        val plan = planner().plan(
            Goal("g", "i", listOf(Claim("ticket.owned", true, 0.5, "goal", t))),
            BeliefState(), graph(), t
        ) as PlanResult.Success
        val world = BeliefWriter()
        val result = Runtime(Policy(), TrustGate()).execute(
            plan.plan, world, graph().capabilities,
            Executor { cap, now -> Observation("o_${cap.id}", "e", now, cap.effects) },
            t, grants()
        )
        assertTrue(result.committed)
        val reconstructed = WorldStateReplay.replay(world.eventLog())
        assertEquals(CanonicalJson.ofState(result.state), CanonicalJson.ofState(reconstructed))
        assertContentEquals(
            CanonicalJson.bytesState(result.state),
            CanonicalJson.bytesState(reconstructed)
        )
        assertEquals(result.state.version, reconstructed.version)
    }

    @Test
    fun R9_epistemic_only_observation_accepted_writes() {
        val world = BeliefWriter()
        val now = t
        val obs = Observation(
            "o_ok", "e", now,
            listOf(Claim("ticket.owned", true, 1.0, "obs", now))
        )
        val applyMethods = BeliefWriter::class.java.methods.filter { it.name == "apply" }
        assertEquals(1, applyMethods.size)
        assertEquals(AcceptedObservation::class.java, applyMethods.single().parameterTypes.single())
        assertTrue(
            BeliefWriter::class.java.methods.none { it.name == "write" },
            "BeliefWriter.write(source, observation) must not exist"
        )

        val accepted = world.apply(acceptedObservation(obs, now))
        assertTrue(accepted is WriteResult.Accepted)
        assertEquals(1, world.committed.version)
        assertEquals(true, world.committed.current(now).first { it.k == "ticket.owned" }.v)
    }
}
