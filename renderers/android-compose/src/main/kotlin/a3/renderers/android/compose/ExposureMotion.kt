// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

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

/**
 * Sensory motion verbs. Configurable via [Theme] durations. Reduced-motion
 * zeroes every duration and still paints the chrome.
 */
@Composable
fun Modifier.exposureLayer(node: RenderedNode): Modifier {
    val axis = Exposure.of(node)
    val reduced = LocalReducedMotion.current
    val verb = Exposure.verb(axis)
    if (verb == null || reduced) return this
    return when (verb) {
        ExposureVerb.PULSE -> {
            val transition = rememberInfiniteTransition(label = node.id + "-pulse")
            val scale = transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.03f,
                animationSpec = infiniteRepeatable(
                    animation = tween(Theme.heldPulseMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "held-pulse"
            )
            graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
        }
        ExposureVerb.SHIMMER -> {
            val shift = remember(node.id) { Animatable(8f) }
            LaunchedEffect(node.id, verb) {
                shift.snapTo(8f)
                shift.animateTo(0f, tween(Theme.unknownShimmerMs, easing = LinearEasing))
            }
            graphicsLayer { translationX = shift.value }
        }
        ExposureVerb.CRACK_REVERSE -> {
            val scale = remember(node.id) { Animatable(1.06f) }
            LaunchedEffect(node.id, verb) {
                scale.snapTo(1.06f)
                scale.animateTo(1f, tween(Theme.contradictedCrackMs, easing = LinearEasing))
            }
            graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
        }
        ExposureVerb.FADE_OUT -> {
            val alpha = remember(node.id) { Animatable(1f) }
            LaunchedEffect(node.id, verb) {
                alpha.snapTo(1f)
                alpha.animateTo(0.55f, tween(Theme.staleFadeMs, easing = LinearEasing))
            }
            graphicsLayer { this.alpha = alpha.value }
        }
        ExposureVerb.FADE_BACK -> {
            val alpha = remember(node.id) { Animatable(0.4f) }
            LaunchedEffect(node.id, verb) {
                alpha.snapTo(0.4f)
                alpha.animateTo(1f, tween(Theme.compensatedFadeMs, easing = LinearEasing))
            }
            graphicsLayer { this.alpha = alpha.value }
        }
    }
}
