// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.overlay

object OverlayNativeJson {
    fun session(
        session: OverlaySession,
        blurUnavailable: Boolean,
        frontmost: String?,
        phase: OverlayPhase = OverlayPhase.COLLAPSED,
        mark: OverlayActionMark = OverlayActionMark.UNKNOWN,
        timeoutMs: Long = OverlayLifecycle.DEFAULT_TIMEOUT_MS,
        reducedMotion: Boolean = false,
        dismissOutside: Boolean = true
    ): String {
        val cards = session.cards.joinToString(",") { card ->
            """{"id":${q(card.id)},"price":${q(card.price)},"subtitle":${q(card.subtitle)},"title":${q(card.title)},"url":${q(card.url)}}"""
        }
        return """{"ambient":${q(session.ambient)},"blur_unavailable":$blurUnavailable,"cards":[$cards],"dismiss_outside":$dismissOutside,"frontmost":${q(frontmost)},"intent":${q(session.intent)},"mark":${q(mark.name)},"phase":${q(phase.name)},"reduced_motion":$reducedMotion,"timeout_ms":$timeoutMs}"""
    }

    private fun q(raw: String?): String {
        if (raw == null) return "null"
        val escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
