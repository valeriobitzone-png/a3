// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

/**
 * Variable type axes from typography.json: wght, opsz.
 * Width is the renderer mapping of density_hint onto condensed/expanded.
 * Pressed raises wght by 50. Optical sizing: smaller type gets wider tracking.
 */
internal object DynamicType {
    const val PRESSED_DELTA = 50
    const val WIDTH_COMPACT = 0.88f
    const val WIDTH_COMFORTABLE = 1f
    const val WIDTH_SPACIOUS = 1.06f

    fun baseWeight(): Int = GraphicsTokens.typography.weight

    fun weight(pressed: Boolean): Int {
        val base = baseWeight()
        return if (pressed) base + PRESSED_DELTA else base
    }

    fun width(densityHint: String): Float = when (densityHint) {
        "compact" -> WIDTH_COMPACT
        "spacious" -> WIDTH_SPACIOUS
        else -> WIDTH_COMFORTABLE
    }

    fun condensed(densityHint: String): Boolean = width(densityHint) < WIDTH_COMFORTABLE

    /** Tracking in em. 18sp (fase1Fallback) is zero. 12sp > 24sp. */
    fun trackingEm(sizeSp: Float): Float {
        val ref = GraphicsTokens.typography.sizeSp.toFloat()
        return 0.08f * (ref / sizeSp.coerceAtLeast(1f) - 1f)
    }

    fun trackingPx(sizeSp: Float, density: Float = 1f): Float =
        trackingEm(sizeSp) * sizeSp * density
}
