package a3.core.confidence

import a3.core.temporal.TemporalStamp
import a3.core.truth.Provenance
import a3.core.truth.TruthBearer
import a3.core.truth.TruthClass
import a3.core.truth.VerificationEnvironment
import kotlin.math.max
import kotlin.math.pow

fun parseVector(fields: Map<String, Any?>): ConfidenceVector {
    fun req(name: String): Double {
        val raw = fields[name] ?: throw ConfidenceReject("missing $name")
        val value = when (raw) {
            is Number -> raw.toDouble()
            else -> raw.toString().toDoubleOrNull()
                ?: throw ConfidenceReject("$name is not a number")
        }
        return value
    }
    return ConfidenceVector(
        sourceReliability = req("source_reliability"),
        evidenceStrength = req("evidence_strength"),
        recency = req("recency"),
        corroboration = req("corroboration"),
        verification = req("verification")
    )
}

/**
 * Non-compensatory weighted minimum. A zero dimension yields score 0
 * regardless of the other four. Weights come from [weights], never from
 * a hidden average.
 */
fun aggregate(vector: ConfidenceVector, weights: AggregationWeights): Double {
    val products = doubleArrayOf(
        vector.sourceReliability * weights.sourceReliability,
        vector.evidenceStrength * weights.evidenceStrength,
        vector.recency * weights.recency,
        vector.corroboration * weights.corroboration,
        vector.verification * weights.verification
    )
    var min = products[0]
    for (i in 1 until products.size) {
        if (products[i] < min) min = products[i]
    }
    return min
}

fun recencyFromAge(ageSeconds: Long, law: RecencyLaw = RecencyLaw.DEFAULT): Double {
    val age = max(0.0, ageSeconds.toDouble())
    return 2.0.pow(-age / law.halfLifeSeconds.toDouble())
}

fun recency(stamp: TemporalStamp, law: RecencyLaw = RecencyLaw.DEFAULT): Double {
    val ageSeconds = stamp.tPresent.epochSecond - stamp.tObserve.epochSecond
    return recencyFromAge(ageSeconds, law)
}

/**
 * Independent sources are distinct source_id values. Repeating the same
 * id does not corroborate. Discordant evidence is 0.
 */
fun corroboration(sourceIds: List<String>, concordant: Boolean = true): Double {
    if (!concordant) return 0.0
    val independent = sourceIds
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSet()
        .size
    return when {
        independent <= 1 -> 0.0
        independent == 2 -> 0.5
        else -> 1.0
    }
}

fun evidenceStrength(observationCount: Int, concordant: Boolean = true): Double {
    if (observationCount <= 0) return 0.0
    if (!concordant) return 0.0
    return if (observationCount == 1) 0.5 else 1.0
}

fun sourceReliability(
    provenance: Provenance,
    config: ConfidenceConfig = ConfidenceConfig.DEFAULT
): Double = config.sourceReliability[provenance]
    ?: throw ConfidenceReject("missing source_reliability for $provenance")

fun verificationScore(
    grade: VerificationGrade,
    config: ConfidenceConfig = ConfidenceConfig.DEFAULT
): Double = config.verification[grade]
    ?: throw ConfidenceReject("missing verification for $grade")

fun verificationGrade(environment: VerificationEnvironment): VerificationGrade =
    when (environment) {
        VerificationEnvironment.SANDBOX -> VerificationGrade.SANDBOX
        VerificationEnvironment.REAL -> VerificationGrade.REAL
    }

/**
 * Declarative mapping. Alta requires FACT and score >= 0.8.
 * Score 0 is UNKNOWN as a first-class category, independent of truth class.
 */
fun categoryOf(score: Double, truthClass: TruthClass): ConfidenceCategory {
    requireDim("score", score)
    if (score == 0.0) return ConfidenceCategory.UNKNOWN
    if (score >= 0.8 && truthClass == TruthClass.FACT) return ConfidenceCategory.HIGH
    if (score >= 0.5) return ConfidenceCategory.MEDIUM
    return ConfidenceCategory.LOW
}

fun vectorFrom(
    stamp: TemporalStamp,
    provenance: Provenance,
    sourceIds: List<String>,
    observationCount: Int,
    concordant: Boolean,
    verification: VerificationGrade,
    config: ConfidenceConfig = ConfidenceConfig.DEFAULT
): ConfidenceVector = ConfidenceVector(
    sourceReliability = sourceReliability(provenance, config),
    evidenceStrength = evidenceStrength(observationCount, concordant),
    recency = recency(stamp, config.recency),
    corroboration = corroboration(sourceIds, concordant),
    verification = verificationScore(verification, config)
)

fun assess(
    bearer: TruthBearer,
    stamp: TemporalStamp,
    sourceIds: List<String>,
    observationCount: Int,
    concordant: Boolean,
    verification: VerificationGrade,
    config: ConfidenceConfig = ConfidenceConfig.DEFAULT
): ConfidenceAssessment {
    val vector = vectorFrom(
        stamp,
        bearer.provenance,
        sourceIds,
        observationCount,
        concordant,
        verification,
        config
    )
    val score = aggregate(vector, config.weights)
    return ConfidenceAssessment(
        vector = vector,
        score = score,
        category = categoryOf(score, bearer.truthClass),
        truthClass = bearer.truthClass,
        weights = config.weights
    )
}

fun assessVector(
    vector: ConfidenceVector,
    truthClass: TruthClass,
    weights: AggregationWeights = AggregationWeights.DEFAULT
): ConfidenceAssessment {
    val score = aggregate(vector, weights)
    return ConfidenceAssessment(
        vector = vector,
        score = score,
        category = categoryOf(score, truthClass),
        truthClass = truthClass,
        weights = weights
    )
}
