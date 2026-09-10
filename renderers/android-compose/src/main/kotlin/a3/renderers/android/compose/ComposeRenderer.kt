package a3.renderers.android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import a3.renderers.android.core.model.RenderedOutput
import a3.renderers.android.core.model.RendererContext

@Composable
fun ComposeRenderer(
    output: RenderedOutput,
    onAction: (String) -> Unit = {},
    stage: String = RendererContext.STAGE_PRONTO,
    crack: Boolean = false,
    foley: FoleySink? = null,
    announce: EpistemicAnnounce = EpistemicAnnounce.Silent,
    highContrast: Boolean = false
) {
    val matrix = remember(stage) { Theme.material(stage) }
    val paint = remember(matrix) {
        Paint().apply { colorFilter = ColorFilter.colorMatrix(matrix) }
    }
    val sink = foley ?: remember { AudioTrackFoleySink() }
    CompositionLocalProvider(
        LocalRendererStage provides stage,
        LocalCrack provides crack,
        LocalReducedMotion provides output.reducedMotion,
        LocalHighContrast provides highContrast,
        LocalEpistemicAnnounce provides announce,
        LocalGlassBlurEnabled provides (Build.VERSION.SDK_INT >= 31)
    ) {
        ComposeFoleyBinder(
            plan = output.sharedElements,
            durationHint = output.spring.durationHint,
            sink = sink
        ) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("a3-material")
                .drawWithContent {
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                        drawContent()
                        canvas.restore()
                    }
                }
        ) {
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
        }
    }
}
