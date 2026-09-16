// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.runtime

import a3.core.action.ActionEvent
import a3.core.action.ActionState
import a3.core.action.ExecutionReceipt
import a3.core.action.requestCompensation
import a3.core.action.transition
import a3.core.admission.SourceId
import a3.core.model.*
import a3.core.policy.Policy
import a3.core.policy.PolicyDecision
import a3.core.schema.ModelValidator
import a3.core.trust.TrustGate
import a3.core.world.BeliefState
import a3.core.world.BeliefWriter
import a3.core.world.WriteResult
import java.time.Instant
import java.util.TreeMap

data class ExecutionResult(
    val outcome: Outcome,
    val committed: Boolean,
    val rolledBack: Boolean,
    val state: BeliefState,
    val action: ActionState? = null
)

fun interface Executor {
    fun execute(capability: Capability, now: Instant): Observation
}

class Runtime(
    private val policy: Policy,
    private val trustGate: TrustGate
) {
    fun execute(
        plan: Plan,
        world: BeliefWriter,
        capabilities: Map<String, Capability>,
        executor: Executor,
        now: Instant,
        grants: Map<String, TrustGrant> = emptyMap()
    ): ExecutionResult {
        ModelValidator.plan(plan)
        val caps = TreeMap(capabilities)
        val grantIndex = TreeMap(grants)
        val origin = world.committed
        val events = world.eventLog()
        val observed = ArrayList<Claim>()
        var lastEventId: String? = null
        val admittedIds = linkedSetOf<Pair<SourceId, String>>()
        var lastAction: ActionState? = null

        fun append(event: Event) {
            events.append(event)
            lastEventId = event.id
        }

        for (step in plan.steps.sortedBy { it.seq }) {
            val cap = caps[step.capability]
                ?: return failure(plan, world.committed, observed, lastAction)

            val decision = policy.evaluate(step.trustTier, step.reversible)
            val authorized = when (decision) {
                PolicyDecision.ALLOW -> true
                PolicyDecision.DENY -> false
                PolicyDecision.CONFIRM -> trustGate.allows(grantIndex[cap.id], step.trustTier, now)
            }
            if (!authorized) {
                append(
                    Event(
                        id = "ev_${step.seq}_trust",
                        t = now,
                        source = "trust",
                        type = "trust.denied",
                        causalId = lastEventId
                    )
                )
                var denied = ActionState("${plan.id}_${step.seq}")
                denied = transition(denied, ActionEvent.PlanProposed(now, plan.id))
                denied = transition(denied, ActionEvent.PlanValidated(now, plan.id))
                denied = transition(denied, ActionEvent.AuthorizationDenied(now, "trust"))
                return failure(plan, world.committed, observed, denied)
            }

            append(
                Event(
                    id = "ev_${step.seq}_start",
                    t = now,
                    source = "runtime",
                    type = "execution.started",
                    causalId = lastEventId
                )
            )

            val revision = beliefRevisionHash(world.committed)
            var action = seedAuthorizedAction("${plan.id}_${step.seq}", plan.id, now, revision)
            action = dispatchCommand(action, "cmd_${plan.id}_${step.seq}", now, revision)

            val observation = executor.execute(cap, now)
            ModelValidator.observation(observation)
            observed += observation.facts
            val receipt = ExecutionReceipt(
                receiptId = observation.id,
                commandId = "cmd_${plan.id}_${step.seq}",
                dispatchId = "disp_cmd_${plan.id}_${step.seq}",
                ok = true,
                at = now
            )
            action = completeWithReceipt(action, receipt)

            if (!matchesExpected(cap.effects, observation.facts)) {
                val compensating = rollbackObservation(plan, origin, now)
                val minted = admit(compensating, now, admittedIds)
                val write = world.applyCompensating(minted, lastEventId) as WriteResult.Accepted
                lastEventId = write.acceptedEventId
                append(
                    Event(
                        id = "ev_${step.seq}_rollback",
                        t = now,
                        source = "runtime",
                        type = "execution.rolled_back",
                        causalId = lastEventId,
                        stateVersion = write.state.version
                    )
                )
                val (compensated, _) = requestCompensation(action, now, "comp_${plan.id}")
                return ExecutionResult(
                    Outcome("out_${plan.id}", plan.goalRef, observed, 0.0, "failed"),
                    committed = false,
                    rolledBack = true,
                    state = world.committed,
                    action = compensated
                )
            }

            val minted = admit(observation, now, admittedIds)
            val write = world.apply(minted) as WriteResult.Accepted
            lastEventId = write.acceptedEventId
            action = linkAccepted(action, minted.candidate.id, now)
            lastAction = action
            append(
                Event(
                    id = "ev_${step.seq}_commit",
                    t = now,
                    source = "runtime",
                    type = "execution.committed",
                    causalId = lastEventId,
                    stateVersion = write.state.version
                )
            )
        }

        return ExecutionResult(
            Outcome("out_${plan.id}", plan.goalRef, observed, 1.0, "achieved"),
            committed = true,
            rolledBack = false,
            state = world.committed,
            action = lastAction
        )
    }

    private fun matchesExpected(expected: List<Claim>, observed: List<Claim>): Boolean {
        val actual = TreeMap<String, Any?>()
        for (fact in observed.sortedWith(compareBy({ it.k }, { it.id }))) {
            actual[fact.k] = fact.v
        }
        return expected.sortedBy { it.k }.all { fact -> actual[fact.k] == fact.v }
    }

    private fun rollbackObservation(plan: Plan, restoreTo: BeliefState, now: Instant): Observation {
        val restored = restoreTo.current(now).sortedWith(compareBy({ it.k }, { it.id }))
        return Observation(
            id = "obs_rollback_${plan.id}",
            executionRef = plan.id,
            t = now,
            facts = restored.map { it.copy(id = "", supersededBy = null, source = "rollback", observedAt = now) }
        )
    }

    private fun failure(
        plan: Plan,
        state: BeliefState,
        observed: List<Claim>,
        action: ActionState?
    ) =
        ExecutionResult(
            Outcome("out_${plan.id}", plan.goalRef, observed, 0.0, "failed"),
            committed = false,
            rolledBack = false,
            state = state,
            action = action
        )
}
