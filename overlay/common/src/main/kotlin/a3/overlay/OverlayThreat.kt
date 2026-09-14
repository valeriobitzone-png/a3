package a3.overlay

/**
 * Overlay threat law. The overlay sits above the user's work; it is not a
 * surveillance aperture. Capture is never agent input.
 */
enum class OverlayCapturePurpose {
    VISIBLE_BLUR
}

object OverlayCaptureLaw {
    const val AGENT_INPUT_ALLOWED = false
    const val PERSIST_ALLOWED = false
    const val OFF_DEVICE_ALLOWED = false
    const val CONSENT_COPY =
        "Capture is per-session consent; visible blur only; never agent input; never persisted; never off-device"
    val purpose: OverlayCapturePurpose = OverlayCapturePurpose.VISIBLE_BLUR

    /** APIs that would make screen pixels an agent source. Overlay may name them in law; agent must not use them. */
    val FORBIDDEN_AGENT_CAPTURE = listOf(
        "MediaProjection",
        "ImageReader",
        "VirtualDisplay",
        "screencapture",
        "CGDisplayCreateImage",
        "CGWindowListCreateImage",
        "MediaRecorder"
    )

    fun agentMayReadPixels(): Boolean = AGENT_INPUT_ALLOWED
}

/**
 * Android MotionEvent FLAG_WINDOW_IS_OBSCURED / PARTIALLY_OBSCURED.
 * A touch covered by a hostile window MUST NOT dispatch as an A3 action.
 */
object OverlayTapJacking {
    const val FLAG_WINDOW_IS_OBSCURED = 0x1
    const val FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 0x2

    fun allowsA3Action(motionFlags: Int): Boolean {
        val masked = motionFlags and (FLAG_WINDOW_IS_OBSCURED or FLAG_WINDOW_IS_PARTIALLY_OBSCURED)
        return masked == 0
    }
}

/** Marks are drawn only by the overlay. No public spoofing API. */
object OverlayMarkLaw {
    const val PUBLIC_SPOOF_API = false
    const val RENDERER = "overlay-only"

    fun mayForgeFromThirdParty(): Boolean = PUBLIC_SPOOF_API
}

enum class OverlaySurfaceKind {
    ROUTINE,
    SENSITIVE
}

/**
 * Expanded surfaces over other apps are shoulder-surfable.
 * Sensitive surfaces SHOULD stay collapsed/hidden.
 */
object OverlaySensitiveLaw {
    const val SHOULD_COLLAPSE_OR_HIDE = true

    fun defaultPhase(kind: OverlaySurfaceKind): OverlayPhase = OverlayPhase.COLLAPSED

    fun effectivePhase(kind: OverlaySurfaceKind, requested: OverlayPhase): OverlayPhase {
        if (kind == OverlaySurfaceKind.SENSITIVE && SHOULD_COLLAPSE_OR_HIDE) {
            return OverlayPhase.COLLAPSED
        }
        return requested
    }

    fun mayShowExpanded(kind: OverlaySurfaceKind): Boolean =
        kind == OverlaySurfaceKind.ROUTINE || !SHOULD_COLLAPSE_OR_HIDE
}

data class OverlayPermissionRow(
    val permission: String,
    val enables: String,
    val onDeny: String
)

object OverlayPermissionTable {
    val systemAlertWindow = OverlayPermissionRow(
        permission = OverlayPolicy.ANDROID_OVERLAY_PERMISSION,
        enables = "overlay window above other apps (pill + expanded surface)",
        onDeny = OverlayPolicy.OVERLAY_DENIED
    )
    val mediaProjection = OverlayPermissionRow(
        permission = "MediaProjection",
        enables = "one-shot bitmap for visible overlay blur only; never agent input, never persisted, never off-device",
        onDeny = OverlayPolicy.BLUR_UNAVAILABLE
    )
    val accessibility = OverlayPermissionRow(
        permission = "Accessibility (Mac, optional)",
        enables = OverlayMacAx.READS.joinToString("; "),
        onDeny = OverlayPolicy.ACCESSIBILITY_OPTIONAL
    )

    fun rows(): List<OverlayPermissionRow> = listOf(systemAlertWindow, mediaProjection, accessibility)
}

/** Declared Accessibility reads. Content of apps under the overlay is not in this list. */
object OverlayMacAx {
    val READS = listOf(
        "AXIsProcessTrusted (consent bit only)",
        "NSWorkspace frontmost application localizedName (app title, not view content)",
        "AXObserver kAXFocusedWindowChangedNotification with empty callback (no text, no bounds dump)"
    )
}

object OverlayLogRedact {
    private val banned = Regex(
        "(?i)(api[_-]?key|secret|password|bearer\\s|sk_live|AIza[0-9A-Za-z_-]{8}|AKIA[0-9A-Z]{8}|a3[_-]?token)"
    )

    fun admits(line: String): Boolean = !banned.containsMatchIn(line)
}
