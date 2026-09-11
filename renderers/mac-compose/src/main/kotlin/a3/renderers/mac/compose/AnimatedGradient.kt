package a3.renderers.mac.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

@Composable
internal fun AnimatedGradient(
    timeSec: Float,
    modifier: Modifier = Modifier,
    reduced: Boolean = LocalReducedMotion.current
) {
    val t = if (reduced) 0f else timeSec
    val pal = remember { ShaderGradient.palette() }
    Canvas(modifier.fillMaxSize().testTag("animated-gradient")) {
        val step = 4
        var y = 0
        while (y < size.height.toInt()) {
            var x = 0
            while (x < size.width.toInt()) {
                val u = x / size.width
                val v = y / size.height
                drawRect(
                    color = Color(ShaderGradient.shade(u, v, t, pal)),
                    topLeft = Offset(x.toFloat(), y.toFloat()),
                    size = Size(step.toFloat(), step.toFloat())
                )
                x += step
            }
            y += step
        }
    }
}
