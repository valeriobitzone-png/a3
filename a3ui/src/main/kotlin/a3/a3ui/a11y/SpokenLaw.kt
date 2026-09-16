// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui.a11y

import a3.a3ui.model.EpistemicAction
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.EpistemicFreshness
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.EpistemicSupport

/**
 * Non-sensory mark speech for TalkBack and VoiceOver (SPEC_A3UI §7).
 * Never "dimmed button". Never color, blur, pulse, audio, or haptic.
 */
enum class MarkKind {
    UNKNOWN,
    STALE,
    HELD,
    CONTRADICTED,
    PENDING,
    FACT;

    fun wire(): String = name
}

data class SpokenRequest(
    val oggetto: String,
    val mark: MarkKind,
    val ageSpoken: String? = null,
    val priceSpoken: String? = null,
    val extra: String? = null,
    val ctaLabel: String = SpokenLaw.CTA_LABEL,
    val reason: String? = null,
    val ctaEnabled: Boolean? = null
)

object SpokenLaw {
    const val CTA_LABEL = "conferma"
    const val FORBID_REASON = "contradicted: resolve conflict first"
    const val STALE_WARNING = "stale 2 ore"
    const val PENDING_SLOT = "in verifica, slot riservato"
    const val ANDROID_MIN_DP = 48
    const val MAC_MIN_PT = 44

    private val SENSORY_GLOSS = listOf(
        "bottone dimmed",
        "dimmed button",
        "dimmed",
        "grayed",
        "greyed"
    )

    fun of(axis: EpistemicAxis): MarkKind = when {
        axis.status == EpistemicStatus.CONTRADICTED -> MarkKind.CONTRADICTED
        axis.status == EpistemicStatus.HELD -> MarkKind.HELD
        axis.action == EpistemicAction.PENDING -> MarkKind.PENDING
        axis.support == EpistemicSupport.UNKNOWN ||
            axis.action == EpistemicAction.UNKNOWN -> MarkKind.UNKNOWN
        axis.freshness == EpistemicFreshness.STALE -> MarkKind.STALE
        else -> MarkKind.FACT
    }

    fun stateDescription(mark: MarkKind): String = when (mark) {
        MarkKind.UNKNOWN -> "UNKNOWN uncertain confirmation required"
        MarkKind.STALE -> "STALE stale 2 ore warning"
        MarkKind.HELD -> "HELD held pending review"
        MarkKind.CONTRADICTED -> "CONTRADICTED contradicted: resolve conflict first confirm forbidden"
        MarkKind.PENDING -> "PENDING in verifica slot reserved confirm forbidden"
        MarkKind.FACT -> "FACT believed now confirm permitted"
    }

    fun ctaEnabled(mark: MarkKind): Boolean = when (mark) {
        MarkKind.STALE, MarkKind.FACT -> true
        else -> false
    }

    fun defaultReason(mark: MarkKind): String? = when (mark) {
        MarkKind.CONTRADICTED -> FORBID_REASON
        MarkKind.PENDING -> PENDING_SLOT
        MarkKind.UNKNOWN -> "unknown: confirmation required"
        MarkKind.HELD -> "held: pending review"
        else -> null
    }

    fun normalizeReason(raw: String): String =
        raw.replace(":", " ").replace(Regex("\\s+"), " ").trim()

    fun reading(request: SpokenRequest): String {
        val enabled = request.ctaEnabled ?: ctaEnabled(request.mark)
        val parts = ArrayList<String>()
        parts += request.oggetto
        when (request.mark) {
            MarkKind.STALE -> parts += request.ageSpoken ?: STALE_WARNING
            MarkKind.CONTRADICTED -> parts += "contradicted"
            MarkKind.PENDING -> parts += request.extra ?: PENDING_SLOT
            MarkKind.HELD -> parts += "held"
            MarkKind.UNKNOWN -> parts += "uncertain"
            MarkKind.FACT -> { }
        }
        if (request.mark != MarkKind.STALE && request.ageSpoken != null) {
            parts += request.ageSpoken
        }
        request.priceSpoken?.let { parts += it }
        if (request.mark != MarkKind.PENDING) {
            request.extra?.let { parts += it }
        }
        parts += if (enabled) "${request.ctaLabel} abilitata" else "${request.ctaLabel} disabilitata"
        if (!enabled) {
            val reason = request.reason ?: defaultReason(request.mark)
            if (!reason.isNullOrBlank()) {
                parts += "ragione: ${normalizeReason(reason)}"
            }
        } else if (request.mark == MarkKind.STALE) {
            parts += "warning: ${request.ageSpoken ?: STALE_WARNING}"
        }
        return requireNotSensoryGloss(parts.joinToString(", "))
    }

    fun canonicalHotelReading(oggetto: String, priceSpoken: String): String = requireNotSensoryGloss(
        reading(
            SpokenRequest(
                oggetto = oggetto,
                mark = MarkKind.CONTRADICTED,
                ageSpoken = STALE_WARNING,
                priceSpoken = priceSpoken,
                ctaEnabled = false,
                reason = FORBID_REASON
            )
        )
    )

    fun requireNotSensoryGloss(spoken: String): String {
        val lower = spoken.lowercase()
        for (gloss in SENSORY_GLOSS) {
            if (lower.contains(gloss)) {
                throw IllegalStateException("sensory gloss '$gloss' in: $spoken")
            }
        }
        return spoken
    }

    fun marks(): List<MarkKind> = MarkKind.entries
}
