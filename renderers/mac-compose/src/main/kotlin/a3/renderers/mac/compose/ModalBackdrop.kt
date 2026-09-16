// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

/**
 * Modal DoF: blur + darken from acrylic / glass tokens, sprung with S-D2 ODE.
 * Overlay alpha is glass.fillOpacity, never an invented dim.
 */
internal object ModalBackdrop {
    fun blurPx(open: Float): Float = GraphicsTokens.snapshot.acrylicBlurPx * open.coerceIn(0f, 1.2f)

    fun overlayAlpha(open: Float): Float =
        GraphicsTokens.snapshot.fillOpacity * open.coerceIn(0f, 1f)

    fun springOpen(
        from: Float = 0f,
        to: Float = 1f,
        compactOvershoot: Boolean = false,
        reduced: Boolean = false
    ): List<MotionPhysics.State> {
        val s = if (compactOvershoot) GraphicsTokens.motion.compact else GraphicsTokens.motion.comfortable
        return MotionPhysics.integrateSettled(from, to, s.mass, s.stiffness, s.damping, reduced)
    }
}
