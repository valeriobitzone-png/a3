package a3.core.prediction.model

import a3.core.model.Fact
import java.time.Instant

/**
 * Distinct from [a3.core.world.BeliefState]. Prepared artifacts are never
 * committable into WorldState; there is no commitToWorldState().
 */
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
    val facts: List<Fact>,
    val score: Double,
    val producedAt: Instant
)

data class PreparedState(
    val id: String,
    val forecastId: String,
    val futureStateId: String,
    val contextRef: String,
    val contextSignature: String,
    val facts: List<Fact>,
    val status: PredictionStatus,
    val preparedAt: Instant,
    val expiresAt: Instant,
    val ttlSeconds: Long
)

data class ProjectionCandidate(
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
