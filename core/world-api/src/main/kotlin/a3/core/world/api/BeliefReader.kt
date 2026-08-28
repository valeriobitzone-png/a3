package a3.core.world.api

import java.time.Instant

/**
 * Read-only view of belief. No write, apply, or commit methods.
 * Implemented by committed BeliefState; prediction may only consume this type.
 */
interface BeliefReader {
    val version: Long
    fun validAt(fact: Fact, t: Instant): Boolean
    fun current(t: Instant): List<Fact>
}

/**
 * Snapshot BeliefReader for off-path consumers (prediction). Not WorldState.
 */
data class ReadBelief(
    override val version: Long = 0,
    val facts: List<Fact> = emptyList()
) : BeliefReader {
    override fun validAt(fact: Fact, t: Instant): Boolean = fact.validAt(t)

    override fun current(t: Instant): List<Fact> =
        facts.filter { it.validAt(t) }
            .sortedWith(compareBy<Fact> { it.k }.thenBy { it.id })
}
