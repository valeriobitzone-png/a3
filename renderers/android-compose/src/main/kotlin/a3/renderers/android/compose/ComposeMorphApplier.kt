package a3.renderers.android.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import a3.renderers.android.core.model.SharedElementPlan

@Composable
fun ComposeMorphApplier(plan: SharedElementPlan, content: @Composable () -> Unit) {
    val morph = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(plan.to, plan.shared) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        morph.snapTo(0f)
        morph.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = plan.spring.stiffness.toFloat()
            )
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = morph.value }
    ) {
        content()
    }
}
