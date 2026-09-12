package a3.showcase

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ShowcaseScenePane(
    ink: Color,
    amber: Color,
    type: TextStyle,
    frost: Boolean,
    ambient: (@Composable () -> Unit)? = null
) {
    val body = type.copy(fontSize = 13.sp, color = ink)
    val quiet = type.copy(fontSize = 12.sp, color = ink.copy(alpha = 0.72f))
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("showcase-scene-epistemic")
    ) {
        Box(Modifier.size(1.dp).testTag(if (frost) "scene-frost-on" else "scene-blur-off"))
        BasicText(
            text = ShowcaseScene.INTENT,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .testTag("scene-intent"),
            style = type.copy(fontSize = 15.sp, color = ink)
        )
        if (ambient != null) {
            Box(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                ambient()
            }
        }
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (surface in ShowcaseScene.surfaces()) {
                SceneCard(
                    surface = surface,
                    ink = ink,
                    amber = amber,
                    body = body,
                    quiet = quiet,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SceneCard(
    surface: ShowcaseScene.Surface,
    ink: Color,
    amber: Color,
    body: TextStyle,
    quiet: TextStyle,
    modifier: Modifier
) {
    val rim = when (surface.state) {
        ShowcaseScene.State.STALE -> amber
        ShowcaseScene.State.PENDING -> ink.copy(alpha = 0.85f)
        ShowcaseScene.State.CONTRADICTED -> ink.copy(alpha = 0.55f)
    }
    Box(
        modifier
            .testTag(surface.tag())
            .drawBehind {
                val stroke = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = if (surface.state == ShowcaseScene.State.PENDING) {
                        PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    } else {
                        null
                    }
                )
                drawRoundRect(
                    color = rim,
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    style = stroke
                )
            }
            .padding(10.dp)
    ) {
        if (surface.state == ShowcaseScene.State.PENDING) {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("scene-train-outline")
            )
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(surface.role, style = body)
                when (surface.state) {
                    ShowcaseScene.State.PENDING -> BasicText(
                        ShowcaseScene.PENDING_LABEL,
                        modifier = Modifier.testTag("scene-train-label"),
                        style = body
                    )
                    ShowcaseScene.State.STALE -> BasicText(
                        ShowcaseScene.STALE_BADGE,
                        modifier = Modifier.testTag("scene-hotel-stale"),
                        style = body.copy(color = amber)
                    )
                    ShowcaseScene.State.CONTRADICTED -> BasicText(
                        ShowcaseScene.CONTRADICTED_BADGE,
                        modifier = Modifier.testTag("scene-calendar-badge"),
                        style = body
                    )
                }
            }
            when (surface.state) {
                ShowcaseScene.State.PENDING -> {
                    Spacer(Modifier.weight(1f).testTag("scene-train-empty"))
                }
                ShowcaseScene.State.STALE -> {
                    BasicText(
                        surface.price ?: "",
                        modifier = Modifier.padding(top = 4.dp).testTag("scene-hotel-price"),
                        style = body.copy(fontSize = 22.sp)
                    )
                    BasicText(
                        surface.ageLabel(),
                        modifier = Modifier.testTag("scene-hotel-age"),
                        style = body
                    )
                    DecayBar(surface.decay(), amber, ink)
                }
                ShowcaseScene.State.CONTRADICTED -> {
                    for (slot in surface.slots) {
                        val tag = if (slot == ShowcaseScene.SLOT_A) {
                            "scene-calendar-slot-0900"
                        } else {
                            "scene-calendar-slot-0930"
                        }
                        BasicText(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = ink.copy(alpha = 0.55f),
                                        textDecoration = TextDecoration.LineThrough
                                    )
                                ) { append(slot) }
                            },
                            modifier = Modifier.testTag(tag),
                            style = body.copy(fontSize = 16.sp)
                        )
                    }
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .testTag("scene-calendar-confirm")
                            .semantics { disabled() }
                            .clickable(enabled = false, role = Role.Button, onClick = {})
                            .drawWithContent {
                                drawContent()
                                drawRoundRect(
                                    color = ink.copy(alpha = 0.35f),
                                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        BasicText(ShowcaseScene.CONFIRM_LABEL, style = quiet)
                    }
                    BasicText(
                        surface.forbidReason ?: "",
                        modifier = Modifier.padding(top = 4.dp).testTag("scene-calendar-reason"),
                        style = body
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            ProvenanceRow(surface, ink, quiet)
            if (surface.state != ShowcaseScene.State.STALE) {
                BasicText(
                    surface.ageLabel(),
                    modifier = Modifier.testTag("scene-${surface.id}-age"),
                    style = quiet
                )
            }
        }
    }
}

@Composable
private fun DecayBar(amount: Float, amber: Color, ink: Color) {
    Canvas(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .height(6.dp)
            .testTag("scene-hotel-decay")
    ) {
        drawRoundRect(
            color = ink.copy(alpha = 0.12f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        drawRoundRect(
            color = amber,
            size = Size(size.width * amount.coerceIn(0f, 1f), size.height),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
    }
}

@Composable
private fun ProvenanceRow(
    surface: ShowcaseScene.Surface,
    ink: Color,
    quiet: TextStyle
) {
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("scene-${surface.id}-provenance"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            Modifier
                .size(12.dp)
                .testTag("scene-${surface.id}-provenance-icon")
        ) {
            when (surface.provenance) {
                ShowcaseScene.Provenance.API -> drawRect(ink, size = this.size)
                ShowcaseScene.Provenance.MODEL -> {
                    val path = Path().apply {
                        moveTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(size.width / 2f, size.height)
                        lineTo(0f, size.height / 2f)
                        close()
                    }
                    drawPath(path, ink)
                }
                ShowcaseScene.Provenance.INFERRED -> drawCircle(
                    color = ink,
                    radius = size.minDimension / 2f - 1f,
                    style = Stroke(
                        width = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2f), 0f)
                    )
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        BasicText(
            text = surface.provenance.label,
            modifier = Modifier.testTag("scene-${surface.id}-provenance-label"),
            style = quiet
        )
    }
}

@Composable
fun ShowcaseOverlayPane(ink: Color, type: TextStyle) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("showcase-overlay-host")
    ) {
        BasicText(
            text = ShowcaseOverlay.NOTE,
            modifier = Modifier.testTag("showcase-overlay-note"),
            style = type.copy(fontSize = 16.sp, color = ink)
        )
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = ShowcaseOverlay.INTENT,
            modifier = Modifier.testTag("showcase-overlay-intent"),
            style = type.copy(fontSize = 14.sp, color = ink.copy(alpha = 0.8f))
        )
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = "start: ${ShowcaseOverlay.ANDROID_ACTION}",
            modifier = Modifier.testTag("showcase-overlay-action"),
            style = type.copy(fontSize = 13.sp, color = ink.copy(alpha = 0.7f))
        )
    }
}
