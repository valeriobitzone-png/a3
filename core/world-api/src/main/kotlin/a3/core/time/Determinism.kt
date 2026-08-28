package a3.core.time

import java.time.Instant

/**
 * Injected clock. Decision path never calls Instant.now().
 */
fun interface InstantSource {
    fun now(): Instant
}

class FixedClock(private val instant: Instant) : InstantSource {
    override fun now(): Instant = instant
}

/**
 * Injected monotonic identifiers. Decision path never uses UUID.randomUUID.
 */
class SequentialIdGenerator(private var seq: Long = 0L) {
    fun next(prefix: String): String {
        seq += 1L
        return "${prefix}_$seq"
    }
}
