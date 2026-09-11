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
    val reduced = LocalReducedMotion.current
    LaunchedEffect(events.events, reduced) {
        performHapticMap(view, events, reduced = reduced)
    }
    content()
}

internal suspend fun performHapticMap(
    view: View,
    events: SemanticHapticEvents,
    reduced: Boolean = false,
    engine: Boolean = true,
    log: (String) -> Unit = {}
) {
    if (reduced) return
    if (!engine) {
        log(SystemHaptic.UNAVAILABLE)
        return
    }
    val gap = GraphicsTokens.haptic.gapMs.toLong()
    for (event in events.events) {
        when (event.pattern) {
            "tap" -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            "double-tap" -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                delay(gap)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }
}
