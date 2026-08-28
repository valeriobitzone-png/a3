package a3.core

import a3.core.events.EventLog
import a3.core.model.*
import a3.core.planner.*
import a3.core.policy.Policy
import a3.core.runtime.*
import a3.core.serialize.CanonicalJson
import a3.core.trust.TrustGate
import a3.core.world.BeliefState
import kotlin.test.*
import java.time.Instant

class CoreAcceptanceTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private fun graph() = CapabilityGraph(mapOf(
        "calendar.read" to Capability(
            "calendar.read","calendar.read", emptyList(),
            effects=listOf(Fact("calendar.next","work@08:30",1.0,"calendar",t,t.plusSeconds(3600)))
        ),
        "train.search" to Capability(
            "train.search","train.search",
            preconditions=listOf(Fact("calendar.next","work@08:30",0.5,"calendar",t)),
            effects=listOf(Fact("train.selected",true,0.95,"train",t))
        ),
        "train.commit" to Capability(
            "train.commit","train.commit",
            preconditions=listOf(Fact("train.selected",true,0.5,"train",t)),
            effects=listOf(Fact("ticket.owned",true,0.97,"train",t)),
            reversible=false
        )
    ))

    private fun planner() = DeterministicPlanner()

    @Test fun T1_happyPath() {
        val result = planner().plan(
            Goal("g","i", listOf(Fact("ticket.owned",true,0.5,"goal",t))),
            BeliefState(), graph(), t
        )
        assertTrue(result is PlanResult.Success)
        assertEquals("g", (result as PlanResult.Success).plan.goalRef)
    }

    @Test fun T2_noPlan() {
        val result = planner().plan(
            Goal("g","i", listOf(Fact("spaceship.ready",true,0.5,"goal",t))),
            BeliefState(), graph(), t
        )
        assertEquals(PlannerError.MissingCapability, (result as PlanResult.Failure).error)
    }

    @Test fun T3_constraintConflict() {
        val result = planner().plan(
            Goal("g","i", listOf(Fact("ticket.owned",true,0.5,"goal",t)),
                constraints=listOf(Constraint("budget","<=", -1))),
            BeliefState(), graph(), t
        )
        assertEquals(PlannerError.ConstraintConflict, (result as PlanResult.Failure).error)
    }

    @Test fun T4_missingPrecondition() {
        val result = planner().plan(
            Goal("g","i", listOf(Fact("train.selected",true,0.5,"goal",t))),
            BeliefState(), graph(), t
        )
        assertTrue(result is PlanResult.Success)
    }

    @Test fun T5_staleState() {
        val stale = Fact("calendar.next","work@08:30",1.0,"calendar",t.minusSeconds(7200),t.minusSeconds(3600))
        val result = planner().plan(
            Goal("g","i", listOf(Fact("ticket.owned",true,0.5,"goal",t))),
            BeliefState(facts=listOf(stale)), graph(), t
        )
        assertEquals(PlannerError.StaleState, (result as PlanResult.Failure).error)
    }

    @Test fun T6_loopCommit() {
        val plan = planner().plan(
            Goal("g","i", listOf(Fact("ticket.owned",true,0.5,"goal",t))),
            BeliefState(), graph(), t
        ) as PlanResult.Success
        val runtime = Runtime(Policy(), TrustGate(), EventLog())
        val executor = Executor { cap, now -> Observation("o_${cap.id}", "e", now, cap.effects) }
        val result = runtime.execute(plan.plan, BeliefState(), graph().capabilities, executor, t,
            grants=mapOf("train.commit" to TrustGrant("tg","train.commit",TrustTier.IRREVERSIBLE,true,t.plusSeconds(300))))
        assertTrue(result.committed)
        assertEquals(true, result.state.current(t).first { it.k == "ticket.owned" }.v)
    }

    @Test fun T7_loopMismatch() {
        val plan = planner().plan(
            Goal("g","i", listOf(Fact("ticket.owned",true,0.5,"goal",t))),
            BeliefState(), graph(), t
        ) as PlanResult.Success
        val runtime = Runtime(Policy(), TrustGate(), EventLog())
        val executor = Executor { cap, now ->
            Observation("o_${cap.id}", "e", now, cap.effects.map { it.copy(v=false) })
        }
        val result = runtime.execute(plan.plan, BeliefState(), graph().capabilities, executor, t,
            grants=mapOf("train.commit" to TrustGrant("tg","train.commit",TrustTier.IRREVERSIBLE,true,t.plusSeconds(300))))
        assertTrue(result.rolledBack)
        assertFalse(result.committed)
    }

    @Test fun T8_trustBlocked() {
        val plan = Plan("p","g",
            listOf(PlanStep(1,"train.commit",TrustTier.IRREVERSIBLE,false)),
            ExpectedOutcome("g", listOf(Fact("ticket.owned",true,1.0,"x",t))))
        val runtime = Runtime(Policy(), TrustGate(), EventLog())
        val result = runtime.execute(plan, BeliefState(), graph().capabilities,
            Executor { cap, now -> Observation("o","e",now,cap.effects) }, t)
        assertTrue(result.outcome.status == "failed")
    }

    @Test fun T9_determinism() {
        val goal = Goal("g","i", listOf(Fact("ticket.owned",true,0.5,"goal",t)))
        val a = planner().plan(goal, BeliefState(), graph(), t)
        val b = planner().plan(goal, BeliefState(), graph(), t)
        assertEquals(a, b)
        val ca = CanonicalJson.planResult(a)
        val cb = CanonicalJson.planResult(b)
        assertEquals(ca, cb)
        assertContentEquals(ca.toByteArray(Charsets.UTF_8), cb.toByteArray(Charsets.UTF_8))
        val hashed = java.util.HashMap(graph().capabilities)
        val fromHash = planner().plan(goal, BeliefState(), CapabilityGraph(hashed), t)
        assertContentEquals(
            ca.toByteArray(Charsets.UTF_8),
            CanonicalJson.planResult(fromHash).toByteArray(Charsets.UTF_8)
        )
    }

    @Test fun T10_replay() {
        val log = EventLog()
        log.append(Event("1",t,"world","state.updated",stateVersion=1))
        log.append(Event("2",t,"world","state.updated",causalId="1",stateVersion=2))
        val replay = log.replay()
        assertEquals(log.all(), replay)
        assertEquals("1", replay[1].causalId)

        val plan = planner().plan(
            Goal("g","i", listOf(Fact("ticket.owned",true,0.5,"goal",t))),
            BeliefState(), graph(), t
        ) as PlanResult.Success
        val execLog = EventLog()
        val result = Runtime(Policy(), TrustGate(), execLog).execute(
            plan.plan, BeliefState(), graph().capabilities,
            Executor { cap, now -> Observation("o_${cap.id}", "e", now, cap.effects) },
            t,
            grants=mapOf("train.commit" to TrustGrant("tg","train.commit",TrustTier.IRREVERSIBLE,true,t.plusSeconds(300)))
        )
        assertTrue(result.committed)
        val reconstructed = execLog.replayState(BeliefState())
        assertEquals(CanonicalJson.ofState(result.state), CanonicalJson.ofState(reconstructed))
        assertContentEquals(
            CanonicalJson.bytesState(result.state),
            CanonicalJson.bytesState(reconstructed)
        )
    }
}
