package a3.renderers.mac.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
internal fun ParallaxIcon(
    tilt: ParallaxLayers.Tilt,
    available: Boolean,
    modifier: Modifier = Modifier
) {
    val tag = if (available) "parallax-icon" else "parallax-fallback"
    Box(modifier.size(64.dp).testTag(tag)) {
        Layer(ParallaxLayers.BG, Color(0xFF282830), 52.dp, tilt, available)
        Layer(ParallaxLayers.MID, Color(0xFFCC8800), 40.dp, tilt, available)
        Layer(ParallaxLayers.FG, Color.Black, 28.dp, tilt, available)
    }
}

@Composable
private fun Layer(
    layer: Int,
    color: Color,
    size: Dp,
    tilt: ParallaxLayers.Tilt,
    available: Boolean
) {
    val name = when (layer) {
        ParallaxLayers.FG -> "fg"
        ParallaxLayers.MID -> "mid"
        else -> "bg"
    }
    Box(
        Modifier
            .zIndex((3 - layer).toFloat())
            .offset(
                x = ParallaxLayers.offsetX(layer, tilt, available).dp,
                y = ParallaxLayers.offsetY(layer, tilt, available).dp
            )
            .size(size)
            .background(color, RoundedCornerShape(8.dp))
            .testTag("parallax-$name")
    )
}
