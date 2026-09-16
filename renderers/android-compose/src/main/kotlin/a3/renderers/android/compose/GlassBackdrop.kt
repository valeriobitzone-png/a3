// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag

/**
 * Shared-background glass: the scene wallpaper is painted sharp once, then each
 * [GlassSurface] blurs a window-aligned replica of the same pixels (not a white
 * fill). Same formula as [DynamicPalette.fixtureWallpaper].
 */
internal object GlassBackdrop {
    const val TECHNIQUE = "shared-background"
    const val CELL = 10
    const val TEAL = (0xFF shl 24) or (0x20 shl 16) or (0x88 shl 8) or 0x90

    fun cellColor(cx: Int, cy: Int): Int {
        return when (((cx + cy) % 4 + 4) % 4) {
            0 -> DynamicPalette.tokenAmber().pack()
            1 -> TEAL
            2 -> DynamicPalette.tokenInk().pack()
            else -> DynamicPalette.tokenPaper().pack()
        }
    }

    fun colorAt(x: Int, y: Int, cell: Int = CELL): Int {
        val c = cell.coerceAtLeast(1)
        return cellColor(floorDiv(x, c), floorDiv(y, c))
    }

    fun fill(width: Int, height: Int, originX: Int = 0, originY: Int = 0, cell: Int = CELL): IntArray {
        val out = IntArray(width * height)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                out[y * width + x] = colorAt(originX + x, originY + y, cell)
                x++
            }
            y++
        }
        return out
    }

    fun chroma(pixels: IntArray): Float {
        if (pixels.isEmpty()) return 0f
        var acc = 0.0
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            acc += (maxOf(r, g, b) - minOf(r, g, b))
        }
        return (acc / pixels.size / 255.0).toFloat()
    }

    fun composeColor(packed: Int): Color = Color(
        ((packed shr 16) and 0xFF) / 255f,
        ((packed shr 8) and 0xFF) / 255f,
        (packed and 0xFF) / 255f,
        ((packed ushr 24) and 0xFF) / 255f
    )

    private fun floorDiv(value: Int, cell: Int): Int {
        return if (value >= 0) value / cell else (value - cell + 1) / cell
    }
}

internal val LocalGlassSceneOrigin = staticCompositionLocalOf { Offset.Zero }

@Composable
internal fun GlassSceneHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    CompositionLocalProvider(LocalGlassSceneOrigin provides origin) {
        Box(modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .testTag("glass-backdrop-sharp")
                    .onGloballyPositioned { origin = it.positionInWindow() }
            ) {
                val cell = GlassBackdrop.CELL
                val step = cell.toFloat()
                var y = 0
                while (y < size.height.toInt()) {
                    var x = 0
                    while (x < size.width.toInt()) {
                        drawRect(
                            color = GlassBackdrop.composeColor(GlassBackdrop.colorAt(x, y, cell)),
                            topLeft = Offset(x.toFloat(), y.toFloat()),
                            size = Size(step, step)
                        )
                        x += cell
                    }
                    y += cell
                }
            }
            content()
        }
    }
}

@Composable
internal fun GlassBackdropReplica(
    modifier: Modifier = Modifier,
    blurOn: Boolean
) {
    val tokens = remember { GraphicsTokens.snapshot }
    val sceneOrigin = LocalGlassSceneOrigin.current
    var glassOrigin by remember { mutableStateOf(Offset.Zero) }
    val matrix = LocalFeatureMatrix.current
    val radius = if (blurOn && matrix.blurEnabled) matrix.blurRadiusPx.toFloat() else 0f
    val effect = if (blurOn && radius > 0f) AndroidGlassEffect.create(tokens, radius) else null
    Canvas(
        modifier
            .onGloballyPositioned { glassOrigin = it.positionInWindow() }
            .graphicsLayer {
                if (effect != null) {
                    renderEffect = effect.asComposeRenderEffect()
                }
            }
            .testTag(if (blurOn) "glass-surface" else "glass-fallback")
    ) {
        val cell = GlassBackdrop.CELL
        val step = cell.toFloat()
        val ox = (glassOrigin.x - sceneOrigin.x).toInt()
        val oy = (glassOrigin.y - sceneOrigin.y).toInt()
        var y = 0
        while (y < size.height.toInt()) {
            var x = 0
            while (x < size.width.toInt()) {
                drawRect(
                    color = GlassBackdrop.composeColor(GlassBackdrop.colorAt(ox + x, oy + y, cell)),
                    topLeft = Offset(x.toFloat(), y.toFloat()),
                    size = Size(step, step)
                )
                x += cell
            }
            y += cell
        }
    }
}
