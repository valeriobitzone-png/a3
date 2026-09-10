package a3.renderers.android.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import a3.renderers.android.core.model.RendererContext
import a3.renderers.android.core.model.SpringParams

@Composable
fun ComposeMotionApplier(params: SpringParams, content: @Composable () -> Unit) {
    val stage = LocalRendererStage.current
    val crack = LocalCrack.current
    val reduced = LocalReducedMotion.current
    if (reduced) {
        Box(Modifier.fillMaxSize()) { content() }
        return
    }
    val anim = remember { Animatable(1f) }
    LaunchedEffect(stage, crack, params.stiffness, params.damping, params.durationHint) {
        when {
            crack -> {
                anim.snapTo(Theme.crackPeak)
                anim.animateTo(1f, animationSpec = tween(Theme.crackMs))
            }
            stage == RendererContext.STAGE_APPROVA -> {
                anim.animateTo(
                    Theme.holdScale,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = params.stiffness.toFloat()
                    )
                )
            }
            stage == RendererContext.STAGE_ASCOLTO -> {
                anim.snapTo(Theme.listenFrom)
                anim.animateTo(
                    1f,
                    animationSpec = tween(params.durationHint.toInt())
                )
            }
            else -> {
                anim.animateTo(
                    1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = params.stiffness.toFloat()
                    )
                )
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = anim.value
                scaleY = anim.value
            }
    ) {
        content()
    }
}
