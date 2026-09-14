package a3.overlay

/**
 * Overlay is a persistent widget, not a screen seizure.
 * Default COLLAPSED (pill only). EXPANDED is explicit. Collapse is mandatory.
 */
enum class OverlayPhase {
    COLLAPSED,
    EXPANDED
}

enum class OverlayActionMark {
    PENDING,
    UNKNOWN,
    DONE
}

enum class OverlayCollapseReason {
    DISPATCH,
    DISMISS,
    TIMEOUT,
    TERMINAL,
    UNDER_FOCUS,
    SENSITIVE
}

enum class OverlayTapTarget {
    PILL,
    SURFACE,
    UNDER_APP
}

data class OverlayRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun contains(x: Int, y: Int): Boolean = x >= left && x < right && y >= top && y < bottom
}

data class OverlayLifecycleState(
    val phase: OverlayPhase,
    val mark: OverlayActionMark,
    val reason: OverlayCollapseReason? = null,
    val lastInteractionAt: Long,
    val expandedAt: Long? = null,
    val collapsedAt: Long? = null
)

object OverlayLifecycle {
    const val DEFAULT_TIMEOUT_MS = 20_000L
    const val DISPATCH_BUDGET_MS = 300L
    const val SPRING_MS = 220L
    const val PILL_PENDING = "…"
    const val PILL_UNKNOWN = "?"
    const val PILL_DONE = "✓"
    const val PILL_MAX_CHARS = 2

    fun start(now: Long): OverlayLifecycleState = OverlayLifecycleState(
        phase = OverlayPhase.COLLAPSED,
        mark = OverlayActionMark.UNKNOWN,
        lastInteractionAt = now,
        collapsedAt = now
    )

    fun pillText(mark: OverlayActionMark): String = when (mark) {
        OverlayActionMark.PENDING -> PILL_PENDING
        OverlayActionMark.UNKNOWN -> PILL_UNKNOWN
        OverlayActionMark.DONE -> PILL_DONE
    }

    fun isLongPillText(text: String): Boolean = text.length > PILL_MAX_CHARS

    fun transitionMs(reducedMotion: Boolean): Long = if (reducedMotion) 0L else SPRING_MS

    fun expand(state: OverlayLifecycleState, now: Long): OverlayLifecycleState = OverlayLifecycleState(
        phase = OverlayPhase.EXPANDED,
        mark = state.mark,
        lastInteractionAt = now,
        expandedAt = now
    )

    fun interact(state: OverlayLifecycleState, now: Long): OverlayLifecycleState =
        state.copy(lastInteractionAt = now)

    fun collapse(
        state: OverlayLifecycleState,
        now: Long,
        reason: OverlayCollapseReason
    ): OverlayLifecycleState = OverlayLifecycleState(
        phase = OverlayPhase.COLLAPSED,
        mark = state.mark,
        reason = reason,
        lastInteractionAt = now,
        collapsedAt = now
    )

    fun dispatch(state: OverlayLifecycleState, now: Long): OverlayLifecycleState = OverlayLifecycleState(
        phase = OverlayPhase.COLLAPSED,
        mark = OverlayActionMark.PENDING,
        reason = OverlayCollapseReason.DISPATCH,
        lastInteractionAt = now,
        collapsedAt = now
    )

    fun terminal(state: OverlayLifecycleState, now: Long, worked: Boolean): OverlayLifecycleState =
        OverlayLifecycleState(
            phase = OverlayPhase.COLLAPSED,
            mark = if (worked) OverlayActionMark.DONE else OverlayActionMark.UNKNOWN,
            reason = OverlayCollapseReason.TERMINAL,
            lastInteractionAt = now,
            collapsedAt = state.collapsedAt ?: now
        )

    fun underFocus(state: OverlayLifecycleState, now: Long): OverlayLifecycleState {
        if (state.phase != OverlayPhase.EXPANDED) return state
        return collapse(state, now, OverlayCollapseReason.UNDER_FOCUS)
    }

    /** Sensitive expanded surfaces SHOULD collapse (shoulder surfing). */
    fun hideSensitive(state: OverlayLifecycleState, now: Long): OverlayLifecycleState =
        collapse(state, now, OverlayCollapseReason.SENSITIVE)

    fun tick(
        state: OverlayLifecycleState,
        now: Long,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): OverlayLifecycleState {
        if (state.phase != OverlayPhase.EXPANDED) return state
        if (now - state.lastInteractionAt < timeoutMs) return state
        return collapse(state, now, OverlayCollapseReason.TIMEOUT)
    }

    fun reachesUnder(phase: OverlayPhase, x: Int, y: Int, pill: OverlayRect): Boolean {
        if (phase != OverlayPhase.COLLAPSED) return false
        return !pill.contains(x, y)
    }

    fun tapTarget(
        phase: OverlayPhase,
        x: Int,
        y: Int,
        pill: OverlayRect,
        surface: OverlayRect?
    ): OverlayTapTarget {
        if (pill.contains(x, y)) return OverlayTapTarget.PILL
        if (phase == OverlayPhase.EXPANDED && surface != null && surface.contains(x, y)) {
            return OverlayTapTarget.SURFACE
        }
        return OverlayTapTarget.UNDER_APP
    }
}

/** Collapsed container must not eat touches; the pill remains touchable. */
object OverlayWindowLaw {
    fun containerTouchable(phase: OverlayPhase): Boolean = phase == OverlayPhase.EXPANDED
    fun pillTouchable(): Boolean = true
    fun passThroughOutsidePill(phase: OverlayPhase): Boolean = phase == OverlayPhase.COLLAPSED
}

class OverlayTapLog {
    val underApp = ArrayList<Pair<Int, Int>>()
    val overlay = ArrayList<Pair<Int, Int>>()

    fun tap(phase: OverlayPhase, x: Int, y: Int, pill: OverlayRect) {
        if (OverlayLifecycle.reachesUnder(phase, x, y, pill)) {
            underApp += x to y
        } else {
            overlay += x to y
        }
    }
}
