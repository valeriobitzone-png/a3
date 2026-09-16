// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import a3.renderers.android.core.model.RenderedNode

enum class MacVerb {
    PULSE,
    SHIMMER,
    CRACK_REVERSE,
    FADE_OUT,
    FADE_BACK;

    fun wire(): String = when (this) {
        PULSE -> "pulse"
        SHIMMER -> "shimmer"
        CRACK_REVERSE -> "crack-reverse"
        FADE_OUT -> "fade-out"
        FADE_BACK -> "fade-back"
    }
}

/**
 * Axis as it arrives on [RenderedNode]: parse [RenderedNode.stateDescription]
 * so this module never imports a3ui types. Semantics match android-compose v0.10.
 */
data class MacAxis(
    val support: String = "high",
    val freshness: String = "fresh",
    val status: String = "believed",
    val action: String = "na"
) {
    fun isDefault(): Boolean =
        support == "high" && freshness == "fresh" && status == "believed" && action == "na"
}

object MacExposure {
    fun of(node: RenderedNode): MacAxis = parse(node.stateDescription)

    fun parse(description: String): MacAxis {
        var support = "high"
        var freshness = "fresh"
        var status = "believed"
        var action = "na"
        val parts = description.split(" ")
        var i = 0
        while (i + 1 < parts.size) {
            when (parts[i]) {
                "support" -> support = parts[i + 1]
                "freshness" -> freshness = parts[i + 1]
                "status" -> status = parts[i + 1]
                "action" -> action = parts[i + 1]
            }
            i += 2
        }
        return MacAxis(support, freshness, status, action)
    }

    fun textAlpha(axis: MacAxis): Float = when (axis.support) {
        "medium" -> 0.75f
        "low" -> 0.50f
        else -> 1f
    }

    fun spokenPhrases(node: RenderedNode): String {
        val axis = of(node)
        if (axis.isDefault()) return ""
        val parts = ArrayList<String>()
        val described = node.stateDescription
        if (described.isNotEmpty()) parts += described
        if (axis.support == "unknown") parts += "uncertain"
        if (axis.freshness == "stale") parts += "stale"
        if (axis.status == "held") parts += "pending review"
        if (axis.status == "contradicted") parts += "contradicted"
        if (axis.action == "unknown") parts += "unknown"
        if (axis.action == "compensated") parts += "compensated"
        when (markOf(axis)) {
            "CONTRADICTED" -> {
                parts += "conferma disabilitata"
                parts += "ragione: contradicted resolve conflict first"
            }
            "PENDING" -> {
                parts += "in verifica, slot riservato"
                parts += "conferma disabilitata"
            }
            "STALE" -> parts += "stale 2 ore"
            "UNKNOWN", "HELD" -> parts += "conferma disabilitata"
            else -> { }
        }
        val spoken = parts.joinToString(" ")
        requireNotSensoryGloss(spoken)
        return spoken
    }

    fun contentDescription(node: RenderedNode): String {
        val axis = of(node)
        val phrases = spokenPhrases(node)
        val text = node.text
        if (axis.isDefault() && node.role != "action") {
            return if (node.role == "item" || node.role == "action") text else ""
        }
        val oggetto = text.ifEmpty { "azione" }
        val law = reading(oggetto, markOf(axis))
        val described = node.stateDescription
        val combined = LinkedHashSet<String>()
        if (law.isNotEmpty()) combined += law
        if (phrases.isNotEmpty()) combined += phrases
        if (described.isNotEmpty()) combined += described
        if (combined.isEmpty()) return text
        val spoken = combined.joinToString(" ")
        requireNotSensoryGloss(spoken)
        return spoken
    }

    fun markStateDescription(node: RenderedNode): String {
        val axis = of(node)
        if (node.stateDescription.isNotEmpty()) return node.stateDescription
        if (node.role == "action" || !axis.isDefault()) {
            return stateDescription(markOf(axis))
        }
        return ""
    }

    fun ctaEnabled(axis: MacAxis): Boolean = when (markOf(axis)) {
        "STALE", "FACT" -> true
        else -> false
    }

    fun markOf(axis: MacAxis): String = when {
        axis.status == "contradicted" -> "CONTRADICTED"
        axis.status == "held" -> "HELD"
        axis.action == "pending" -> "PENDING"
        axis.support == "unknown" || axis.action == "unknown" -> "UNKNOWN"
        axis.freshness == "stale" -> "STALE"
        else -> "FACT"
    }

    fun stateDescription(mark: String): String = when (mark) {
        "UNKNOWN" -> "UNKNOWN uncertain confirmation required"
        "STALE" -> "STALE stale 2 ore warning"
        "HELD" -> "HELD held pending review"
        "CONTRADICTED" -> "CONTRADICTED contradicted: resolve conflict first confirm forbidden"
        "PENDING" -> "PENDING in verifica slot reserved confirm forbidden"
        else -> "FACT believed now confirm permitted"
    }

