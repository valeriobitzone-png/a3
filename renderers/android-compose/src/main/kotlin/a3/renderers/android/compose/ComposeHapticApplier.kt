package a3.renderers.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalHapticFeedback
import a3.renderers.android.core.model.SemanticHapticEvents

@Composable
fun ComposeHapticApplier(events: SemanticHapticEvents, content: @Composable () -> Unit) {
    LocalHapticFeedback.current
    content()
}
