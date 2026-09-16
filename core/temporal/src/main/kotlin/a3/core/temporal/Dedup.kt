// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.temporal

import java.util.TreeMap

/**
 * Same (source_id, subject, key) → last observation by [TemporalOrder] is current.
 * The sorted log is append-only: superseded rows stay.
 */
fun dedup(stream: Iterable<TemporalObservation>): DedupView {
    val log = sort(stream)
    val current = TreeMap<DedupKey, TemporalObservation>()
    for (obs in log) {
        current[obs.dedupKey()] = obs
    }
    return DedupView(log = log, current = current)
}
