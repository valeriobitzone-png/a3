// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build

/**
 * Android S+ chain: Gaussian blur then vibrancy. Null below API 31 (honest fallback).
 */
internal object AndroidGlassEffect {
    fun create(
        tokens: GraphicsTokens.Snapshot = GraphicsTokens.snapshot,
        blurRadiusPx: Float = tokens.blurRadiusPx
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < 31) return null
        if (blurRadiusPx <= 0f) return null
        val blur = RenderEffect.createBlurEffect(
            blurRadiusPx,
            blurRadiusPx,
            Shader.TileMode.CLAMP
        )
        val matrix = ColorMatrix()
        val s = tokens.vibrancySaturation
        val inv = 1f - s
        val r = 0.213f * inv
        val g = 0.715f * inv
        val b = 0.072f * inv
        matrix.set(
            floatArrayOf(
                r + s, g, b, 0f, 0f,
                r, g + s, b, 0f, 0f,
                r, g, b + s, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val vibrancy = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
        return RenderEffect.createChainEffect(vibrancy, blur)
    }
}
