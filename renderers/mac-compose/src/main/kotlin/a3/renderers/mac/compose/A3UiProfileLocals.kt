// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import a3.overlay.A3UiProfile
import a3.overlay.FeatureMatrix
import a3.overlay.MotionMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalA3UiProfile = staticCompositionLocalOf { A3UiProfile.HIGH }
internal val LocalFeatureMatrix = staticCompositionLocalOf { FeatureMatrix.of(A3UiProfile.HIGH) }
internal val LocalParticlesEnabled = staticCompositionLocalOf { true }
internal val LocalSimplifiedMotion = staticCompositionLocalOf { false }
internal val LocalGlassBlurEnabled = staticCompositionLocalOf { true }

@Composable
fun A3UiProfileProvider(
    profile: A3UiProfile = A3UiProfile.HIGH,
    content: @Composable () -> Unit
) {
    val matrix = FeatureMatrix.of(profile)
    CompositionLocalProvider(
        LocalA3UiProfile provides profile,
        LocalFeatureMatrix provides matrix,
        LocalParticlesEnabled provides matrix.particles,
        LocalSimplifiedMotion provides (matrix.motion == MotionMode.SIMPLIFIED),
        LocalGlassBlurEnabled provides matrix.blurEnabled,
        content = content
    )
}
