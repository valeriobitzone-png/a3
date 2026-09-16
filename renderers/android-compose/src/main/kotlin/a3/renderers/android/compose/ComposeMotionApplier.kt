// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import a3.renderers.android.core.model.RendererContext
import a3.renderers.android.core.model.SpringParams

@Composable
fun ComposeMotionApplier(params: SpringParams, content: @Composable () -> Unit) {
    val stage = LocalRendererStage.current
    val crack = LocalCrack.current
    val reduced = LocalReducedMotion.current || LocalSimplifiedMotion.current
    if (reduced) {
        Box(Modifier.fillMaxSize()) { content() }
        return
    }
    val token = tokenSpring(params)
    var state by remember { mutableStateOf(MotionPhysics.State(1f, 0f)) }
    LaunchedEffect(stage, crack, params.stiffness, params.damping, params.durationHint) {
        when {
            crack -> {
                state = MotionPhysics.State(Theme.crackPeak, 0f)
                val easing = tokenEasing(emphasized = false)
                val from = Theme.crackPeak
                val start = withFrameNanos { it }
                val dur = Theme.crackMs * 1_000_000L
                while (true) {
                    val now = withFrameNanos { it }
                    val t = ((now - start).toFloat() / dur).coerceIn(0f, 1f)
                    val x = from + (1f - from) * easing.transform(t)
                    state = MotionPhysics.State(x, 0f)
                    if (t >= 1f) break
                }
                state = MotionPhysics.State(1f, 0f)
            }
            stage == RendererContext.STAGE_APPROVA -> {
                state = runSpring(
                    reduced = false,
                    initial = MotionPhysics.inheritVelocity(state, state.v),
                    target = Theme.holdScale,
                    mass = token.mass,
                    stiffness = token.stiffness,
                    damping = token.damping
                ) { state = it }
            }
            stage == RendererContext.STAGE_ASCOLTO -> {
                state = MotionPhysics.State(Theme.listenFrom, 0f)
                val easing = tokenEasing(emphasized = true)
                val from = Theme.listenFrom
                val start = withFrameNanos { it }
                val dur = params.durationHint * 1_000_000L
                while (true) {
                    val now = withFrameNanos { it }
                    val t = ((now - start).toFloat() / dur).coerceIn(0f, 1f)
                    val x = from + (1f - from) * easing.transform(t)
                    state = MotionPhysics.State(x, 0f)
                    if (t >= 1f) break
                }
                state = MotionPhysics.State(1f, 0f)
            }
            else -> {
                state = runSpring(
                    reduced = false,
                    initial = MotionPhysics.inheritVelocity(state, state.v),
                    target = 1f,
                    mass = token.mass,
                    stiffness = token.stiffness,
                    damping = token.damping
                ) { state = it }
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = state.x
                scaleY = state.x
            }
    ) {
        content()
    }
}
