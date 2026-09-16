// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import a3.renderers.android.core.model.SpringParams

@Composable
fun MacMotionApplier(params: SpringParams, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current || LocalSimplifiedMotion.current
    if (reduced) {
        Box(Modifier.fillMaxSize()) { content() }
        return
    }
    val token = tokenSpring(params)
    var state by remember { mutableStateOf(MotionPhysics.State(1f, 0f)) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(params.stiffness, params.damping, params.durationHint) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        state = runSpring(
            reduced = false,
            initial = MotionPhysics.inheritVelocity(state, state.v),
            target = 1f,
            mass = token.mass,
            stiffness = token.stiffness,
            damping = token.damping
        ) { state = it }
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
