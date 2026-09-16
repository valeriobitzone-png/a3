// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.overlay.mac

import a3.overlay.OverlayActionMark
import a3.overlay.OverlayFlight
import a3.overlay.OverlayNativeJson
import a3.overlay.OverlayPhase
import java.io.File

object OverlayMacLauncher {
    fun launch(
        native: String,
        holdSeconds: Int,
        json: File,
        extraEnv: Map<String, String> = emptyMap(),
        phase: OverlayPhase = OverlayPhase.COLLAPSED,
        mark: OverlayActionMark = OverlayActionMark.UNKNOWN,
        choose: String? = null,
        reducedMotion: Boolean = false
    ): Process {
        json.writeText(
            OverlayNativeJson.session(
                OverlayFlight.present(),
                blurUnavailable = false,
                frontmost = "Safari",
                phase = phase,
                mark = mark,
                reducedMotion = reducedMotion
            )
        )
        val cmd = ArrayList<String>()
        cmd += native
        cmd += "--json"
        cmd += json.absolutePath
        if (phase == OverlayPhase.EXPANDED) cmd += "--expanded"
        if (reducedMotion) cmd += "--reduced"
        if (choose != null) {
            cmd += "--choose"
            cmd += choose
        }
        val env = ProcessBuilder(cmd)
        env.environment()["A3_OVERLAY_HOLD"] = holdSeconds.toString()
        extraEnv.forEach { (k, v) -> env.environment()[k] = v }
        env.redirectErrorStream(true)
        return env.start()
    }
}
