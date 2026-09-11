package a3.showcase

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import a3.renderers.android.compose.Theme

internal fun showcaseArgb(color: Int): Color = Color(
    ((color shr 16) and 0xFF) / 255f,
    ((color shr 8) and 0xFF) / 255f,
    (color and 0xFF) / 255f,
    ((color ushr 24) and 0xFF) / 255f
)

internal class ShowcaseSquircleShape(
    private val radiusPx: Float,
    private val n: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val pts = ShowcaseGlassMath.points(size.width, size.height, radiusPx, n)
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
internal fun ShowcaseWallpaperLayer(
    blurred: Boolean,
    modifier: Modifier = Modifier,
    tagged: Boolean = true
) {
    val cell = ShowcaseWallpaper.CELL
    val taggedModifier = when {
        !tagged -> modifier
        blurred -> modifier.testTag("showcase-wallpaper-blurred")
        else -> modifier.testTag("showcase-wallpaper")
    }
    Canvas(taggedModifier) {
        val step = cell.toFloat()
        var y = 0
        while (y < size.height.toInt()) {
            var x = 0
            while (x < size.width.toInt()) {
                val packed = if (blurred) {
                    ShowcaseWallpaper.blurredAt(x, y, cell)
                } else {
                    ShowcaseWallpaper.colorAt(x, y, cell)
                }
                drawRect(
                    color = showcaseArgb(packed),
                    topLeft = Offset(x.toFloat(), y.toFloat()),
                    size = Size(step, step)
                )
                x += cell
            }
            y += cell
        }
    }
}

@Composable
internal fun ShowcaseGlassPlate(
    modifier: Modifier = Modifier,
    nested: Boolean = false,
    frost: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = remember { ShowcaseTokens.snapshot }
    val radius = if (nested) {
        ShowcaseGlassMath.nestedRadius(tokens.radiusPx, 12f, tokens.nestedMinPx)
    } else {
        tokens.radiusPx
    }
    val shape = ShowcaseSquircleShape(radius, tokens.superellipseN)
    Box(
        modifier
            .testTag("showcase-glass-plate")
            .drawBehind {
                val path = squirclePath(size.width, size.height, radius, tokens.superellipseN)
                drawPath(path, Color.Black.copy(alpha = 0.18f))
            }
            .clip(shape)
            .drawWithContent {
                drawContent()
                val path = squirclePath(size.width, size.height, radius, tokens.superellipseN)
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
    ) {
        if (frost) {
            ShowcaseWallpaperLayer(blurred = true, Modifier.matchParentSize(), tagged = !nested)
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Color.White.copy(alpha = if (frost) tokens.fillOpacity * 0.18f else tokens.fillOpacity * 0.12f)
                )
                .testTag("showcase-glass-fill")
        )
        Box(Modifier.size(1.dp).testTag("showcase-glass-highlight"))
        if (frost) Box(Modifier.size(1.dp).testTag("showcase-glass-tint"))
        content()
    }
}

@Composable
internal fun ShowcaseGlassChip(
    label: String,
    tag: String,
    active: Boolean,
    ink: Color,
    onClick: () -> Unit
) {
    val tokens = ShowcaseTokens.snapshot
    val amber = showcaseArgb(tokens.amber)
    ShowcaseGlassPlate(nested = true, frost = true) {
        Box(
            Modifier
                .testTag(tag)
                .clickable(role = Role.Button, onClick = onClick)
                .background(if (active) amber.copy(alpha = 0.28f) else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(label, style = Theme.type.copy(fontSize = 13.sp, color = ink))
        }
    }
}

private fun squirclePath(width: Float, height: Float, radius: Float, n: Float): Path {
    val path = Path()
    val pts = ShowcaseGlassMath.points(width, height, radius, n)
    path.moveTo(pts[0], pts[1])
    var i = 2
    while (i < pts.size) {
        path.lineTo(pts[i], pts[i + 1])
        i += 2
    }
    path.close()
    return path
}