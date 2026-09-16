// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
internal fun AmbientIndicator(
    kind: AmbientChrome.Kind,
    expanded: Boolean,
    granted: Boolean,
    mac: Boolean = true,
    modifier: Modifier = Modifier
) {
    val tokens = remember { GraphicsTokens.snapshot }
    val reduced = LocalReducedMotion.current
    if (!granted) {
        BasicText(
            text = AmbientChrome.UNAVAILABLE,
            modifier = modifier
                .zIndex(tokens.zChrome.toFloat())
                .testTag("ambient-unavailable"),
            style = MacTheme.type
        )
        return
    }
    var t by remember { mutableFloatStateOf(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded, reduced) {
        val target = if (expanded) 1f else 0f
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
            t = s.x.coerceIn(0f, 1.2f)
            frames++
        }
        t = target
    }
    val rect = AmbientChrome.morph(t)
    val host = AmbientChrome.host(true, mac)
    val shapeTag = if (expanded) "ambient-expanded" else "ambient-collapsed"
    Box(
        modifier
            .zIndex(tokens.zChrome.toFloat())
            .offset(rect.x.dp, rect.y.dp)
            .size(rect.w.dp, rect.h.dp)
            .background(Color.Black.copy(alpha = tokens.fillOpacity), RoundedCornerShape(rect.radius.dp))
            .testTag("ambient-indicator")
    ) {
        Box(Modifier.fillMaxSize().testTag(shapeTag))
        Box(Modifier.size(1.dp).testTag("ambient-host-" + host.name.lowercase()))
        BasicText(
            text = AmbientChrome.caption(kind),
            modifier = Modifier.align(Alignment.Center).testTag("ambient-copy"),
            style = MacTheme.type.copy(color = Color.White)
        )
    }
}
