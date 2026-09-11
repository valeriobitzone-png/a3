package a3.core.temporal

import a3.core.json.CanonicalJson
import java.util.TreeMap

/**
 * Structural merge per subject. No combined confidence.
 * [fold] is commutative over arrival order: fold(π(stream)) == fold(stream).
 */
fun fold(stream: Iterable<TemporalObservation>): List<ObservationFold> {
    val view = dedup(stream)
    val bySubject = view.log.groupBy { it.subject }
    return bySubject.keys.sorted().map { subject ->
        foldSubject(subject, bySubject.getValue(subject), view)
    }
}

fun foldBytes(stream: Iterable<TemporalObservation>): ByteArray =
    CanonicalJson.bytes(fold(stream).map { it.toCanonical() })

fun ObservationFold.canonicalBytes(): ByteArray =
    CanonicalJson.bytes(toCanonical())

private fun foldSubject(
    subject: String,
    history: List<TemporalObservation>,
    view: DedupView
): ObservationFold {
    val ranked = view.current.values
        .filter { it.subject == subject }
        .sortedWith(TemporalOrder)
    val fields = TreeMap<String, FoldField>()
    for (obs in ranked) {
        fields[obs.key] = FoldField(
            key = obs.key,
            value = obs.value,
            sourceId = obs.sourceId,
            seq = obs.seq,
            tObserve = obs.stamp.tObserve
        )
    }
    return ObservationFold(subject = subject, fields = fields, history = history)
}
