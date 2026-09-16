// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.confidence

import a3.core.temporal.TemporalStamp
import a3.core.truth.Provenance
import a3.core.truth.TruthBearer
import a3.core.truth.TruthClass
import a3.core.truth.VerificationEnvironment
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

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

/**
 * `2^(-(t_present - t_observe) / half_life)`, age clamped at 0.
 *
 * JDK libm `pow` (the same bits as StrictMath on 21) sits 1 ULP above
 * the IEEE-754 binary64 nearest-even value of the exact power for
 * Δt=10799s (`0.7071294727113613` vs `0.7071294727113612`). Recency is
 * therefore `exp(ln(2) * -age/halfLife)` in [BigDecimal] (80 decimal
 * digits, HALF_EVEN) then rounded to binary64 via [BigDecimal.doubleValue],
 * which matches ECMAScript/CPython correctly rounded exponentiation.
 * Integer multiples of the half-life stay exact via [StrictMath.scalb].
 */
fun recencyFromAge(ageSeconds: Long, law: RecencyLaw = RecencyLaw.DEFAULT): Double {
    val age = if (ageSeconds < 0L) 0L else ageSeconds
    if (age == 0L) return 1.0
    val halfLife = law.halfLifeSeconds
    if (age % halfLife == 0L) {
        val generations = age / halfLife
        if (generations <= 1023L) return StrictMath.scalb(1.0, -generations.toInt())
    }
    return exp2Rational(-age, halfLife)
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

private val RECENCY_MATH = MathContext(80, RoundingMode.HALF_EVEN)

/** ln(2) to >80 digits (OEIS A002162). */
private val LN2 = BigDecimal(
    "0.693147180559945309417232121458176568075500134360255254120680009493393621969694715605863326996418687"
)

private fun exp2Rational(numerator: Long, denominator: Long): Double {
    val exponent = BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), RECENCY_MATH)
    return exp(exponent.multiply(LN2, RECENCY_MATH)).toDouble()
}

private fun exp(x: BigDecimal): BigDecimal {
    var term = BigDecimal.ONE
    var sum = BigDecimal.ONE
    var n = 1
    while (n <= 256) {
        term = term.multiply(x, RECENCY_MATH).divide(BigDecimal.valueOf(n.toLong()), RECENCY_MATH)
        val next = sum.add(term, RECENCY_MATH)
        if (next.compareTo(sum) == 0) return next
        sum = next
        n++
    }
    return sum
}
