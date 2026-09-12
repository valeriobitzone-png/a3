package a3.overlay

/** Cross-platform window contract. Android overlay type and Mac visual effect are law, not theme. */
object OverlayContract {
    const val ANDROID_WINDOW_TYPE = "TYPE_APPLICATION_OVERLAY"
    const val ANDROID_API_MIN = 26
    const val MAC_LEVEL = "floating"
    const val MAC_MATERIAL = "fullScreenUI"
    const val MAC_BLENDING = "behindWindow"
    const val PILL_SWIPE_DOWN = "swipe-down"
    const val PILL_LONG_PRESS = "long-press-settings"
    const val MAC_CLOSE = "cmd-w"
}
