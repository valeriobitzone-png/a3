package a3.overlay.mac

import a3.overlay.OverlayContract
import a3.overlay.OverlayPolicy

object OverlayMacWindow {
    const val LEVEL = OverlayContract.MAC_LEVEL
    const val MATERIAL = OverlayContract.MAC_MATERIAL
    const val BLENDING = OverlayContract.MAC_BLENDING
    const val CLOSE = OverlayContract.MAC_CLOSE

    fun permissionMessage(): String = OverlayPolicy.MAC_NO_SPECIAL_PERMISSION

    fun accessibilityOptional(): String = OverlayPolicy.ACCESSIBILITY_OPTIONAL

    fun nativeBinary(): String =
        System.getProperty("a3.overlay.native") ?: error("a3.overlay.native not set")
}
