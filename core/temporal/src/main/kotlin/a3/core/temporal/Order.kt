// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.temporal

/**
 * Total order (t_observe, source_id, seq). Lexicographic on source_id then seq.
 * Subject/key break residual ties so equal order-keys still commute.
 */
object TemporalOrder : Comparator<TemporalObservation> {
    override fun compare(a: TemporalObservation, b: TemporalObservation): Int {
        val key = a.orderKey().compareTo(b.orderKey())
        if (key != 0) return key
        val subject = a.subject.compareTo(b.subject)
        if (subject != 0) return subject
        return a.key.compareTo(b.key)
    }
}

fun sort(stream: Iterable<TemporalObservation>): List<TemporalObservation> =
    stream.sortedWith(TemporalOrder)
