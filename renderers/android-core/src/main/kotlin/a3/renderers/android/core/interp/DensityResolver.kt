// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.core.interp

class DensityResolver {
    fun scale(densityHint: String): Double = when (densityHint) {
        "compact" -> 0.85
        "comfortable" -> 1.0
        "spacious" -> 1.2
        else -> throw IllegalArgumentException("unknown density_hint: $densityHint")
    }
}
