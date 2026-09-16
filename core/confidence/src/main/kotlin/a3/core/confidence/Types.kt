// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.confidence

import a3.core.truth.Provenance
import a3.core.truth.TruthClass

class ConfidenceReject(reason: String) : IllegalArgumentException(reason)

val CONFIDENCE_DIMENSIONS = listOf(
    "corroboration",
    "evidence_strength",
    "recency",
    "source_reliability",
    "verification"
)

/**
 * Five-dimensional belief calibration. There is no total confidence
 * without the complete vector.
 */
data class ConfidenceVector(
    val sourceReliability: Double,
    val evidenceStrength: Double,
    val recency: Double,
    val corroboration: Double,
    val verification: Double
) {
    init {
        requireDim("source_reliability", sourceReliability)
        requireDim("evidence_strength", evidenceStrength)
        requireDim("recency", recency)
        requireDim("corroboration", corroboration)
        requireDim("verification", verification)
    }

    fun toCanonical(): Map<String, Any?> = mapOf(
        "corroboration" to corroboration,
        "evidence_strength" to evidenceStrength,
        "recency" to recency,
        "source_reliability" to sourceReliability,
        "verification" to verification
    )

    fun asList(): List<Pair<String, Double>> = listOf(
        "source_reliability" to sourceReliability,
        "evidence_strength" to evidenceStrength,
        "recency" to recency,
        "corroboration" to corroboration,
        "verification" to verification
    )
}

enum class ConfidenceCategory {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}

enum class VerificationGrade {
    NONE,
    SANDBOX,
    REAL,
    DETERMINISTIC
}

/**
 * Declared weighted-min coefficients. Not baked into [aggregate];
 * callers pass a config so a weight change is an input, not a code edit.
 */
data class AggregationWeights(
    val sourceReliability: Double,
    val evidenceStrength: Double,
    val recency: Double,
    val corroboration: Double,
    val verification: Double
) {
    init {
        requireDim("weight source_reliability", sourceReliability)
        requireDim("weight evidence_strength", evidenceStrength)
        requireDim("weight recency", recency)
        requireDim("weight corroboration", corroboration)
        requireDim("weight verification", verification)
        if (sourceReliability == 0.0 || evidenceStrength == 0.0 || recency == 0.0 ||
            corroboration == 0.0 || verification == 0.0
        ) {
            throw ConfidenceReject("weights must be positive")
        }
    }

    fun toCanonical(): Map<String, Any?> = mapOf(
        "corroboration" to corroboration,
        "evidence_strength" to evidenceStrength,
        "recency" to recency,
        "source_reliability" to sourceReliability,
        "verification" to verification
    )

    companion object {
        val DEFAULT = AggregationWeights(
            sourceReliability = 1.0,
            evidenceStrength = 1.0,
            recency = 0.8,
            corroboration = 0.6,
            verification = 0.9
        )
        val UNIT = AggregationWeights(
            sourceReliability = 1.0,
            evidenceStrength = 1.0,
            recency = 1.0,
            corroboration = 1.0,
            verification = 1.0
        )
    }
}

data class RecencyLaw(
    val halfLifeSeconds: Long
) {
    init {
        if (halfLifeSeconds <= 0) throw ConfidenceReject("halfLife must be positive")
    }

    fun toCanonical(): Map<String, Any?> = mapOf(
        "half_life_seconds" to halfLifeSeconds
    )

    companion object {
        val DEFAULT = RecencyLaw(halfLifeSeconds = 6L * 3600L)
    }
}

data class ConfidenceConfig(
    val weights: AggregationWeights,
    val recency: RecencyLaw,
    val sourceReliability: Map<Provenance, Double>,
    val verification: Map<VerificationGrade, Double>
) {
    fun toCanonical(): Map<String, Any?> = mapOf(
        "recency" to recency.toCanonical(),
        "source_reliability" to sourceReliability.mapKeys { it.key.name.lowercase() },
        "verification" to verification.mapKeys { it.key.name.lowercase() },
        "weights" to weights.toCanonical()
    )

    companion object {
        val DEFAULT = ConfidenceConfig(
            weights = AggregationWeights.DEFAULT,
            recency = RecencyLaw.DEFAULT,
            sourceReliability = mapOf(
                Provenance.OBSERVED_SIGNED to 1.0,
                Provenance.HUMAN_ADMITTED to 0.8,
                Provenance.INFERRED to 0.5,
                Provenance.DERIVED_MODEL to 0.3
            ),
            verification = mapOf(
                VerificationGrade.NONE to 0.0,
                VerificationGrade.SANDBOX to 0.3,
                VerificationGrade.REAL to 0.8,
                VerificationGrade.DETERMINISTIC to 1.0
            )
        )
    }
}

data class ConfidenceAssessment(
    val vector: ConfidenceVector,
    val score: Double,
    val category: ConfidenceCategory,
    val truthClass: TruthClass,
    val weights: AggregationWeights
) {
    fun toCanonical(): Map<String, Any?> = mapOf(
        "category" to category,
        "score" to score,
        "truth_class" to truthClass,
        "vector" to vector.toCanonical(),
        "weights" to weights.toCanonical()
    )
}

internal fun requireDim(name: String, value: Double) {
    if (value.isNaN() || value.isInfinite()) {
        throw ConfidenceReject("$name is not a finite number")
    }
    if (value < 0.0 || value > 1.0) {
        throw ConfidenceReject("$name out of range")
    }
}
