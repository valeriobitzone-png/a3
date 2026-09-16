// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.envelope

import a3.core.temporal.TemporalStamp
import a3.core.truth.TruthBearer
import java.time.Instant

const val CLOUD_EVENTS_SPEC = "1.0"
const val DATA_CONTENT_TYPE = "application/json"

class EnvelopeReject(reason: String) : IllegalArgumentException(reason)

data class EnvelopePayload(
    val temporal: TemporalStamp,
    val truth: TruthBearer,
    val content: Any?,
    val foldRef: String? = null,
    val attestation: Attestation? = null
) {
    fun toCanonical(): Map<String, Any?> = buildMap {
        if (attestation != null) put("attestation", attestation.toCanonical())
        put("content", content)
        put("fold_ref", foldRef)
        put("temporal", temporal.toCanonical())
        put("truth", truth.toCanonical())
    }
}

data class CloudEventEnvelope(
    val specversion: String,
    val type: String,
    val source: String,
    val id: String,
    val time: String,
    val datacontenttype: String,
    val subject: String,
    val data: EnvelopePayload
) {
    fun toCanonical(): Map<String, Any?> = mapOf(
        "data" to data.toCanonical(),
        "datacontenttype" to datacontenttype,
        "id" to id,
        "source" to source,
        "specversion" to specversion,
        "subject" to subject,
        "time" to time,
        "type" to type
    )
}

fun parseStamp(fields: Map<String, Any?>): TemporalStamp = TemporalStamp(
    tEvent = instantOf(fields["t_event"], "t_event"),
    tObserve = instantOf(fields["t_observe"], "t_observe"),
    tAdmit = instantOf(fields["t_admit"], "t_admit"),
    tPresent = instantOf(fields["t_present"], "t_present")
)

private fun instantOf(raw: Any?, field: String): Instant {
    if (raw == null) throw EnvelopeReject("missing $field")
    return Instant.parse(raw.toString())
}
