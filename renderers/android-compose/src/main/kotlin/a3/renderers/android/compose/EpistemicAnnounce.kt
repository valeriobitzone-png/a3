package a3.renderers.android.compose

/**
 * TalkBack-facing announce sink for critical epistemic transitions.
 * Motion is not a substitute for this.
 */
fun interface EpistemicAnnounce {
    fun announce(nodeId: String, phrase: String)

    companion object {
        val Silent = EpistemicAnnounce { _, _ -> }
    }
}

class CountingAnnounce : EpistemicAnnounce {
    val phrases = ArrayList<Pair<String, String>>()

    override fun announce(nodeId: String, phrase: String) {
        phrases += nodeId to phrase
    }
}
