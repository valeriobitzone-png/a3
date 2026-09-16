// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

import android.os.VibrationEffect
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Epistemic and press haptics from a3ui-graphics haptic.json.
 * Android host: VibrationEffect with per-pulse amplitude. Reduced-motion and
 * missing engine disable the path; missing engine logs [UNAVAILABLE].
 */
internal object SystemHaptic {
    const val UNAVAILABLE = "haptic engine unavailable"
    const val ANDROID_HOST = "VibrationEffect"

    object Cause {
        const val HELD = "HELD"
        const val UNKNOWN = "UNKNOWN"
        const val DONE = "DONE"
        const val CONTRADICTED = "CONTRADICTED"
        const val CONFIRM = "CONFIRM"
        const val TAP = "TAP"
    }

    data class Pulse(
        val cause: String,
        val intensity: String,
        val amplitude: Float,
        val durationMs: Int,
        val texture: String,
        val count: Int,
        val gapMs: Int
    )

    fun enabled(reducedMotion: Boolean, engine: Boolean): Boolean = !reducedMotion && engine

    fun emit(cause: String, reducedMotion: Boolean, engine: Boolean, log: (String) -> Unit = {}): Pulse? {
        if (reducedMotion) return null
        if (!engine) {
            log(UNAVAILABLE)
            return null
        }
        return pulse(cause)
    }

    fun pulse(cause: String): Pulse {
        val h = GraphicsTokens.haptic
        return when (cause) {
            Cause.HELD -> of(cause, h.light, "light", 1, h.gapMs)
            Cause.UNKNOWN -> of(cause, h.medium, h.doubleTap.intensity, h.doubleTap.count, h.doubleTap.gapMs)
            Cause.DONE, Cause.CONFIRM, Cause.TAP -> of(cause, h.light, h.tap.intensity, h.tap.count, h.gapMs)
            Cause.CONTRADICTED -> of(cause, h.medium, "medium", 1, h.gapMs)
            else -> error("unmapped haptic cause $cause")
        }
    }

    fun androidAmplitude(amplitude: Float): Int = (amplitude * 255f).roundToInt().coerceIn(1, 255)

    fun effectTimings(pulse: Pulse): LongArray {
        val n = pulse.count.coerceAtLeast(1)
        if (n == 1) return longArrayOf(pulse.durationMs.toLong())
        val out = LongArray(n * 2 - 1)
        var i = 0
        while (i < n) {
            out[i * 2] = pulse.durationMs.toLong()
            if (i < n - 1) out[i * 2 + 1] = pulse.gapMs.toLong()
            i++
        }
        return out
    }

    fun effectAmplitudes(pulse: Pulse): IntArray {
        val a = androidAmplitude(pulse.amplitude)
        val n = pulse.count.coerceAtLeast(1)
        if (n == 1) return intArrayOf(a)
        val out = IntArray(n * 2 - 1)
        var i = 0
        while (i < n) {
            out[i * 2] = a
            if (i < n - 1) out[i * 2 + 1] = 0
            i++
        }
        return out
    }

    fun waveform(pulse: Pulse): VibrationEffect =
        VibrationEffect.createWaveform(effectTimings(pulse), effectAmplitudes(pulse), -1)

    fun mappingJson(): String {
        val causes = listOf(Cause.HELD, Cause.UNKNOWN, Cause.DONE, Cause.CONTRADICTED, Cause.CONFIRM, Cause.TAP)
        val body = causes.joinToString(",\n") { c ->
            val p = pulse(c)
            "    \"$c\": {\"intensity\": \"${p.intensity}\", \"amplitude\": ${fmt(p.amplitude)}, \"durationMs\": ${p.durationMs}, \"texture\": \"${p.texture}\", \"count\": ${p.count}, \"gapMs\": ${p.gapMs}}"
        }
        val h = GraphicsTokens.haptic
        return """
{
  "host": "$ANDROID_HOST",
  "gapMs": ${h.gapMs},
  "light": {"amplitude": ${fmt(h.light.amplitude)}, "durationMs": ${h.light.durationMs}, "texture": "${h.light.texture}"},
  "medium": {"amplitude": ${fmt(h.medium.amplitude)}, "durationMs": ${h.medium.durationMs}, "texture": "${h.medium.texture}"},
  "patterns": {
$body
  },
  "gates": { "reducedMotion": "off", "engineMissing": "$UNAVAILABLE" },
  "deferred": ["localized", "pressure"],
  "mac": { "host": "NSHapticFeedbackManager", "pattern": "generic", "limit": "Mac haptic is limited to generic trackpad Force Touch; not localized." }
}
""".trimIndent() + "\n"
    }

    private fun of(
        cause: String,
        intensity: GraphicsTokens.HapticIntensity,
        name: String,
        count: Int,
        gapMs: Int
    ) = Pulse(cause, name, intensity.amplitude, intensity.durationMs, intensity.texture, count, gapMs)

    private fun fmt(v: Float): String = String.format(Locale.US, "%.4f", v)
}
