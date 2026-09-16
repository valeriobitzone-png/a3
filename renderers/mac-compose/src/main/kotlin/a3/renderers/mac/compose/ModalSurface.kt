// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
internal fun ModalSurface(
    open: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val reduced = LocalReducedMotion.current
    val tokens = remember { GraphicsTokens.snapshot }
    var t by remember { mutableFloatStateOf(if (open) 1f else 0f) }
    LaunchedEffect(open, reduced) {
        val target = if (open) 1f else 0f
        if (reduced) {
            t = target
            return@LaunchedEffect
        }
        val spring = GraphicsTokens.motion.comfortable
        var s = MotionPhysics.State(t, 0f)
        var last = 0L
        var frames = 0
        withFrameNanos { last = it }
        while (kotlin.math.abs(s.x - target) > 0.01f && frames < 480) {
            val now = withFrameNanos { it }
            val raw = (now - last) / 1_000_000_000f
            val dt = if (raw <= 0f) MotionPhysics.DT else raw.coerceAtMost(0.032f)
            last = now
            s = MotionPhysics.step(s, target, spring.mass, spring.stiffness, spring.damping, dt)
            t = s.x
            frames++
        }
        t = target
    }
    val blur = ModalBackdrop.blurPx(t)
    val alpha = ModalBackdrop.overlayAlpha(t)
    Box(modifier.zIndex(tokens.zOverlay.toFloat()).testTag("modal-surface")) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("modal-backdrop")
                .blur(blur.dp)
        )
        Box(
            Modifier
                .fillMaxSize()
                .testTag("modal-scrim")
                .background(Color.Black.copy(alpha = alpha))
        )
        content()
    }
}
