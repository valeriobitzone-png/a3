package a3.renderers.mac.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun MacGlassSurface(
    modifier: Modifier = Modifier,
    nested: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = remember { GraphicsTokens.snapshot }
    val blurOn = LocalGlassBlurEnabled.current && LocalFeatureMatrix.current.blurEnabled
    val paddingPx = with(LocalDensity.current) { MacTheme.space.toPx() }
    val radiusPx = if (nested) {
        SquircleGeometry.nestedRadius(tokens.radiusPx, paddingPx, tokens.nestedMinPx)
    } else {
        tokens.radiusPx
    }
    val shape = SquircleShape(radiusPx.dp, tokens.superellipseN)
    val fill = Color.Black.copy(alpha = tokens.fillOpacity)
    Box(
        modifier
            .drawBehind {
                drawPath(
                    SquircleGeometry.composePath(size.width, size.height, radiusPx, tokens.superellipseN),
                    color = Color.Black.copy(alpha = tokens.ambient.opacity),
                )
            }
            .clip(shape)
    ) {
        GlassBackdropReplica(
            Modifier
                .matchParentSize()
                .semantics {
                    if (!blurOn) contentDescription = MacGlassRaster.BLUR_UNAVAILABLE
                }
        )
        Box(Modifier.size(1.dp).testTag("glass-backdrop-replica"))
        Box(Modifier.matchParentSize().background(fill))
        Box(
            Modifier
                .matchParentSize()
                .drawWithContent {
                    val path = SquircleGeometry.composePath(
                        size.width,
                        size.height,
                        radiusPx,
                        tokens.superellipseN
                    )
                    drawPath(
                        path,
                        color = Color.Black.copy(alpha = tokens.outer.startAlpha),
                        style = Stroke(width = tokens.outer.widthPx.dp.toPx())
                    )
                    drawPath(
                        path,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = tokens.inner.startAlpha),
                                Color.White.copy(alpha = tokens.inner.endAlpha)
                            )
                        ),
                        style = Stroke(width = tokens.inner.widthPx.dp.toPx())
                    )
                }
        )
        content()
    }
}
