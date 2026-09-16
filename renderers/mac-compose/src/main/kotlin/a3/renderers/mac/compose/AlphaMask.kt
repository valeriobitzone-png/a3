// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import kotlin.math.min

/**
 * Vector edge fade for text/chrome. Alpha ramps at left and right edges.
 */
internal object AlphaMask {
    const val FADE_PX = 16f

    fun edge(x: Float, width: Float, fade: Float = FADE_PX): Float {
        val w = width.coerceAtLeast(1f)
        val f = fade.coerceAtLeast(1f)
        val left = (x / f).coerceIn(0f, 1f)
        val right = ((w - x) / f).coerceIn(0f, 1f)
        return min(left, right)
    }
}
