package a3.renderers.mac.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.animation.core.CubicBezierEasing
import a3.renderers.android.core.model.SpringParams

internal fun tokenSpring(params: SpringParams): GraphicsTokens.SpringToken {
    return GraphicsTokens.motion.matching(params.stiffness, params.damping)
}

internal fun tokenEasing(emphasized: Boolean): CubicBezierEasing {
    val p = if (emphasized) GraphicsTokens.motion.bezierEmphasized else GraphicsTokens.motion.bezierStandard
    return CubicBezierEasing(p[0], p[1], p[2], p[3])
}

internal suspend fun runSpring(
    reduced: Boolean,
    initial: MotionPhysics.State,
    target: Float,
    mass: Float,
    stiffness: Float,
    damping: Float,
    onFrame: (MotionPhysics.State) -> Unit
): MotionPhysics.State {
    if (reduced) {
        val done = MotionPhysics.State(target, 0f)
        onFrame(done)
        return done
    }
    var s = initial
    onFrame(s)
    var last = 0L
    var frames = 0
    withFrameNanos { last = it }
    while (true) {
        val now = withFrameNanos { it }
        val raw = (now - last) / 1_000_000_000f
        val dt = when {
            raw <= 0f -> MotionPhysics.DT
            raw > 0.032f -> 0.032f
            else -> raw
        }
        last = now
        s = MotionPhysics.step(s, target, mass, stiffness, damping, dt)
        onFrame(s)
        frames++
        if (kotlin.math.abs(s.x - target) < 0.0015f && kotlin.math.abs(s.v) < 0.02f) break
        if (frames > 480) break
    }
    val done = MotionPhysics.State(target, 0f)
    onFrame(done)
    return done
}

@Composable
internal fun FluidResizeContainer(content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (!constraints.hasBoundedWidth) {
            content()
            return@BoxWithConstraints
        }
        val targetPx = constraints.maxWidth.toFloat()
        var widthPx by remember { mutableFloatStateOf(targetPx) }
        var primed by remember { mutableStateOf(false) }
        LaunchedEffect(targetPx, reduced) {
            if (!primed || reduced || targetPx < 8f || widthPx < 8f) {
                widthPx = targetPx
                primed = true
                return@LaunchedEffect
            }
            val from = widthPx
            val bezier = GraphicsTokens.motion.bezierStandard
            val hint = GraphicsTokens.motion.comfortable.durationHintMs.toFloat()
            var last = 0L
            withFrameNanos { last = it }
            var elapsed = 0f
            while (elapsed < hint) {
                val now = withFrameNanos { it }
                elapsed += (now - last) / 1_000_000f
                last = now
                val t = (elapsed / hint).coerceIn(0f, 1f)
                widthPx = MotionPhysics.fluidWidth(from, targetPx, t, bezier)
            }
            widthPx = targetPx
        }
        Box(
            Modifier
                .requiredWidth(with(density) { widthPx.toDp() })
                .fillMaxHeight()
        ) {
            content()
        }
    }
}

internal fun Modifier.contactRipple(): Modifier = composed {
    val reduced = LocalReducedMotion.current
    if (reduced) return@composed this
    var origin by remember { mutableStateOf(Offset.Unspecified) }
    var playing by remember { mutableStateOf(false) }
    var tMs by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = 0L
        withFrameNanos { last = it }
        tMs = 0f
        while (tMs < 420f) {
            val now = withFrameNanos { it }
            tMs += (now - last) / 1_000_000f
            last = now
        }
        playing = false
    }
    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    if (change.changedToDown()) {
                        origin = change.position
                        playing = true
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            if (playing && origin != Offset.Unspecified) {
                val r = MotionPhysics.rippleRadius(tMs, size.minDimension, 90f)
                val a = MotionPhysics.rippleAlpha(tMs, 140f)
                drawCircle(
                    color = Color.White.copy(alpha = a),
                    radius = r,
                    center = origin,
                    style = Stroke(width = 3f)
                )
            }
        }
}

internal fun Modifier.rubberBand(): Modifier = composed {
    val reduced = LocalReducedMotion.current
    var stretch by remember { mutableFloatStateOf(0f) }
    var vel by remember { mutableFloatStateOf(0f) }
    var returning by remember { mutableStateOf(false) }
    val spring = remember { GraphicsTokens.motion.comfortable }
    LaunchedEffect(returning, reduced) {
        if (!returning || reduced) {
            if (reduced) {
                stretch = 0f
                vel = 0f
            }
            return@LaunchedEffect
        }
        var s = MotionPhysics.State(stretch, vel)
        var last = 0L
        withFrameNanos { last = it }
        while (kotlin.math.abs(s.x) > 0.25f || kotlin.math.abs(s.v) > 2f) {
            val now = withFrameNanos { it }
            val raw = (now - last) / 1_000_000_000f
            val dt = if (raw <= 0f) MotionPhysics.DT else raw.coerceAtMost(0.032f)
            last = now
            s = MotionPhysics.step(s, 0f, spring.mass, spring.stiffness, spring.damping, dt)
            stretch = s.x
            vel = s.v
        }
        stretch = 0f
        vel = 0f
        returning = false
    }
    val connection = remember(reduced) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (reduced || available.y == 0f) return Offset.Zero
                val raw = stretch + available.y
                stretch = if (raw >= 0f) {
                    MotionPhysics.rubberStretch(raw)
                } else {
                    -MotionPhysics.rubberStretch(-raw)
                }
                returning = false
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (reduced || kotlin.math.abs(stretch) < 0.5f) return Velocity.Zero
                vel = available.y
                returning = true
                return available
            }
        }
    }
    this
        .nestedScroll(connection)
        .graphicsLayer { translationY = stretch }
}

@Composable
internal fun Modifier.actionPress(
    enabled: Boolean,
    source: androidx.compose.foundation.interaction.MutableInteractionSource,
    onClick: () -> Unit
): Modifier {
    return clickable(
        enabled = enabled,
        indication = null,
        interactionSource = source,
        onClick = onClick
    )
}
