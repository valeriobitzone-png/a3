package a3.showcase

import a3.a3ui.a11y.MarkKind
import a3.a3ui.a11y.SpokenLaw
import a3.a3ui.a11y.SpokenRequest
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.focusable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.getOrNull
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
import java.io.File

data class A11yFixture(
    val id: String,
    val oggetto: String,
    val mark: MarkKind,
    val price: String? = null,
    val slots: List<String> = emptyList(),
    val ageSpoken: String? = null,
    val extra: String? = null,
    val reason: String? = null,
    val warning: String? = null
) {
    fun request(): SpokenRequest = SpokenRequest(
        oggetto = oggetto,
        mark = mark,
        ageSpoken = ageSpoken,
        priceSpoken = price?.let { "prezzo ${it.replace("€", "").trim()} euro" },
        extra = extra,
        reason = reason
    )

    fun reading(): String = SpokenLaw.reading(request())

    fun stateDescription(): String = SpokenLaw.stateDescription(mark)

    fun ctaEnabled(): Boolean = SpokenLaw.ctaEnabled(mark)
}

object ShowcaseA11y {
    val contradicted = A11yFixture(
        id = "calendar",
        oggetto = "Calendario",
        mark = MarkKind.CONTRADICTED,
        slots = listOf(ShowcaseScene.SLOT_A, ShowcaseScene.SLOT_B),
        reason = SpokenLaw.FORBID_REASON
    )
    val stale = A11yFixture(
        id = "hotel",
        oggetto = "Hotel Milano",
        mark = MarkKind.STALE,
        price = ShowcaseScene.HOTEL_PRICE,
        ageSpoken = SpokenLaw.STALE_WARNING,
        warning = SpokenLaw.STALE_WARNING
    )
    val pending = A11yFixture(
        id = "train",
        oggetto = "Treno",
        mark = MarkKind.PENDING,
        extra = SpokenLaw.PENDING_SLOT
    )
    val fact = A11yFixture(
        id = "flight",
        oggetto = "Volo",
        mark = MarkKind.FACT
    )
    val unknown = A11yFixture(
        id = "unknown",
        oggetto = "Hotel",
        mark = MarkKind.UNKNOWN
    )
    val held = A11yFixture(
        id = "held",
        oggetto = "Hotel",
        mark = MarkKind.HELD
    )

    fun lawful(): List<A11yFixture> = listOf(stale, contradicted, pending, fact)

    fun everyMark(): List<A11yFixture> = listOf(unknown, stale, held, contradicted, pending, fact)

    fun byId(id: String): A11yFixture = (everyMark() + lawful()).distinctBy { it.id }.single { it.id == id }
}

