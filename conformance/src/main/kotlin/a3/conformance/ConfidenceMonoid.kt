package a3.conformance

import kotlin.math.min

object ConfidenceMonoid {
    const val TOP: Double = 1.0

    fun combine(a: Double, b: Double): Double = min(a, b)
}
