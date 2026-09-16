// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import a3.renderers.android.core.model.RenderedNode

@Composable
internal fun EpistemicGlyph(node: RenderedNode) {
    val axis = MacExposure.of(node)
    val kind = GlyphGeometry.kind(axis.action, axis.status) ?: return
    if (kind == GlyphGeometry.Kind.SPINNER) return
    GlyphCanvas(node.id, kind)
}

@Composable
internal fun GlyphCanvas(id: String, kind: GlyphGeometry.Kind) {
    val reduced = LocalReducedMotion.current
    var t by remember(id, kind) { mutableFloatStateOf(if (reduced) 1f else 0f) }
    LaunchedEffect(id, kind, reduced) {
        if (reduced || kind == GlyphGeometry.Kind.PAUSE) {
            t = 1f
            return@LaunchedEffect
        }
        t = 0f
        val spring = GraphicsTokens.motion.comfortable
        var s = MotionPhysics.State(0f, 0f)
        var last = 0L
        var frames = 0
        withFrameNanos { last = it }
        while (kotlin.math.abs(s.x - 1f) > 0.01f && frames < 480) {
            val now = withFrameNanos { it }
            val raw = (now - last) / 1_000_000_000f
            val dt = if (raw <= 0f) MotionPhysics.DT else raw.coerceAtMost(0.032f)
            last = now
            s = MotionPhysics.step(s, 1f, spring.mass, spring.stiffness, spring.damping, dt)
            t = s.x.coerceIn(0f, 1f)
            frames++
        }
        t = 1f
    }
    Canvas(
        Modifier
            .size(12.dp)
            .testTag(id + "-glyph-" + GlyphGeometry.tag(kind))
    ) {
        val pts = GlyphGeometry.points(kind, t, reduced)
        if (pts.size < 2) return@Canvas
        val path = Path()
        path.moveTo(pts[0].x * size.width, pts[0].y * size.height)
        var i = 1
        while (i < pts.size) {
            path.lineTo(pts[i].x * size.width, pts[i].y * size.height)
            i++
        }
        drawPath(
            path,
            color = Color.Black,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
