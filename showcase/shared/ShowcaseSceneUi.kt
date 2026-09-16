// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.showcase

import a3.a3ui.a11y.SpokenLaw
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.focusable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
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
    reducedMotion: Boolean = false,
    highContrast: Boolean = false,
    ambient: (@Composable () -> Unit)? = null
) {
    val body = type.copy(fontSize = 13.sp, color = ink)
    val quiet = type.copy(fontSize = 12.sp, color = ink.copy(alpha = 0.72f))
    val markInk = if (highContrast) ink else amber
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("showcase-scene-epistemic")
    ) {
        Box(Modifier.size(1.dp).testTag(if (frost) "scene-frost-on" else "scene-blur-off"))
        Box(Modifier.size(1.dp).testTag(if (reducedMotion) "motion-zero" else "motion-on"))
        Box(Modifier.size(1.dp).testTag(if (highContrast) "high-contrast-on" else "high-contrast-off"))
        BasicText(
            text = "Salta ai marchi",
            modifier = Modifier
                .testTag("skip-to-marks")
                .focusable()
                .semantics {
                    contentDescription = "Salta ai marchi"
                    traversalIndex = -1f
                }
                .padding(bottom = 2.dp),
            style = quiet
        )
        BasicText(
            text = ShowcaseScene.INTENT,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .testTag("scene-intent")
                .semantics { traversalIndex = 0f },
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
            for ((index, surface) in ShowcaseScene.surfaces().withIndex()) {
                SceneCard(
                    surface = surface,
                    ink = ink,
                    amber = markInk,
                    body = body,
                    quiet = quiet,
                    highContrast = highContrast,
                    traversal = (index + 1).toFloat(),
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
    highContrast: Boolean,
    traversal: Float,
    modifier: Modifier
) {
    val spoken = surface.reading()
    val described = surface.stateDescription()
    val rim = when (surface.state) {
        ShowcaseScene.State.STALE -> amber
        ShowcaseScene.State.PENDING -> ink.copy(alpha = 0.85f)
        ShowcaseScene.State.CONTRADICTED -> ink.copy(alpha = 0.55f)
        ShowcaseScene.State.FACT -> ink.copy(alpha = 0.35f)
    }
    Box(
        modifier
            .testTag(surface.tag())
            .focusable()
            .semantics {
                stateDescription = described
                contentDescription = spoken
                traversalIndex = traversal
            }
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
                if (highContrast) {
                    val step = 10.dp.toPx()
                    var x = 0f
                    while (x < size.width + size.height) {
                        drawLine(
                            color = ink,
                            start = Offset(x, 0f),
                            end = Offset(x - size.height, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                        x += step
                    }
                }
            }
            .padding(10.dp)
    ) {
        if (highContrast) {
            Box(Modifier.size(4.dp).testTag("scene-${surface.id}-pattern"))
        }
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
                        style = body.copy(color = if (highContrast) ink else amber)
                    )
                    ShowcaseScene.State.CONTRADICTED -> BasicText(
                        ShowcaseScene.CONTRADICTED_BADGE,
                        modifier = Modifier.testTag("scene-calendar-badge"),
                        style = body
                    )
                    ShowcaseScene.State.FACT -> Box(Modifier.size(4.dp).testTag("scene-flight-baseline"))
                }
            }
            when (surface.state) {
                ShowcaseScene.State.PENDING -> {
                    Spacer(Modifier.weight(1f).testTag("scene-train-empty"))
                    SceneCta(surface, ink, body, quiet, "scene-train-confirm")
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
                    DecayBar(surface.decay(), amber, ink, highContrast)
                    SceneCta(surface, ink, body, quiet, "scene-hotel-confirm")
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
                    SceneCta(surface, ink, body, quiet, "scene-calendar-confirm")
                    BasicText(
                        surface.forbidReason ?: "",
                        modifier = Modifier.padding(top = 4.dp).testTag("scene-calendar-reason"),
                        style = body
                    )
                }
                ShowcaseScene.State.FACT -> {
                    SceneCta(surface, ink, body, quiet, "scene-flight-confirm")
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
private fun SceneCta(
    surface: ShowcaseScene.Surface,
    ink: Color,
    body: TextStyle,
    quiet: TextStyle,
    tag: String
) {
    val enabled = surface.confirmEnabled
    Box(
        Modifier
            .padding(top = 4.dp)
            .testTag(tag)
            .sizeIn(minWidth = SpokenLaw.ANDROID_MIN_DP.dp, minHeight = SpokenLaw.ANDROID_MIN_DP.dp)
            .focusable()
            .semantics {
                if (!enabled) disabled()
                stateDescription = surface.stateDescription()
                contentDescription = surface.reading()
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {}
            )
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = ink.copy(alpha = if (enabled) 0.55f else 0.35f),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        BasicText(ShowcaseScene.CONFIRM_LABEL, style = if (enabled) body else quiet)
    }
}

@Composable
private fun DecayBar(amount: Float, amber: Color, ink: Color, highContrast: Boolean) {
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
            color = if (highContrast) ink else amber,
            size = Size(size.width * amount.coerceIn(0f, 1f), size.height),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        if (highContrast) {
            drawRoundRect(
                color = ink,
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }
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
