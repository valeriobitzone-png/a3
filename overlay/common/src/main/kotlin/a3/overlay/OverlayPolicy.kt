package a3.overlay

/**
 * Permission and backdrop law. Overlay is off until the user grants the
 * platform window permission. Blur is real capture or it is absent — never faked.
 */
enum class OverlayAvailability {
    ENABLED,
    DISABLED_NO_OVERLAY_PERMISSION
}

enum class BackdropMode {
    REAL_BLUR,
    UNAVAILABLE
}

data class OverlayPermissionState(
    val availability: OverlayAvailability,
    val overlayGranted: Boolean,
    val mediaProjectionGranted: Boolean,
    val accessibilityGranted: Boolean,
    val backdrop: BackdropMode,
    val message: String
)

object OverlayPolicy {
    const val ANDROID_OVERLAY_PERMISSION = "SYSTEM_ALERT_WINDOW"
    const val OVERLAY_DENIED = "overlay disabled: SYSTEM_ALERT_WINDOW not granted"
    const val BLUR_UNAVAILABLE = "blur unavailable"
    const val ACTIVE = "A3 attivo"
    const val MAC_NO_SPECIAL_PERMISSION = "NSWindow.floating: no special permission required"
    const val ACCESSIBILITY_OPTIONAL = "Accessibility optional: contextual surfaces require consent"

    fun android(
        canDrawOverlays: Boolean,
        mediaProjectionGranted: Boolean
    ): OverlayPermissionState {
        if (!canDrawOverlays) {
            return OverlayPermissionState(
                availability = OverlayAvailability.DISABLED_NO_OVERLAY_PERMISSION,
                overlayGranted = false,
                mediaProjectionGranted = mediaProjectionGranted,
                accessibilityGranted = false,
                backdrop = BackdropMode.UNAVAILABLE,
                message = OVERLAY_DENIED
            )
        }
        val backdrop = if (mediaProjectionGranted) BackdropMode.REAL_BLUR else BackdropMode.UNAVAILABLE
        return OverlayPermissionState(
            availability = OverlayAvailability.ENABLED,
            overlayGranted = true,
            mediaProjectionGranted = mediaProjectionGranted,
            accessibilityGranted = false,
            backdrop = backdrop,
            message = if (backdrop == BackdropMode.UNAVAILABLE) BLUR_UNAVAILABLE else ACTIVE
        )
    }

    fun mac(accessibilityGranted: Boolean): OverlayPermissionState {
        return OverlayPermissionState(
            availability = OverlayAvailability.ENABLED,
            overlayGranted = true,
            mediaProjectionGranted = true,
            accessibilityGranted = accessibilityGranted,
            backdrop = BackdropMode.REAL_BLUR,
            message = if (accessibilityGranted) ACTIVE else "$ACTIVE · $ACCESSIBILITY_OPTIONAL"
        )
    }
}
