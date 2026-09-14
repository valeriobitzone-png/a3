package a3.overlay.mac

import a3.overlay.OverlayContract
import a3.overlay.OverlayMacAx
import a3.overlay.OverlayPolicy

object OverlayMacWindow {
    const val LEVEL = OverlayContract.MAC_LEVEL
    const val MATERIAL = OverlayContract.MAC_MATERIAL
    const val BLENDING = OverlayContract.MAC_BLENDING
    const val CLOSE = OverlayContract.MAC_CLOSE
    const val EVENT_ISOLATION = "window level + event isolation"

    fun permissionMessage(): String = OverlayPolicy.MAC_NO_SPECIAL_PERMISSION

    fun accessibilityOptional(): String = OverlayPolicy.ACCESSIBILITY_OPTIONAL

    fun axReads(): List<String> = OverlayMacAx.READS

    fun nativeBinary(): String =
        System.getProperty("a3.overlay.native") ?: error("a3.overlay.native not set")
}
