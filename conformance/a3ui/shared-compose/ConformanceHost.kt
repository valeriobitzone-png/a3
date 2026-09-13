package a3.a3ui.conformance.ui

import a3.a3ui.conformance.A3UiFixtures
import a3.a3ui.conformance.CtaLaw
import a3.a3ui.conformance.SurfaceFixture
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConformanceHost(
    reducedMotion: Boolean = false,
    blurOff: Boolean = false,
    fixtures: List<SurfaceFixture> = A3UiFixtures.lawful()
) {
    val ink = Color(0xFF111111)
    val paper = if (blurOff) Color(0xFFF4F1EA) else Color(0xFFE8E4D9)
    val amber = Color(0xFFB45309)
    val body = TextStyle(fontSize = 14.sp, color = ink)
    val quiet = TextStyle(fontSize = 12.sp, color = ink.copy(alpha = 0.75f))
    val motionMs = if (reducedMotion) 0 else 240
    Column(
        Modifier
            .fillMaxSize()
            .background(paper)
            .graphicsLayer()
            .padding(12.dp)
            .testTag("conformance-host")
            .semantics {
                contentDescription = "a3ui conformance host"
            }
    ) {
        Box(Modifier.size(4.dp).testTag(if (blurOff) "scene-blur-off" else "scene-frost-on"))
        BasicText(
            text = "motion-ms=$motionMs",
            modifier = Modifier.testTag(if (reducedMotion) "motion-zero" else "motion-on"),
            style = quiet
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            for (fixture in fixtures) {
                FixtureCard(fixture, ink, amber, body, quiet, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun FixtureCard(
    fixture: SurfaceFixture,
    ink: Color,
    amber: Color,
    body: TextStyle,
    quiet: TextStyle,
    modifier: Modifier
) {
    val rim = when (fixture.mark) {
        "STALE" -> amber
        "PENDING" -> ink.copy(alpha = 0.85f)
        "CONTRADICTED" -> ink.copy(alpha = 0.55f)
        else -> ink.copy(alpha = 0.35f)
    }
    val dashed = fixture.mark == "PENDING"
    Box(
        modifier
            .testTag("fixture-${fixture.id}")
            .semantics {
                stateDescription = fixture.stateDescription
                contentDescription = fixture.stateDescription
            }
            .drawBehind {
                drawRoundRect(
                    color = rim,
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) else null
                    )
                )
            }
            .padding(10.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(fixture.oggetto, style = body)
                when (fixture.mark) {
                    "PENDING" -> BasicText(
                        fixture.pendingLabel ?: CtaLaw.PENDING_LABEL,
                        modifier = Modifier.testTag("pending-label"),
                        style = body
                    )
                    "STALE" -> BasicText(
                        fixture.warning ?: CtaLaw.STALE_WARNING,
                        modifier = Modifier.testTag("stale-warning"),
                        style = body.copy(color = amber)
                    )
                    "CONTRADICTED" -> BasicText(
                        "contradicted",
                        modifier = Modifier.testTag("contradicted-badge"),
                        style = body
                    )
                    "FACT" -> Box(Modifier.size(4.dp).testTag("fact-baseline"))
                }
            }
            if (fixture.mark == "PENDING") {
                Box(Modifier.fillMaxWidth().height(28.dp).testTag("pending-slot"))
            }
            val price = fixture.price
            if (price != null) {
                BasicText(price, modifier = Modifier.testTag("hotel-price"), style = body.copy(fontSize = 22.sp))
                BasicText(fixture.age, modifier = Modifier.testTag("hotel-age"), style = body)
            }
            for (slot in fixture.slots) {
                val tag = if (slot == "9:00") "slot-0900" else "slot-0930"
                BasicText(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = ink.copy(alpha = 0.55f), textDecoration = TextDecoration.LineThrough)) {
                            append(slot)
                        }
                    },
                    modifier = Modifier.testTag(tag),
                    style = body.copy(fontSize = 16.sp)
                )
            }
            val ctaTag = "cta-${fixture.id}"
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .testTag(ctaTag)
                    .semantics {
                        if (!fixture.ctaEnabled) disabled()
                        stateDescription = fixture.stateDescription
                        contentDescription = fixture.stateDescription
                    }
                    .clickable(
                        enabled = fixture.ctaEnabled,
                        role = Role.Button,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {}
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                BasicText(fixture.ctaLabel, style = if (fixture.ctaEnabled) body else quiet)
            }
            if (fixture.reasonVisible) {
                BasicText(
                    fixture.reason ?: "",
                    modifier = Modifier.testTag("forbid-reason").padding(top = 4.dp),
                    style = body
                )
            }
            Spacer(Modifier.height(4.dp))
            BasicText("${fixture.provenance} · ${fixture.truthClass}", style = quiet)
        }
    }
}
