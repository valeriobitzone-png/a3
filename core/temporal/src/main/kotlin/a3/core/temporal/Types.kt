// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.temporal

import a3.core.admission.AcceptedObservation
import a3.core.admission.SourceId
import java.time.Instant

/**
 * Explicit four-time stamp. Callers supply every instant; this type never
 * reads a clock. [tPresent] is an independent asOf at the present boundary.
 */
data class TemporalStamp(
    val tEvent: Instant,
    val tObserve: Instant,
    val tAdmit: Instant,
    val tPresent: Instant
) {
    init {
        if (tObserve.isBefore(tEvent)) {
            throw TemporalReject("t_observe < t_event")
        }
        if (tAdmit.isBefore(tObserve)) {
            throw TemporalReject("t_admit < t_observe")
        }
    }

    fun toCanonical(): Map<String, Any?> = mapOf(
        "t_admit" to tAdmit,
        "t_event" to tEvent,
        "t_observe" to tObserve,
        "t_present" to tPresent
    )
}

class TemporalReject(reason: String) : IllegalArgumentException(reason)

data class TemporalObservation(
    val sourceId: SourceId,
    val subject: String,
    val key: String,
    val seq: Long,
    val stamp: TemporalStamp,
    val value: Any?
) {
    init {
        require(subject.isNotBlank()) { "subject must be non-blank" }
        require(key.isNotBlank()) { "key must be non-blank" }
    }

    fun dedupKey(): DedupKey = DedupKey(sourceId.value, subject, key)

    fun orderKey(): OrderKey = OrderKey(stamp.tObserve, sourceId.value, seq)

    fun toCanonical(): Map<String, Any?> = mapOf(
        "key" to key,
        "seq" to seq,
        "source_id" to sourceId.value,
        "stamp" to stamp.toCanonical(),
        "subject" to subject,
        "value" to value
    )
}

data class OrderKey(
    val tObserve: Instant,
    val sourceId: String,
    val seq: Long
) : Comparable<OrderKey> {
    override fun compareTo(other: OrderKey): Int {
        val t = tObserve.compareTo(other.tObserve)
        if (t != 0) return t
        val s = sourceId.compareTo(other.sourceId)
        if (s != 0) return s
        return seq.compareTo(other.seq)
    }
}

data class DedupKey(
    val sourceId: String,
    val subject: String,
    val key: String
) : Comparable<DedupKey> {
    override fun compareTo(other: DedupKey): Int =
        compareValuesBy(this, other, { it.sourceId }, { it.subject }, { it.key })
}

data class FoldField(
    val key: String,
    val value: Any?,
    val sourceId: SourceId,
    val seq: Long,
    val tObserve: Instant
) {
    fun toCanonical(): Map<String, Any?> = mapOf(
        "key" to key,
        "seq" to seq,
        "source_id" to sourceId.value,
        "t_observe" to tObserve,
        "value" to value
    )
}

data class ObservationFold(
    val subject: String,
    val fields: Map<String, FoldField>,
    val history: List<TemporalObservation>
) {
    fun toCanonical(): Map<String, Any?> = mapOf(
        "fields" to fields.mapValues { it.value.toCanonical() },
        "history" to history.map { it.toCanonical() },
        "subject" to subject
    )
}

data class DedupView(
    val log: List<TemporalObservation>,
    val current: Map<DedupKey, TemporalObservation>
)

fun stampFromAccepted(accepted: AcceptedObservation, tPresent: Instant): TemporalStamp =
    TemporalStamp(
        tEvent = accepted.candidate.occurredAt,
        tObserve = accepted.candidate.observedAt,
        tAdmit = accepted.admittedAt,
        tPresent = tPresent
    )

fun observationFromAccepted(
    accepted: AcceptedObservation,
    subject: String,
    key: String,
    seq: Long,
    tPresent: Instant,
    value: Any?
): TemporalObservation = TemporalObservation(
    sourceId = accepted.candidate.source,
    subject = subject,
    key = key,
    seq = seq,
    stamp = stampFromAccepted(accepted, tPresent),
    value = value
)
