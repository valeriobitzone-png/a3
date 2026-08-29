package a3.renderers.android.compose

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import a3.renderers.android.core.model.SemanticGestureActions

@Composable
fun ComposeGestureApplier(actions: SemanticGestureActions, content: @Composable () -> Unit) {
    val dismiss = actions.actions.any { it.action == "dismiss" }
    Box(
        modifier = if (dismiss) {
            Modifier.pointerInput(actions) {
                detectHorizontalDragGestures { _, _ -> }
            }
        } else {
            Modifier
        }
    ) {
        content()
    }
}
