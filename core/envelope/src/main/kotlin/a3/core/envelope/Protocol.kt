package a3.core.envelope

const val TYPE_BELIEF_ADMITTED = "io.a3ep.belief.admitted"
const val TYPE_ACTION_AUTHORIZED = "io.a3ep.action.authorized"
const val TYPE_ENV_POSTCONDITION = "io.a3ep.env.postcondition"

val CORE_TYPES: Set<String> = setOf(
    TYPE_BELIEF_ADMITTED,
    TYPE_ACTION_AUTHORIZED,
    TYPE_ENV_POSTCONDITION
)

private val TYPE_ALIASES: Map<String, String> = mapOf(
    "a3.belief.admitted" to TYPE_BELIEF_ADMITTED,
    "a3.action.authorized" to TYPE_ACTION_AUTHORIZED,
    "a3.observation.admitted" to TYPE_ENV_POSTCONDITION,
    "a3.env.postcondition" to TYPE_ENV_POSTCONDITION,
    TYPE_BELIEF_ADMITTED to TYPE_BELIEF_ADMITTED,
    TYPE_ACTION_AUTHORIZED to TYPE_ACTION_AUTHORIZED,
    TYPE_ENV_POSTCONDITION to TYPE_ENV_POSTCONDITION
)

fun normalizeType(raw: String): String {
    val key = raw.trim()
    if (key.isEmpty()) throw EnvelopeReject("type must be non-blank")
    return TYPE_ALIASES[key] ?: key
}

fun isCoreType(raw: String): Boolean = normalizeType(raw) in CORE_TYPES

fun isIrreversibleType(raw: String): Boolean =
    normalizeType(raw) == TYPE_ACTION_AUTHORIZED

data class Attestation(
    val attesterId: String,
    val requesterId: String
) {
    init {
        if (attesterId.isBlank()) throw EnvelopeReject("attester_id must be non-blank")
        if (requesterId.isBlank()) throw EnvelopeReject("requester_id must be non-blank")
    }

    fun toCanonical(): Map<String, Any?> = mapOf(
        "attester_id" to attesterId,
        "requester_id" to requesterId
    )
}

fun parseAttestation(raw: Any?): Attestation? {
    if (raw == null) return null
    val map = raw as? Map<*, *> ?: throw EnvelopeReject("attestation must be an object")
    val attester = map["attester_id"]?.toString()?.trim().orEmpty()
    val requester = map["requester_id"]?.toString()?.trim().orEmpty()
    if (attester.isEmpty() || requester.isEmpty()) {
        throw EnvelopeReject("attestation missing attester_id or requester_id")
    }
    return Attestation(attester, requester)
}

fun validateAttestation(attestation: Attestation, irreversible: Boolean) {
    if (irreversible && attestation.attesterId == attestation.requesterId) {
        throw EnvelopeReject("attester_id must not equal requester_id on irreversible action")
    }
}

const val LOCK_V2_ATTESTER = "urn:a3:party:attester"
const val LOCK_V2_REQUESTER = "urn:a3:party:requester"

fun lockV2Attestation(): Attestation = Attestation(LOCK_V2_ATTESTER, LOCK_V2_REQUESTER)
