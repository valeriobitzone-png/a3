package a3.renderers.android.compose

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import a3.renderers.android.core.model.SemanticHapticEvents
import kotlinx.coroutines.delay

@Composable
fun ComposeHapticApplier(events: SemanticHapticEvents, content: @Composable () -> Unit) {
    val view = LocalView.current
    LaunchedEffect(events.events) {
        performHapticMap(view, events)
    }
    content()
}

internal suspend fun performHapticMap(view: View, events: SemanticHapticEvents) {
    for (event in events.events) {
        when (event.pattern) {
            "tap" -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            "double-tap" -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                delay(Theme.hapticGapMs)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }
}
