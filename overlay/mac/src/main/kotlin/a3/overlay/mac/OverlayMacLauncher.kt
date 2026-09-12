package a3.overlay.mac

import a3.overlay.OverlayFlight
import a3.overlay.OverlayNativeJson
import java.io.File

object OverlayMacLauncher {
    fun launch(
        native: String,
        holdSeconds: Int,
        json: File,
        extraEnv: Map<String, String> = emptyMap()
    ): Process {
        json.writeText(OverlayNativeJson.session(OverlayFlight.present(), blurUnavailable = false, frontmost = "Safari"))
        val env = ProcessBuilder(native, "--json", json.absolutePath)
        env.environment()["A3_OVERLAY_HOLD"] = holdSeconds.toString()
        extraEnv.forEach { (k, v) -> env.environment()[k] = v }
        env.redirectErrorStream(true)
        return env.start()
    }
}
