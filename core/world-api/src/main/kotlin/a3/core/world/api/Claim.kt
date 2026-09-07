package a3.core.world.api

import java.time.Instant

/**
 * Belief atom (read-only). Schema fields: k, v, confidence, source, observed_at, expires_at.
 * [id] and [supersededBy] are history metadata omitted from SCHEMA JSON.
 *
 * validAt(f,t) := not superseded AND observed_at <= t AND (no expiry OR t < expires_at)
 */
data class Claim(
    val k: String,
    val v: Any?,
    val confidence: Double,
    val source: String,
    val observedAt: Instant,
    val expiresAt: Instant? = null,
    val id: String = "",
    val supersededBy: String? = null
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be in [0,1]" }
    }

    fun validAt(t: Instant): Boolean {
        if (supersededBy != null) return false
        if (observedAt.isAfter(t)) return false
        val expiry = expiresAt
        if (expiry != null && !t.isBefore(expiry)) return false
        return true
    }
}

val FACT_ORDER = compareBy<Claim> { it.k }
    .thenBy { it.observedAt }
    .thenBy { it.id }
    .thenBy { it.source }
