// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import a3.a3ui.model.IntentCandidate
import a3.renderers.android.core.model.SemanticGestureActions

@Composable
fun ComposeGestureApplier(
    actions: SemanticGestureActions,
    onAction: (String) -> Unit = {},
    attachSwipe: Boolean = true,
    content: @Composable () -> Unit
) {
    val dismiss = actions.actions.firstOrNull { it.action == "dismiss" }
    val sized = Modifier.fillMaxSize()
    Box(
        modifier = if (attachSwipe && dismiss != null) {
            sized.pointerInput(dismiss.action) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < 0f) {
                        onAction(
                            IntentCandidate(dismiss.gesture, dismiss.action, dismiss.targetNodeId).action
                        )
                    }
                }
            }
        } else {
            sized
        }
    ) {
        content()
    }
}
