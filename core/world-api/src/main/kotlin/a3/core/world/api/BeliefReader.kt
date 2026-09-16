// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.world.api

import java.time.Instant

/**
 * Read-only view of belief. No write, apply, or commit methods.
 * Implemented by committed BeliefState; prediction may only consume this type.
 */
interface BeliefReader {
    val version: Long
    fun validAt(fact: Claim, t: Instant): Boolean
    fun current(t: Instant): List<Claim>
}

/**
 * Snapshot BeliefReader for off-path consumers (prediction).
 */
data class ReadBelief(
    override val version: Long = 0,
    val facts: List<Claim> = emptyList()
) : BeliefReader {
    override fun validAt(fact: Claim, t: Instant): Boolean = fact.validAt(t)

    override fun current(t: Instant): List<Claim> =
        facts.filter { it.validAt(t) }
            .sortedWith(compareBy<Claim> { it.k }.thenBy { it.id })
}
