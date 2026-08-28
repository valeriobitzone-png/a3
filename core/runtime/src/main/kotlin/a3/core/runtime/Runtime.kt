package a3.core.runtime

import a3.core.events.EventLog
import a3.core.model.*
import a3.core.policy.Policy
import a3.core.policy.PolicyDecision
import a3.core.schema.ModelValidator
import a3.core.trust.TrustGate
import a3.core.world.BeliefState
import a3.core.world.IntegrateMode
import a3.core.world.ObservationAcceptance
import java.time.Instant
import java.util.TreeMap

data class ExecutionResult(
    val outcome: Outcome,
    val committed: Boolean,
    val rolledBack: Boolean,
    val state: BeliefState
)

fun interface Executor {
    fun execute(capability: Capability, now: Instant): Observation
}

class Runtime(
    private val policy: Policy,
    private val trustGate: TrustGate,
    private val events: EventLog
) {
    fun execute(
        plan: Plan,
        initial: BeliefState,
        capabilities: Map<String, Capability>,
        executor: Executor,
        now: Instant,
        grants: Map<String, TrustGrant> = emptyMap()
    ): ExecutionResult {
        ModelValidator.plan(plan)
        val caps = TreeMap(capabilities)
        val grantIndex = TreeMap(grants)
        var state = initial
        val before = initial
        val observed = ArrayList<Fact>()
        var lastEventId: String? = null

        fun append(event: Event) {
            events.append(event)
            lastEventId = event.id
        }

        for (step in plan.steps.sortedBy { it.seq }) {
            val cap = caps[step.capability]
                ?: return failure(plan, before, observed)

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
                return failure(plan, before, observed)
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

            val observation = executor.execute(cap, now)
            ModelValidator.observation(observation)
            observed += observation.facts

            if (!matchesExpected(cap.effects, observation.facts)) {
                val rolled = if (state.version == before.version) {
                    state
                } else {
                    compensate(state, before, plan, now)
                }
                append(
                    Event(
                        id = "ev_${step.seq}_rollback",
                        t = now,
                        source = "runtime",
                        type = "execution.rolled_back",
                        causalId = lastEventId,
                        stateVersion = rolled.version
                    )
                )
                if (rolled.version != state.version) {
                    val compensation = rollbackObservation(plan, before, now)
                    append(
                        Event(
                            id = "ev_${step.seq}_state",
                            t = now,
                            source = "runtime",
                            type = "state.updated",
                            causalId = lastEventId,
                            stateVersion = rolled.version,
                            payload = compensation
                        )
                    )
                }
                return ExecutionResult(
                    Outcome("out_${plan.id}", plan.goalRef, observed, 0.0, "failed"),
                    committed = false,
                    rolledBack = true,
                    state = rolled
                )
            }

            state = ObservationAcceptance.apply(state, observation)
            append(
                Event(
                    id = "ev_${step.seq}_commit",
                    t = now,
                    source = "runtime",
                    type = "execution.committed",
                    causalId = lastEventId,
                    stateVersion = state.version
                )
            )
            append(
                Event(
                    id = "ev_${step.seq}_state",
                    t = now,
                    source = "runtime",
                    type = "state.updated",
                    causalId = lastEventId,
                    stateVersion = state.version,
                    payload = observation
                )
            )
        }

        return ExecutionResult(
            Outcome("out_${plan.id}", plan.goalRef, observed, 1.0, "achieved"),
            committed = true,
            rolledBack = false,
            state = state
        )
    }

    fun eventLog(): EventLog = events

    private fun matchesExpected(expected: List<Fact>, observed: List<Fact>): Boolean {
        val actual = TreeMap<String, Any?>()
        for (fact in observed.sortedWith(compareBy({ it.k }, { it.id }))) {
            actual[fact.k] = fact.v
        }
        return expected.sortedBy { it.k }.all { fact -> actual[fact.k] == fact.v }
    }

    private fun compensate(
        committed: BeliefState,
        restoreTo: BeliefState,
        plan: Plan,
        now: Instant
    ): BeliefState {
        val observation = rollbackObservation(plan, restoreTo, now)
        return ObservationAcceptance.apply(committed, observation, IntegrateMode.COMPENSATE)
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

    private fun failure(plan: Plan, state: BeliefState, observed: List<Fact>) =
        ExecutionResult(
            Outcome("out_${plan.id}", plan.goalRef, observed, 0.0, "failed"),
            committed = false,
            rolledBack = false,
            state = state
        )
}
