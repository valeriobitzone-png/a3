// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal val LocalGlassBlurEnabled = staticCompositionLocalOf { Build.VERSION.SDK_INT >= 31 }

@Composable
internal fun GlassSurface(
    modifier: Modifier = Modifier,
    nested: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = remember { GraphicsTokens.snapshot }
    val blurOn = LocalGlassBlurEnabled.current
    val paddingPx = with(LocalDensity.current) { Theme.space.toPx() }
    val radiusPx = if (nested) {
        SquircleGeometry.nestedRadius(tokens.radiusPx, paddingPx, tokens.nestedMinPx)
    } else {
        tokens.radiusPx
    }
    val shape = SquircleShape(radiusPx.dp, tokens.superellipseN)
    val fill = Color(
        red = android.graphics.Color.red(tokens.fillColor) / 255f,
        green = android.graphics.Color.green(tokens.fillColor) / 255f,
        blue = android.graphics.Color.blue(tokens.fillColor) / 255f,
        alpha = tokens.fillOpacity
    )
    Box(
        modifier
            .drawBehind {
                val path = SquircleGeometry.androidPath(size.width, size.height, radiusPx, tokens.superellipseN)
                drawIntoCanvas { canvas ->
                    val ambient = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb(
                            (tokens.ambient.opacity * 255).toInt(),
                            0,
                            0,
                            0
                        )
                        maskFilter = BlurMaskFilter(tokens.ambient.blurPx, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.translate(tokens.ambient.offsetXPx, tokens.ambient.offsetYPx)
                    canvas.nativeCanvas.drawPath(path, ambient)
                    canvas.nativeCanvas.restore()
                    val key = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb(
                            (tokens.key.opacity * 255).toInt(),
                            0,
                            0,
                            0
                        )
                        maskFilter = BlurMaskFilter(tokens.key.blurPx, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.translate(tokens.key.offsetXPx, tokens.key.offsetYPx)
                    canvas.nativeCanvas.drawPath(path, key)
                    canvas.nativeCanvas.restore()
                }
            }
            .clip(shape)
    ) {
        GlassBackdropReplica(
            Modifier
                .matchParentSize()
                .semantics {
                    if (!blurOn) contentDescription = GlassRaster.BLUR_UNAVAILABLE
                },
            blurOn = blurOn
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
