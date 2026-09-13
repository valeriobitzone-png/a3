package a3.renderers.android.compose

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
import a3.a3ui.a11y.MarkKind
import a3.a3ui.a11y.SpokenLaw
import a3.a3ui.a11y.SpokenRequest
import a3.a3ui.model.EpistemicAction
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.EpistemicFreshness
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.EpistemicSupport
import a3.renderers.android.core.model.RenderedNode

enum class ExposureVerb {
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
 * Pure mapping from [RenderedNode] + [EpistemicAxis] to pixel marks, a11y
 * phrases, and sensory motion verbs. Motion never enters presentation or hash.
 */
object Exposure {
    fun of(node: RenderedNode): EpistemicAxis = node.axis ?: EpistemicAxis()

    fun textAlpha(axis: EpistemicAxis): Float = when (axis.support) {
        EpistemicSupport.HIGH -> 1f
        EpistemicSupport.MEDIUM -> 0.75f
        EpistemicSupport.LOW -> 0.50f
        EpistemicSupport.UNKNOWN -> 1f
    }

    fun spokenPhrases(axis: EpistemicAxis): String {
        if (axis.isDefault()) return ""
        val parts = ArrayList<String>()
        val described = axis.stateDescription()
        if (described.isNotEmpty()) parts += described
        if (axis.support == EpistemicSupport.UNKNOWN) parts += "uncertain"
        if (axis.freshness == EpistemicFreshness.STALE) parts += "stale"
        if (axis.status == EpistemicStatus.HELD) parts += "pending review"
        if (axis.status == EpistemicStatus.CONTRADICTED) parts += "contradicted"
        if (axis.action == EpistemicAction.UNKNOWN) parts += "unknown"
        if (axis.action == EpistemicAction.COMPENSATED) parts += "compensated"
        when (SpokenLaw.of(axis)) {
            MarkKind.CONTRADICTED -> {
                parts += "conferma disabilitata"
                parts += "ragione: ${SpokenLaw.normalizeReason(SpokenLaw.FORBID_REASON)}"
            }
            MarkKind.PENDING -> {
                parts += SpokenLaw.PENDING_SLOT
                parts += "conferma disabilitata"
            }
            MarkKind.STALE -> parts += SpokenLaw.STALE_WARNING
            MarkKind.UNKNOWN, MarkKind.HELD -> parts += "conferma disabilitata"
            MarkKind.FACT -> { }
        }
        return SpokenLaw.requireNotSensoryGloss(parts.joinToString(" "))
    }

    fun contentDescription(node: RenderedNode): String {
        val axis = of(node)
        val phrases = spokenPhrases(axis)
        val text = node.text
        if (axis.isDefault() && node.role != "action") {
            return if (node.role == "item" || node.role == "action") text else ""
        }
        val oggetto = text.ifEmpty { "azione" }
        val law = SpokenLaw.reading(SpokenRequest(oggetto = oggetto, mark = SpokenLaw.of(axis)))
        val described = node.stateDescription.ifEmpty { axis.stateDescription() }
        val combined = LinkedHashSet<String>()
        if (law.isNotEmpty()) combined += law
        if (phrases.isNotEmpty()) combined += phrases
        if (described.isNotEmpty()) combined += described
        if (combined.isEmpty()) return text
        return SpokenLaw.requireNotSensoryGloss(combined.joinToString(" "))
    }

    fun markStateDescription(node: RenderedNode): String {
        val axis = of(node)
        val described = node.stateDescription.ifEmpty { axis.stateDescription() }
        if (described.isNotEmpty()) return described
        if (node.role == "action" || !axis.isDefault()) {
            return SpokenLaw.stateDescription(SpokenLaw.of(axis))
        }
        return ""
    }

    fun announcePhrases(axis: EpistemicAxis): List<String> {
        val out = ArrayList<String>()
        if (axis.support == EpistemicSupport.UNKNOWN) out += "uncertain"
        if (axis.freshness == EpistemicFreshness.STALE) out += "stale"
        if (axis.status == EpistemicStatus.HELD) out += "pending review"
        if (axis.status == EpistemicStatus.CONTRADICTED) out += "contradicted"
        if (axis.action == EpistemicAction.UNKNOWN) out += "unknown"
        if (axis.action == EpistemicAction.COMPENSATED) out += "compensated"
        return out
    }

    fun verb(axis: EpistemicAxis): ExposureVerb? = when {
        axis.status == EpistemicStatus.HELD -> ExposureVerb.PULSE
        axis.status == EpistemicStatus.CONTRADICTED -> ExposureVerb.CRACK_REVERSE
        axis.action == EpistemicAction.COMPENSATED -> ExposureVerb.FADE_BACK
        axis.support == EpistemicSupport.UNKNOWN ||
            axis.action == EpistemicAction.UNKNOWN -> ExposureVerb.SHIMMER
        axis.freshness == EpistemicFreshness.STALE -> ExposureVerb.FADE_OUT
        else -> null
    }

    fun motionMs(axis: EpistemicAxis, reduced: Boolean): Int {
        if (reduced) return 0
        return when (verb(axis)) {
            ExposureVerb.PULSE -> Theme.heldPulseMs
            ExposureVerb.SHIMMER -> Theme.unknownShimmerMs
            ExposureVerb.CRACK_REVERSE -> Theme.contradictedCrackMs
            ExposureVerb.FADE_OUT -> Theme.staleFadeMs
            ExposureVerb.FADE_BACK -> Theme.compensatedFadeMs
            null -> 0
        }
    }

    fun style(node: RenderedNode, highContrast: Boolean): TextStyle {
        val axis = of(node)
        val alpha = textAlpha(axis)
        val strike = axis.status == EpistemicStatus.CONTRADICTED ||
            axis.action == EpistemicAction.COMPENSATED
        return Theme.type.copy(
            color = Color.Black.copy(alpha = alpha),
            textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None
        ).let { style ->
            if (highContrast) style.copy(color = Color.Black.copy(alpha = alpha)) else style
        }
    }
}

fun Modifier.exposureChrome(axis: EpistemicAxis, highContrast: Boolean): Modifier {
    if (axis.isDefault()) return this
    val boxed = if (axis.support == EpistemicSupport.UNKNOWN) {
        this.padding(10.dp)
    } else {
        this
    }
    return boxed.drawBehind {
        val ink = Color.Black
        val amber = if (highContrast) ink else Color(0xFFCC8800)
        when (axis.support) {
            EpistemicSupport.MEDIUM -> {
                drawLine(
                    color = ink,
                    start = Offset(2.dp.toPx(), 0f),
                    end = Offset(2.dp.toPx(), size.height),
                    strokeWidth = 3.dp.toPx()
                )
            }
            EpistemicSupport.LOW -> {
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
            EpistemicSupport.UNKNOWN -> {
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
            EpistemicSupport.HIGH -> { }
        }
        when (axis.freshness) {
            EpistemicFreshness.AGING -> {
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
            EpistemicFreshness.STALE -> {
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
            EpistemicFreshness.FRESH -> { }
        }
    }
}
