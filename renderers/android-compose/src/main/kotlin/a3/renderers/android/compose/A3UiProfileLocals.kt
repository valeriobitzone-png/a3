package a3.renderers.android.compose

import a3.overlay.A3UiProfile
import a3.overlay.FeatureMatrix
import a3.overlay.MotionMode
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalA3UiProfile = staticCompositionLocalOf { A3UiProfile.HIGH }
internal val LocalFeatureMatrix = staticCompositionLocalOf { FeatureMatrix.of(A3UiProfile.HIGH) }
internal val LocalParticlesEnabled = staticCompositionLocalOf { true }
internal val LocalSimplifiedMotion = staticCompositionLocalOf { false }

@Composable
fun A3UiProfileProvider(
    profile: A3UiProfile = A3UiProfile.HIGH,
    content: @Composable () -> Unit
) {
    val matrix = FeatureMatrix.of(profile)
    val blurOn = matrix.blurEnabled && Build.VERSION.SDK_INT >= 31
    CompositionLocalProvider(
        LocalA3UiProfile provides profile,
        LocalFeatureMatrix provides matrix,
        LocalParticlesEnabled provides matrix.particles,
        LocalSimplifiedMotion provides (matrix.motion == MotionMode.SIMPLIFIED),
        LocalGlassBlurEnabled provides blurOn,
        content = content
    )
}
