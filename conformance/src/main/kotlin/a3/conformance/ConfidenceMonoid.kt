// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.conformance

import kotlin.math.min

object ConfidenceMonoid {
    const val TOP: Double = 1.0

    fun combine(a: Double, b: Double): Double = min(a, b)
}
