package a3.renderers.android.compose

import androidx.compose.runtime.Composable
import a3.renderers.android.core.model.SharedElementPlan

@Composable
fun ComposeMorphApplier(plan: SharedElementPlan, content: @Composable () -> Unit) {
    content()
}