@Composable
fun ShowcaseA11yHost(
    fixtures: List<A11yFixture> = ShowcaseA11y.lawful(),
    reducedMotion: Boolean = false,
    highContrast: Boolean = false,
    caption: String? = null
) {
    val ink = Color(0xFF111111)
    val paper = Color(0xFFF4F1EA)
    val amber = if (highContrast) ink else Color(0xFFB45309)
    val body = TextStyle(fontSize = 14.sp, color = ink)
    val quiet = TextStyle(fontSize = 12.sp, color = ink.copy(alpha = 0.75f))
    val motionMs = if (reducedMotion) 0 else 240
    Column(
        Modifier
            .fillMaxSize()
            .background(paper)
            .padding(12.dp)
            .testTag("a11y-host")
            .semantics {
                contentDescription = "a3ui a11y host"
            }
    ) {
        BasicText(
            text = "Salta ai marchi",
            modifier = Modifier
                .testTag("skip-to-marks")
                .focusable()
                .semantics {
                    contentDescription = "Salta ai marchi"
                    traversalIndex = -1f
                }
                .padding(bottom = 4.dp),
            style = quiet
        )
        Box(Modifier.size(4.dp).testTag(if (reducedMotion) "motion-zero" else "motion-on"))
        Box(Modifier.size(4.dp).testTag(if (highContrast) "high-contrast-on" else "high-contrast-off"))
        BasicText(
            text = "motion-ms=$motionMs",
            modifier = Modifier.testTag("a11y-motion-ms"),
            style = quiet
        )
        if (caption != null) {
            BasicText(
                text = caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("a11y-caption")
                    .semantics { contentDescription = caption },
                style = body
            )
        }
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((index, fixture) in fixtures.withIndex()) {
                A11yCard(
                    fixture = fixture,
                    ink = ink,
                    amber = amber,
                    body = body,
                    quiet = quiet,
                    highContrast = highContrast,
                    traversal = index.toFloat(),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

@Composable
private fun A11yCard(
    fixture: A11yFixture,
    ink: Color,
    amber: Color,
    body: TextStyle,
    quiet: TextStyle,
    highContrast: Boolean,
    traversal: Float,
    modifier: Modifier
) {
    val spoken = fixture.reading()
    val described = fixture.stateDescription()
    val rim = when (fixture.mark) {
        MarkKind.STALE -> amber
        MarkKind.PENDING -> ink.copy(alpha = 0.85f)
        MarkKind.CONTRADICTED -> ink.copy(alpha = 0.55f)
        MarkKind.UNKNOWN -> ink
        MarkKind.HELD -> ink.copy(alpha = 0.7f)
        MarkKind.FACT -> ink.copy(alpha = 0.35f)
    }
    val dashed = fixture.mark == MarkKind.PENDING || fixture.mark == MarkKind.UNKNOWN
    Box(
        modifier
            .testTag("fixture-${fixture.id}")
            .focusable()
            .semantics {
                stateDescription = described
                contentDescription = spoken
                traversalIndex = traversal
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
            Box(Modifier.size(4.dp).testTag("mark-pattern-${fixture.id}"))
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(fixture.oggetto, style = body)
                when (fixture.mark) {
                    MarkKind.PENDING -> BasicText(
                        ShowcaseScene.PENDING_LABEL,
                        modifier = Modifier.testTag("pending-label"),
                        style = body
                    )
                    MarkKind.STALE -> BasicText(
                        fixture.warning ?: SpokenLaw.STALE_WARNING,
                        modifier = Modifier.testTag("stale-warning"),
                        style = body.copy(color = if (highContrast) ink else amber)
                    )
                    MarkKind.CONTRADICTED -> BasicText(
                        "contradicted",
                        modifier = Modifier.testTag("contradicted-badge"),
                        style = body
                    )
                    MarkKind.FACT -> Box(Modifier.size(4.dp).testTag("fact-baseline"))
                    MarkKind.UNKNOWN -> BasicText(
                        "uncertain",
                        modifier = Modifier.testTag("unknown-badge"),
                        style = body
                    )
                    MarkKind.HELD -> BasicText(
                        "[held]",
                        modifier = Modifier.testTag("held-badge"),
                        style = body
                    )
                }
            }
            if (fixture.mark == MarkKind.PENDING) {
                Box(Modifier.fillMaxWidth().height(28.dp).testTag("pending-slot"))
            }
            val price = fixture.price
            if (price != null) {
                BasicText(price, modifier = Modifier.testTag("hotel-price"), style = body.copy(fontSize = 22.sp))
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
            val enabled = fixture.ctaEnabled()
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .testTag("a11y-cta-${fixture.id}")
                    .sizeIn(minWidth = SpokenLaw.ANDROID_MIN_DP.dp, minHeight = SpokenLaw.ANDROID_MIN_DP.dp)
                    .focusable()
                    .semantics {
                        if (!enabled) disabled()
                        stateDescription = described
                        contentDescription = spoken
                        traversalIndex = traversal + 0.1f
                    }
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {}
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                BasicText(SpokenLaw.CTA_LABEL, style = if (enabled) body else quiet)
            }
            if (!enabled && fixture.reason != null) {
                BasicText(
                    fixture.reason,
                    modifier = Modifier.testTag("forbid-reason").padding(top = 4.dp),
                    style = body
                )
            }
            if (fixture.mark == MarkKind.STALE) {
                BasicText(
                    fixture.warning ?: SpokenLaw.STALE_WARNING,
                    modifier = Modifier.testTag("stale-age").padding(top = 4.dp),
                    style = body
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

fun dumpA11yTree(root: SemanticsNode): String {
    fun walk(node: SemanticsNode, indent: Int): String {
        val pad = "  ".repeat(indent)
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        val texts = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()
        val state = node.config.getOrNull(SemanticsProperties.StateDescription)
        val content = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val enabled = !node.config.contains(SemanticsProperties.Disabled)
        val traversal = node.config.getOrNull(SemanticsProperties.TraversalIndex)
        val children = node.children.joinToString(",\n") { walk(it, indent + 1) }
        return buildString {
            append(pad).append("{\n")
            append(pad).append("  \"tag\": ").append(jsonValue(tag)).append(",\n")
            append(pad).append("  \"text\": ").append(jsonArray(texts)).append(",\n")
            append(pad).append("  \"enabled\": ").append(enabled).append(",\n")
            append(pad).append("  \"stateDescription\": ").append(jsonValue(state)).append(",\n")
            append(pad).append("  \"contentDescription\": ").append(jsonValue(content)).append(",\n")
            append(pad).append("  \"traversalIndex\": ").append(traversal ?: "null").append(",\n")
            append(pad).append("  \"width\": ").append(node.size.width).append(",\n")
            append(pad).append("  \"height\": ").append(node.size.height).append(",\n")
            append(pad).append("  \"children\": [")
            if (children.isEmpty()) append("]") else append("\n").append(children).append("\n").append(pad).append("  ]")
            append("\n").append(pad).append("}")
        }
    }
    return walk(root, 0)
}

fun writeA11yTree(file: File, root: SemanticsNode) {
    file.parentFile?.mkdirs()
    file.writeText(dumpA11yTree(root))
}

fun collectSpoken(root: SemanticsNode): List<String> {
    val out = ArrayList<String>()
    fun walk(node: SemanticsNode) {
        node.config.getOrNull(SemanticsProperties.StateDescription)?.let { out += it }
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { out += it }
        for (child in node.children) walk(child)
    }
    walk(root)
    return out
}

fun collectFocusOrder(root: SemanticsNode): List<String> {
    val found = ArrayList<Pair<Float, String>>()
    fun walk(node: SemanticsNode) {
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        val traversal = node.config.getOrNull(SemanticsProperties.TraversalIndex)
        if (tag != null && traversal != null) found += traversal to tag
        for (child in node.children) walk(child)
    }
    walk(root)
    return found.sortedBy { it.first }.map { it.second }
}

private fun jsonValue(value: String?): String =
    if (value == null) "null" else "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""

private fun jsonArray(values: List<String>): String =
    values.joinToString(prefix = "[", postfix = "]") { jsonValue(it) }
