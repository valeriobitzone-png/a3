package a3.renderers.mac.compose

import java.util.Locale
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Procedural system tones from a3ui-graphics audio.json (morph token).
 * No binary assets. One cause, one sound. Mock-friendly via [emit].
 */
internal object SystemAudio {
    const val SAMPLE_RATE = 22050
    const val INVARIANT = "oneSoundOneVisibleCause"

    object Cause {
        const val HELD = "HELD"
        const val UNKNOWN = "UNKNOWN"
        const val DONE = "DONE"
        const val CONTRADICTED = "CONTRADICTED"
        const val CONFIRM = "CONFIRM"
        const val CLOSE = "CLOSE"
        const val NOTIFY = "NOTIFY"
    }

    data class Tone(
        val cause: String,
        val frequencyHz: Float,
        val endFrequencyHz: Float,
        val durationMs: Int,
        val amplitude: Float,
        val attackMs: Int,
        val decayMs: Int,
        val sustain: Float,
        val releaseMs: Int
    )

    fun enabled(reducedMotion: Boolean, silent: Boolean): Boolean = !reducedMotion && !silent

    fun deviceSilent(silent: Boolean): Boolean = silent

    fun emit(cause: String, reducedMotion: Boolean, silent: Boolean): Tone? {
        if (!enabled(reducedMotion, silent)) return null
        return tone(cause)
    }

    fun tone(cause: String): Tone {
        val audio = GraphicsTokens.audio
        val morph = audio.morph
        val tonic = morph.frequencyHz
        val down = tonic * 2f.pow(morph.semitones / 12f)
        val fifth = tonic * 1.5f
        val tickMs = morph.attackMs + morph.decayMs
        val ceil = audio.voiceCeiling
        return when (cause) {
            Cause.HELD -> Tone(cause, tonic / 2f, tonic / 2f, morph.durationMs, ceil * 0.5f, morph.attackMs, morph.decayMs, morph.sustain, morph.releaseMs)
            Cause.UNKNOWN, Cause.NOTIFY -> Tone(cause, tonic, tonic, tickMs, ceil, morph.attackMs, morph.decayMs, 0f, 0)
            Cause.DONE, Cause.CONFIRM -> Tone(cause, tonic, fifth, morph.durationMs, ceil, morph.attackMs, morph.decayMs, morph.sustain, morph.releaseMs)
            Cause.CONTRADICTED -> Tone(cause, tonic, down, morph.durationMs, ceil, morph.attackMs, morph.decayMs, morph.sustain, morph.releaseMs)
            Cause.CLOSE -> Tone(cause, down, down, morph.durationMs, ceil, morph.attackMs, morph.decayMs, morph.sustain, morph.releaseMs)
            else -> error("unmapped audio cause $cause")
        }
    }

    fun pcm(cause: String): ShortArray {
        val t = tone(cause)
        val n = ((SAMPLE_RATE * t.durationMs) / 1000).coerceAtLeast(1)
        val out = ShortArray(n)
        val twoPi = 2.0 * PI
        var p1 = 0.0
        var p2 = 0.0
        val dyad = cause == Cause.DONE || cause == Cause.CONFIRM
        val slide = cause == Cause.CONTRADICTED
        for (i in 0 until n) {
            val env = envelope(i, n, t)
            val u = if (n <= 1) 0.0 else i / (n - 1.0)
            val f1 = if (slide) t.frequencyHz + (t.endFrequencyHz - t.frequencyHz) * u.toFloat() else t.frequencyHz
            val s = if (dyad) {
                0.5f * (sin(p1).toFloat() + sin(p2).toFloat())
            } else {
                sin(p1).toFloat()
            }
            val v = (s * env * t.amplitude).coerceIn(-1f, 1f)
            out[i] = (v * Short.MAX_VALUE).toInt().toShort()
            p1 += twoPi * f1 / SAMPLE_RATE
            if (dyad) p2 += twoPi * t.endFrequencyHz / SAMPLE_RATE
        }
        return out
    }

    fun mappingJson(): String {
        val causes = listOf(
            Cause.HELD, Cause.UNKNOWN, Cause.DONE, Cause.CONTRADICTED,
            Cause.CONFIRM, Cause.CLOSE, Cause.NOTIFY
        )
        val body = causes.joinToString(",\n") { c ->
            val t = tone(c)
            "    \"$c\": {\"frequencyHz\": ${fmt(t.frequencyHz)}, \"endFrequencyHz\": ${fmt(t.endFrequencyHz)}, \"durationMs\": ${t.durationMs}, \"amplitude\": ${fmt(t.amplitude)}}"
        }
        val a = GraphicsTokens.audio
        return """
{
  "invariant": "${a.invariant}",
  "synthesis": "${a.synthesis}",
  "files": ${a.files},
  "voiceCeiling": ${fmt(a.voiceCeiling)},
  "morphTokenHz": ${fmt(a.morph.frequencyHz)},
  "morphSemitones": ${a.morph.semitones},
  "morphDurationMs": ${a.morph.durationMs},
  "tones": {
$body
  },
  "gates": { "reducedMotion": "off", "silent": "off" }
}
""".trimIndent() + "\n"
    }

    private fun envelope(i: Int, n: Int, t: Tone): Float {
        val sr = SAMPLE_RATE.toFloat()
        val attack = (t.attackMs / 1000f) * sr
        val decay = (t.decayMs / 1000f) * sr
        val release = (t.releaseMs / 1000f) * sr
        val ti = i.toFloat()
        val value = when {
            attack > 0f && ti < attack -> ti / attack
            ti < attack + decay -> {
                val u = if (decay <= 0f) 1f else (ti - attack) / decay
                1f + (t.sustain - 1f) * u
            }
            t.releaseMs <= 0 -> t.sustain
            else -> {
                val relStart = (n - release).coerceAtLeast(attack + decay)
                if (ti < relStart) t.sustain
                else t.sustain * (1f - (ti - relStart) / (n - relStart).coerceAtLeast(1f))
            }
        }
        return value.coerceIn(0f, 1f)
    }

    private fun fmt(v: Float): String = String.format(Locale.US, "%.4f", v)
}
