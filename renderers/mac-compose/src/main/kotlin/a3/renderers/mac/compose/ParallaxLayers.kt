// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

/**
 * Three-layer icon. Motion only when a real tilt source exists.
 * No gyroscope / no mouse → stacked static layers, never a fake wobble.
 */
internal object ParallaxLayers {
    const val FG = 0
    const val MID = 1
    const val BG = 2
    const val AMP_PX = 12f
    val DEPTH = floatArrayOf(1f, 0.45f, 0.18f)
    val REST_Y = floatArrayOf(6f, 3f, 0f)

    data class Tilt(val x: Float, val y: Float)

    fun offsetX(layer: Int, tilt: Tilt, available: Boolean): Float {
        val rest = 0f
        if (!available) return rest
        return rest + tilt.x * DEPTH[layer] * AMP_PX
    }

    fun offsetY(layer: Int, tilt: Tilt, available: Boolean): Float {
        val rest = REST_Y[layer]
        if (!available) return rest
        return rest + tilt.y * DEPTH[layer] * AMP_PX
    }

    fun movement(layer: Int, from: Tilt, to: Tilt, available: Boolean): Float {
        val dx = offsetX(layer, to, available) - offsetX(layer, from, available)
        val dy = offsetY(layer, to, available) - offsetY(layer, from, available)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
