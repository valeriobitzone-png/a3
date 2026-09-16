// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.prediction.engine

import a3.core.time.SequentialIdGenerator
import a3.core.world.api.BeliefReader
import a3.prediction.model.CapabilityHint
import a3.prediction.model.Forecast
import a3.prediction.model.ForecastCandidate
import a3.prediction.model.FutureState
import a3.prediction.model.GoalHint
import a3.prediction.model.PredictionPolicy
import java.time.Instant
import java.util.ArrayList

data class ForecastBundle(
    val forecast: Forecast,
    val futureStates: List<FutureState>
)

/**
 * Deterministic one-step forecaster. No ML. Capability order is TreeMap id order.
 * Ranking key: score desc, capability_ref asc, id asc.
 */
class RuleBasedForecaster {
    fun forecast(
        contextRef: String,
        contextSignature: String,
        belief: BeliefReader,
        capabilities: List<CapabilityHint>,
        policy: PredictionPolicy,
        now: Instant,
        ids: SequentialIdGenerator,
        goal: GoalHint? = null
    ): ForecastBundle {
        val ranked = ArrayList<Pair<ForecastCandidate, FutureState>>()
        for (cap in CapabilityHints.ordered(capabilities)) {
            if (!CapabilityHints.canApply(belief, cap, now)) continue
            val score = score(cap, goal)
            if (score < policy.minScore) continue
            val facts = CapabilityHints.hypothesize(belief, cap, now, score)
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
        val CANDIDATE_ORDER = compareByDescending<ForecastCandidate> { it.score }
            .thenBy { it.capabilityRef }
            .thenBy { it.id }

        private val BUNDLE_ORDER = compareByDescending<Pair<ForecastCandidate, FutureState>> { it.first.score }
            .thenBy { it.first.capabilityRef }
            .thenBy { it.first.id }

        fun score(cap: CapabilityHint, goal: GoalHint?): Double {
            val denom = 1.0 + cap.money + cap.timeMin
            val base = cap.reliability / denom
            val boost = if (goal != null && CapabilityHints.overlapsGoal(cap, goal)) {
                1.0 + goal.priority
            } else {
                1.0
            }
            return base * boost
        }
    }
}
