package a3.core

import a3.core.action.ActionEvent
import a3.core.action.ActionPhase
import a3.core.action.ExecutionReceipt
import a3.core.action.transition
import a3.core.model.*
import a3.core.planner.DeterministicPlanner
import a3.core.planner.PlanResult
import a3.core.policy.Policy
import a3.core.runtime.Executor
import a3.core.runtime.Runtime
import a3.core.runtime.applyActionEventToBelief
import a3.core.runtime.applyReceiptToBelief
import a3.core.runtime.completeWithReceipt
import a3.core.runtime.dispatchCommand
import a3.core.runtime.seedAuthorizedAction
import a3.core.serialize.CanonicalJson
import a3.core.trust.TrustGate
import a3.core.world.BeliefState
import a3.core.world.BeliefWriter
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ActionBoundaryTest {
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

    @Test
    fun ACT_001_dispatch_does_not_mutate_belief() {
        val world = BeliefWriter()
        val before = CanonicalJson.bytesState(world.committed)
        var action = seedAuthorizedAction("act", "plan-1", t, "rev-1")
        action = dispatchCommand(action, "cmd-1", t, "rev-1")
        assertEquals(ActionPhase.DISPATCHED, action.actionPhase)
        assertTrue(CanonicalJson.bytesState(world.committed).contentEquals(before))
        assertFails {
            applyActionEventToBelief(world.committed, ActionEvent.ExecutorCompleted(t, "r"))
        }
    }

    @Test
    fun ACT_002_executor_completed_does_not_write_belief() {
        var action = seedAuthorizedAction("act", "plan-1", t, "rev-1")
        action = dispatchCommand(action, "cmd-1", t, "rev-1")
        val receipt = ExecutionReceipt("r1", "cmd-1", "disp_cmd-1", true, t)
        val completed = completeWithReceipt(action, receipt)
        assertEquals(ActionPhase.COMPLETED, completed.actionPhase)
        assertNotEquals(ActionPhase.OBSERVED, completed.actionPhase)
        val belief = BeliefState()
        assertFails { applyReceiptToBelief(belief, receipt) }
        assertEquals(0, belief.version)
    }

    @Test
    fun execute_links_observed_only_after_admit() {
        val plan = DeterministicPlanner().plan(
            Goal("g", "i", listOf(Claim("ticket.owned", true, 0.5, "goal", t))),
            BeliefState(),
            graph(),
            t
        ) as PlanResult.Success
        val world = BeliefWriter()
        val result = Runtime(Policy(), TrustGate()).execute(
            plan.plan,
            world,
            graph().capabilities,
            Executor { cap, now -> Observation("o_${cap.id}", "e", now, cap.effects) },
            t,
            grants()
        )
        assertTrue(result.committed)
        val action = result.action!!
        assertEquals(ActionPhase.OBSERVED, action.actionPhase)
        assertFalse(CanonicalJson.ofState(result.state).contains("actionPhase"))
        assertTrue(BeliefState::class.java.declaredFields.none { it.type == a3.core.action.ActionState::class.java })
    }
}
