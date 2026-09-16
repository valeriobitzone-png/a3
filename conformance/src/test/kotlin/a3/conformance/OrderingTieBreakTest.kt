// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.conformance

import a3.core.temporal.TemporalOrder
import a3.core.temporal.sort
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderingTieBreakTest {
    @Test
    fun CS_005_total_order_commutes_and_tie_breaks() {
        val stream = ConformanceIo.observationsFrom(
            ConformanceIo.readTree(File(ConformancePaths.vectorsV2(), "tm-order.json"))
        )
        val canonical = sort(stream)
        for (perm in permutations(stream)) {
            assertEquals(
                canonical.map { it.toCanonical() },
                sort(perm).map { it.toCanonical() }
            )
        }
        val equalObserve = ConformanceIo.observationsFrom(
            ConformanceIo.readTree(File(ConformancePaths.fixtures(), "order-tiebreak-violation.json"))
                .path("input")
        )
        val byProtocol = equalObserve.sortedWith(TemporalOrder)
        assertEquals(listOf("alpha", "zeta"), byProtocol.map { it.sourceId.value })
        for (perm in permutations(equalObserve)) {
            assertEquals(
                byProtocol.map { it.sourceId.value },
                perm.sortedWith(TemporalOrder).map { it.sourceId.value }
            )
        }
        val residual = listOf(
            equalObserve[0].copy(sourceId = a3.core.admission.SourceId("same"), subject = "b", key = "k2"),
            equalObserve[1].copy(sourceId = a3.core.admission.SourceId("same"), subject = "a", key = "k1")
        ).map {
            it.copy(seq = 1L)
        }
        val residualSorted = residual.sortedWith(TemporalOrder)
        assertEquals(listOf("a", "b"), residualSorted.map { it.subject })
        println("PASS CS-005 permutations=${permutations(stream).size} tie-break")
    }

    private fun <T> permutations(list: List<T>): List<List<T>> {
        if (list.size <= 1) return listOf(list)
        val out = ArrayList<List<T>>()
        for (i in list.indices) {
            val rest = list.filterIndexed { idx, _ -> idx != i }
            for (p in permutations(rest)) {
                out += listOf(list[i]) + p
            }
        }
        return out
    }
}
