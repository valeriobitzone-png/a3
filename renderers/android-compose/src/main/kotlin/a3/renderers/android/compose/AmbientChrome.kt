package a3.renderers.android.compose

/**
 * Compact-status chrome. Conceptual analogue of a floating island, not Apple
 * Dynamic Island / Live Activities / iOS hardware.
 */
internal object AmbientChrome {
    const val UNAVAILABLE = "ambient indicator unavailable"
    const val NOT_ISLAND = "AmbientIndicator is not Apple Dynamic Island"
    const val ANDROID_OVERLAY = "SYSTEM_ALERT_WINDOW"
    const val ANDROID_PIP = "picture-in-picture"
    const val MAC_WINDOW = "NSWindow"
    const val MAC_LEVEL = "floating"

    enum class Kind { TIMER, AUDIO, CALL }
    enum class Shape { COLLAPSED, EXPANDED }
    enum class Host { OVERLAY, WINDOW, UNAVAILABLE }

    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val radius: Float)

    fun collapsed(): Rect = Rect(20f, 8f, 108f, 32f, 16f)

    fun expanded(): Rect = Rect(8f, 4f, 220f, 64f, 18f)

    fun morph(t: Float): Rect {
        val a = collapsed()
        val b = expanded()
        val u = t.coerceIn(0f, 1f)
        return Rect(
            x = a.x + (b.x - a.x) * u,
            y = a.y + (b.y - a.y) * u,
            w = a.w + (b.w - a.w) * u,
            h = a.h + (b.h - a.h) * u,
            radius = a.radius + (b.radius - a.radius) * u
        )
    }

    fun springExpand(reduced: Boolean = false): List<MotionPhysics.State> {
        val s = GraphicsTokens.motion.comfortable
        return MotionPhysics.integrateSettled(0f, 1f, s.mass, s.stiffness, s.damping, reduced)
    }

    fun caption(kind: Kind): String = when (kind) {
        Kind.TIMER -> "12:00"
        Kind.AUDIO -> "playing"
        Kind.CALL -> "call"
    }

    fun host(granted: Boolean, mac: Boolean): Host {
        if (!granted) return Host.UNAVAILABLE
        return if (mac) Host.WINDOW else Host.OVERLAY
    }
}
