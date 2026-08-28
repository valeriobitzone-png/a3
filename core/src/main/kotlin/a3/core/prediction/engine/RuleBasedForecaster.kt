package a3.core.prediction.engine

import a3.core.capability.Transition
import a3.core.model.Capability
import a3.core.model.CapabilityGraph
import a3.core.model.Fact
import a3.core.model.Goal
import a3.core.prediction.model.Forecast
import a3.core.prediction.model.ForecastCandidate
import a3.core.prediction.model.FutureState
import a3.core.prediction.model.PredictionPolicy
import a3.core.time.SequentialIdGenerator
import a3.core.world.BeliefState
import java.time.Instant
import java.util.ArrayList

data class ForecastBundle(
    val forecast: Forecast,
    val futureStates: List<FutureState>
)

/**
 * Deterministic one-step forecaster. No ML. Capability order is TreeMap id order
 * via [Transition.orderedCapabilities]. Ranking key: score desc, capability_ref
 * asc, id asc.
 */
class RuleBasedForecaster {
    fun forecast(
        contextRef: String,
        contextSignature: String,
        belief: BeliefState,
        graph: CapabilityGraph,
        policy: PredictionPolicy,
        now: Instant,
        ids: SequentialIdGenerator,
        goal: Goal? = null
    ): ForecastBundle {
        val ranked = ArrayList<Pair<ForecastCandidate, FutureState>>()
        for (cap in Transition.orderedCapabilities(graph.capabilities)) {
            if (!Transition.canApply(belief, cap, now)) continue
            val score = score(cap, goal)
            if (score < policy.minScore) continue
            val simulated = Transition.apply(belief, cap, now)
            val facts = simulated.current(now).sortedWith(FACT_ORDER)
            val fsId = ids.next("fs")
            val candId = ids.next("fc")
            val future = FutureState(
                id = fsId,
                contextRef = contextRef,
                capabilityRef = cap.id,
                facts = facts,
                score = score,
                producedAt = now
            )
            val candidate = ForecastCandidate(
                id = candId,
                futureStateId = fsId,
                capabilityRef = cap.id,
                score = score,
                rank = 0
            )
            ranked += candidate to future
        }
        ranked.sortWith(BUNDLE_ORDER)
        val limited = if (ranked.size > policy.maxCandidates) {
            ArrayList(ranked.subList(0, policy.maxCandidates))
        } else {
            ranked
        }
        val candidates = ArrayList<ForecastCandidate>(limited.size)
        val futures = ArrayList<FutureState>(limited.size)
        for (index in limited.indices) {
            val (candidate, future) = limited[index]
            candidates += candidate.copy(rank = index + 1)
            futures += future
        }
        val forecast = Forecast(
            id = ids.next("forecast"),
            contextRef = contextRef,
            contextSignature = contextSignature,
            candidates = candidates,
            producedAt = now,
            policyRef = policy.id
        )
        return ForecastBundle(forecast, futures)
    }

    companion object {
        val FACT_ORDER = compareBy<Fact> { it.k }
            .thenBy { it.observedAt }
            .thenBy { it.id }
            .thenBy { it.source }

        val CANDIDATE_ORDER = compareByDescending<ForecastCandidate> { it.score }
            .thenBy { it.capabilityRef }
            .thenBy { it.id }

        private val BUNDLE_ORDER = compareByDescending<Pair<ForecastCandidate, FutureState>> { it.first.score }
            .thenBy { it.first.capabilityRef }
            .thenBy { it.first.id }

        fun score(cap: Capability, goal: Goal?): Double {
            val denom = 1.0 + cap.cost.money + cap.cost.timeMin
            val base = cap.reliability / denom
            val boost = if (goal != null && overlapsGoal(cap, goal)) 1.0 + goal.priority else 1.0
            return base * boost
        }

        fun overlapsGoal(cap: Capability, goal: Goal): Boolean =
            cap.effects.any { effect ->
                goal.desiredState.any { desired -> desired.k == effect.k && desired.v == effect.v }
            }
    }
}
