// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.truth

/**
 * Promotion, classification, and axis coherence. No clock. No silent defaults.
 */
fun promoteHypothesis(
    bearer: TruthBearer,
    verification: VerificationAdmitted?
): TruthBearer {
    if (bearer.truthClass != TruthClass.HYPOTHESIS) {
        throw TruthReject("only HYPOTHESIS promotes")
    }
    if (verification == null) return bearer
    if (verification.environment == VerificationEnvironment.SANDBOX) {
        return TruthBearer(
            truthClass = TruthClass.OBSERVATION,
            provenance = bearer.provenance,
            ref = verification.verificationId
        )
    }
    return TruthBearer(
        truthClass = TruthClass.FACT,
        provenance = Provenance.OBSERVED_SIGNED,
        ref = verification.verificationId
    )
}

fun fromInsufficient(provenance: Provenance, ref: String? = null): TruthBearer =
    TruthBearer(TruthClass.UNKNOWN, provenance, ref)

fun fromProcessOutcome(
    exitCode: Int,
    printed: String? = null,
    provenance: Provenance = Provenance.OBSERVED_SIGNED
): TruthBearer {
    val label = printed?.trim().orEmpty()
    val ref = if (label.isEmpty()) "exit:$exitCode" else "exit:$exitCode:$label"
    return TruthBearer(TruthClass.OBSERVATION, provenance, ref)
}

fun fromSandbox(verificationId: String, provenance: Provenance): TruthBearer {
    require(verificationId.isNotBlank()) { "verificationId must be non-blank" }
    return TruthBearer(TruthClass.OBSERVATION, provenance, verificationId)
}

fun fromRealAdmitted(verification: VerificationAdmitted): TruthBearer {
    if (verification.environment != VerificationEnvironment.REAL) {
        throw TruthReject("FACT requires real admitted environment")
    }
    return TruthBearer(
        truthClass = TruthClass.FACT,
        provenance = Provenance.OBSERVED_SIGNED,
        ref = verification.verificationId
    )
}

fun projectAxis(bearer: TruthBearer, freshness: String): AxisProjection {
    val fresh = freshness.trim().lowercase()
    require(fresh.isNotBlank()) { "freshness must be explicit" }
    return when (bearer.truthClass) {
        TruthClass.HYPOTHESIS -> AxisProjection(
            support = "medium",
            freshness = fresh,
            status = "held",
            action = "na"
        )
        TruthClass.UNKNOWN -> AxisProjection(
            support = "unknown",
            freshness = fresh,
            status = "unknown",
            action = "unknown"
        )
        TruthClass.OBSERVATION -> AxisProjection(
            support = "medium",
            freshness = fresh,
            status = "held",
            action = "na"
        )
        TruthClass.FACT -> AxisProjection(
            support = "high",
            freshness = fresh,
            status = if (fresh == "stale") "held" else "believed",
            action = "na"
        )
    }
}

fun requireAxisCoherent(bearer: TruthBearer, status: String, freshness: String) {
    val s = status.trim().lowercase()
    val f = freshness.trim().lowercase()
    if (bearer.truthClass == TruthClass.HYPOTHESIS && s == "believed") {
        throw TruthReject("HYPOTHESIS cannot declare status BELIEVED")
    }
    if (bearer.truthClass == TruthClass.UNKNOWN && s == "believed") {
        throw TruthReject("UNKNOWN cannot declare status BELIEVED")
    }
    if (bearer.truthClass == TruthClass.FACT && s == "believed" && f == "stale") {
        throw TruthReject("FACT cannot declare BELIEVED when STALE")
    }
}
