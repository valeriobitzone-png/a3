package a3.renderers.android.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import a3.renderers.android.core.model.SpringParams

@Composable
fun ComposeMotionApplier(params: SpringParams, content: @Composable () -> Unit) {
    val anim = remember { Animatable(1f) }
    LaunchedEffect(params.stiffness, params.damping) {
        anim.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = params.stiffness.toFloat()
            )
        )
    }
    content()
}
