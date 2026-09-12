package a3.overlay

object OverlayNativeJson {
    fun session(session: OverlaySession, blurUnavailable: Boolean, frontmost: String?): String {
        val cards = session.cards.joinToString(",") { card ->
            """{"id":${q(card.id)},"price":${q(card.price)},"subtitle":${q(card.subtitle)},"title":${q(card.title)},"url":${q(card.url)}}"""
        }
        return """{"ambient":${q(session.ambient)},"blur_unavailable":$blurUnavailable,"cards":[$cards],"frontmost":${q(frontmost)},"intent":${q(session.intent)}}"""
    }

    private fun q(raw: String?): String {
        if (raw == null) return "null"
        val escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