    fun reading(oggetto: String, mark: String): String {
        val enabled = mark == "STALE" || mark == "FACT"
        val parts = ArrayList<String>()
        parts += oggetto
        when (mark) {
            "STALE" -> parts += "stale 2 ore"
            "CONTRADICTED" -> parts += "contradicted"
            "PENDING" -> parts += "in verifica, slot riservato"
            "HELD" -> parts += "held"
            "UNKNOWN" -> parts += "uncertain"
        }
        parts += if (enabled) "conferma abilitata" else "conferma disabilitata"
        if (!enabled) {
            val reason = when (mark) {
                "CONTRADICTED" -> "contradicted resolve conflict first"
                "PENDING" -> "in verifica, slot riservato"
                "UNKNOWN" -> "unknown confirmation required"
                "HELD" -> "held pending review"
                else -> null
            }
            if (reason != null) parts += "ragione: $reason"
        } else if (mark == "STALE") {
            parts += "warning: stale 2 ore"
        }
        val spoken = parts.joinToString(", ")
        requireNotSensoryGloss(spoken)
        return spoken
    }

    fun requireNotSensoryGloss(spoken: String) {
        val lower = spoken.lowercase()
        if (lower.contains("dimmed") || lower.contains("grayed") || lower.contains("greyed")) {
            throw IllegalStateException("sensory gloss in: $spoken")
        }
    }

    fun verb(axis: MacAxis): MacVerb? = when {
        axis.status == "held" -> MacVerb.PULSE
        axis.status == "contradicted" -> MacVerb.CRACK_REVERSE
        axis.action == "compensated" -> MacVerb.FADE_BACK
        axis.support == "unknown" || axis.action == "unknown" -> MacVerb.SHIMMER
        axis.freshness == "stale" -> MacVerb.FADE_OUT
        else -> null
    }

    fun motionMs(axis: MacAxis, reduced: Boolean): Int {
        if (reduced) return 0
        return when (verb(axis)) {
            MacVerb.PULSE -> MacTheme.heldPulseMs
            MacVerb.SHIMMER -> MacTheme.unknownShimmerMs
            MacVerb.CRACK_REVERSE -> MacTheme.contradictedCrackMs
            MacVerb.FADE_OUT -> MacTheme.staleFadeMs
            MacVerb.FADE_BACK -> MacTheme.compensatedFadeMs
            null -> 0
        }
    }

    fun style(node: RenderedNode, highContrast: Boolean): TextStyle {
        val axis = of(node)
        val alpha = textAlpha(axis)
        val strike = axis.status == "contradicted" || axis.action == "compensated"
        val color = Color.Black.copy(alpha = alpha)
        return MacTheme.type.copy(
            color = color,
            textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None
        ).let { style ->
            if (highContrast) style.copy(color = color) else style
        }
    }
}

fun Modifier.macChrome(axis: MacAxis, highContrast: Boolean): Modifier {
    if (axis.isDefault()) return this
    val boxed = if (axis.support == "unknown") this.padding(10.dp) else this
    return boxed.drawBehind {
        val ink = Color.Black
        val amber = if (highContrast) ink else Color(0xFFCC8800)
        when (axis.support) {
            "medium" -> drawLine(
                color = ink,
                start = Offset(2.dp.toPx(), 0f),
                end = Offset(2.dp.toPx(), size.height),
                strokeWidth = 3.dp.toPx()
            )
            "low" -> {
                drawLine(
                    color = ink,
                    start = Offset(2.dp.toPx(), 0f),
                    end = Offset(2.dp.toPx(), size.height),
                    strokeWidth = 3.dp.toPx()
                )
                drawLine(
                    color = ink,
                    start = Offset(7.dp.toPx(), 0f),
                    end = Offset(7.dp.toPx(), size.height),
                    strokeWidth = 3.dp.toPx()
                )
            }
            "unknown" -> {
                val inset = 3.dp.toPx()
                drawRect(
                    color = ink,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 6.dp.toPx())
                        )
                    )
                )
            }
        }
        when (axis.freshness) {
            "aging" -> {
                if (highContrast) {
                    val step = 8.dp.toPx().coerceAtLeast(1f)
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
                } else {
                    drawRect(ink.copy(alpha = 0.06f))
                }
            }
            "stale" -> {
                drawRect(color = amber, style = Stroke(width = 3.dp.toPx()))
                if (highContrast) {
                    val inset = 4.dp.toPx()
                    drawRect(
                        color = ink,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2f, size.height - inset * 2f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    val step = 10.dp.toPx().coerceAtLeast(1f)
                    var x = 0f
                    while (x < size.width + size.height) {
                        drawLine(
                            color = ink,
                            start = Offset(x, 0f),
                            end = Offset(x - size.height, size.height),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        x += step
                    }
                }
            }
        }
    }
}
