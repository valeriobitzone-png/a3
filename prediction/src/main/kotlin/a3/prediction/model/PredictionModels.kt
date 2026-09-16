// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.prediction.model

import a3.core.world.api.Claim
import java.time.Instant

enum class PredictionStatus {
    PREPARED,
    EXPIRED,
    INVALIDATED;

    fun wire(): String = name.lowercase()
}

data class PredictionPolicy(
    val id: String,
    val ttlSeconds: Long = 300,
    val maxCandidates: Int = 8,
    val minScore: Double = 0.0
) {
    init {
        require(ttlSeconds > 0) { "ttl_seconds must be > 0" }
        require(maxCandidates > 0) { "max_candidates must be > 0" }
        require(minScore in 0.0..1.0) { "min_score must be in [0,1]" }
    }
}

data class ForecastCandidate(
    val id: String,
    val futureStateId: String,
    val capabilityRef: String,
    val score: Double,
    val rank: Int
)

data class Forecast(
    val id: String,
    val contextRef: String,
    val contextSignature: String,
    val candidates: List<ForecastCandidate>,
    val producedAt: Instant,
    val policyRef: String? = null
)

data class FutureState(
    val id: String,
    val contextRef: String,
    val capabilityRef: String,
    val facts: List<Claim>,
    val score: Double,
    val producedAt: Instant
)

/**
 * Prefetch artifact. Distinct from BeliefState. No commit / toWorldState / toBeliefState.
 * No may_commit flag: the type cannot reach BeliefWriter.apply (no overload exists).
 */
data class PreparedState(
    val id: String,
    val forecastId: String,
    val futureStateId: String,
    val contextRef: String,
    val contextSignature: String,
    val facts: List<Claim>,
    val status: PredictionStatus,
    val preparedAt: Instant,
    val expiresAt: Instant,
    val ttlSeconds: Long
)

data class PrefetchHint(
    val id: String,
    val futureStateRef: String,
    val contextRef: String,
    val uiStateHint: String
)

data class PredictionInvalidation(
    val id: String,
    val preparedIds: List<String>,
    val contextRef: String,
    val contextSignature: String,
    val reason: String,
    val t: Instant
)

data class CapabilityHint(
    val id: String,
    val preconditions: List<Claim>,
    val effects: List<Claim>,
    val reliability: Double,
    val money: Double = 0.0,
    val timeMin: Double = 0.0
)

data class DesiredAtom(val k: String, val v: Any?)

data class GoalHint(
    val id: String,
    val desired: List<DesiredAtom> = emptyList(),
    val priority: Double = 0.0
)
