package a3.renderers.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import a3.renderers.android.core.model.RenderedOutput

@Composable
fun ComposeRenderer(output: RenderedOutput, onAction: (String) -> Unit = {}) {
    ComposeMotionApplier(output.spring) {
        ComposeMorphApplier(output.sharedElements) {
            ComposeGestureApplier(
                actions = output.gestures,
                onAction = onAction,
                attachSwipe = output.nodes.isEmpty()
            ) {
                ComposeHapticApplier(output.haptics) {
                    if (output.nodes.isEmpty()) {
                        Column(Modifier.testTag("a3-surface")) {
                            for (token in output.resolvedTokens) {
                                Spacer(
                                    Modifier
                                        .size((8 * output.densityScale).dp)
                                        .height((8 * output.densityScale).dp)
                                        .background(
                                            Color(
                                                red = token.color.red / 255f,
                                                green = token.color.green / 255f,
                                                blue = token.color.blue / 255f
                                            )
                                        )
                                        .testTag("token-" + token.token)
                                )
                            }
                        }
                    } else {
                        ComposeCatalog(output.nodes, output.gestures.actions, onAction)
                    }
                }
            }
        }
    }
}
