// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import a3.renderers.android.core.model.SharedElementPlan

@Composable
fun MacMorphApplier(plan: SharedElementPlan, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val token = tokenSpring(plan.spring)
    val morph = remember { mutableStateOf(MotionPhysics.State(1f, 0f)) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(plan.to, plan.shared) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (reduced) {
            morph.value = MotionPhysics.State(1f, 0f)
            return@LaunchedEffect
        }
        morph.value = MotionPhysics.State(0.4f, 0f)
        runSpring(
            reduced = false,
            initial = morph.value,
            target = 1f,
            mass = token.mass,
            stiffness = token.stiffness,
            damping = token.damping
        ) { morph.value = it }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val s = morph.value.x
                scaleX = s
                scaleY = s
            }
    ) {
        content()
    }
}
