package a3.renderers.mac.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import a3.renderers.android.core.model.RenderedNode

@Composable
fun Modifier.macMotion(node: RenderedNode): Modifier {
    val axis = MacExposure.of(node)
    val reduced = LocalReducedMotion.current
    val verb = MacExposure.verb(axis)
    if (verb == null || reduced) return this
    return when (verb) {
        MacVerb.PULSE -> {
            val transition = rememberInfiniteTransition(label = node.id + "-pulse")
            val scale = transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.03f,
                animationSpec = infiniteRepeatable(
                    animation = tween(MacTheme.heldPulseMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "held-pulse"
            )
            graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
        }
        MacVerb.SHIMMER -> {
            val shift = remember(node.id) { Animatable(8f) }
            LaunchedEffect(node.id, verb) {
                shift.snapTo(8f)
                shift.animateTo(0f, tween(MacTheme.unknownShimmerMs, easing = LinearEasing))
            }
            graphicsLayer { translationX = shift.value }
        }
        MacVerb.CRACK_REVERSE -> {
            val scale = remember(node.id) { Animatable(1.06f) }
            LaunchedEffect(node.id, verb) {
                scale.snapTo(1.06f)
                scale.animateTo(1f, tween(MacTheme.contradictedCrackMs, easing = LinearEasing))
            }
            graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
        }
        MacVerb.FADE_OUT -> {
            val alpha = remember(node.id) { Animatable(1f) }
            LaunchedEffect(node.id, verb) {
                alpha.snapTo(1f)
                alpha.animateTo(0.55f, tween(MacTheme.staleFadeMs, easing = LinearEasing))
            }
            graphicsLayer { this.alpha = alpha.value }
        }
        MacVerb.FADE_BACK -> {
            val alpha = remember(node.id) { Animatable(0.4f) }
            LaunchedEffect(node.id, verb) {
                alpha.snapTo(0.4f)
                alpha.animateTo(1f, tween(MacTheme.compensatedFadeMs, easing = LinearEasing))
            }
            graphicsLayer { this.alpha = alpha.value }
        }
    }
}
