package a3.core.truth

import a3.core.admission.AcceptedObservation
import a3.core.admission.SourceType

/**
 * Truth class is a law of the bearer, not decoration. UNKNOWN is first-class.
 */
enum class TruthClass {
    FACT,
    OBSERVATION,
    HYPOTHESIS,
    UNKNOWN
}

enum class Provenance {
    OBSERVED_SIGNED,
    DERIVED_MODEL,
    INFERRED,
    HUMAN_ADMITTED
}

enum class VerificationEnvironment {
    REAL,
    SANDBOX
}

class TruthReject(reason: String) : IllegalArgumentException(reason)

/**
 * Every proposition carries [truthClass] and [provenance]. Neither has a
 * silent default; omit them and [parseBearer] rejects.
 */
data class TruthBearer(
    val truthClass: TruthClass,
    val provenance: Provenance,
    val ref: String? = null
) {
    fun toCanonical(): Map<String, Any?> = mapOf(
        "provenance" to provenance,
        "ref" to ref,
        "truth_class" to truthClass
    )
}

data class VerificationAdmitted(
    val verificationId: String,
    val admittedId: String,
    val environment: VerificationEnvironment
) {
    init {
        require(verificationId.isNotBlank()) { "verificationId must be non-blank" }
        require(admittedId.isNotBlank()) { "admittedId must be non-blank" }
    }

    fun toCanonical(): Map<String, Any?> = mapOf(
        "admitted_id" to admittedId,
        "environment" to environment,
        "verification_id" to verificationId
    )
}

data class AxisProjection(
    val support: String,
    val freshness: String,
    val status: String,
    val action: String
) {
    fun toCanonical(): Map<String, Any?> = mapOf(
        "action" to action,
        "freshness" to freshness,
        "status" to status,
        "support" to support
    )
}

fun parseBearer(fields: Map<String, Any?>): TruthBearer {
    val rawClass = fields["truth_class"] ?: fields["truthClass"]
    val rawProv = fields["provenance"]
    if (rawClass == null) throw TruthReject("missing truthClass")
    if (rawProv == null) throw TruthReject("missing provenance")
    return TruthBearer(
        truthClass = parseEnum<TruthClass>(rawClass, "truthClass"),
        provenance = parseEnum<Provenance>(rawProv, "provenance"),
        ref = fields["ref"]?.toString()
    )
}

fun provenanceFrom(sourceType: SourceType): Provenance = when (sourceType) {
    SourceType.DIRECT -> Provenance.OBSERVED_SIGNED
    SourceType.GROUNDED_BY_MODEL -> Provenance.DERIVED_MODEL
    SourceType.INFERRED -> Provenance.INFERRED
}

fun verificationFromAccepted(
    accepted: AcceptedObservation,
    verificationId: String,
    environment: VerificationEnvironment
): VerificationAdmitted = VerificationAdmitted(
    verificationId = verificationId,
    admittedId = accepted.candidate.id,
    environment = environment
)

private inline fun <reified T : Enum<T>> parseEnum(raw: Any, field: String): T {
    val name = raw.toString().trim().uppercase().replace('-', '_')
    return enumValues<T>().firstOrNull { it.name == name }
        ?: throw TruthReject("unknown $field")
}
