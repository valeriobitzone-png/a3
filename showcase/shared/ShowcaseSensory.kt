package a3.showcase

import a3.a3ui.model.EpistemicAction
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.EpistemicFreshness
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.EpistemicSupport
import a3.renderers.android.core.model.RenderedOutput
import java.util.ArrayList
import java.util.Locale
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Same cause → tone / pulse mapping as renderer SystemAudio / SystemHaptic,
 * consumed from audio.json / haptic.json (not invented).
 */
object ShowcaseSensory {
    object Cause {
        const val HELD = "HELD"
        const val UNKNOWN = "UNKNOWN"
        const val DONE = "DONE"
        const val CONTRADICTED = "CONTRADICTED"
        const val CONFIRM = "CONFIRM"
        const val CLOSE = "CLOSE"
        const val NOTIFY = "NOTIFY"
        const val TAP = "TAP"
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

    data class Pulse(
        val cause: String,
        val intensity: String,
        val amplitude: Float,
        val durationMs: Int,
        val texture: String,
        val count: Int,
        val gapMs: Int
    )

    fun audioEnabled(reducedMotion: Boolean, silent: Boolean): Boolean = !reducedMotion && !silent

    fun hapticEnabled(reducedMotion: Boolean, engine: Boolean): Boolean = !reducedMotion && engine

    fun emitTone(cause: String, reducedMotion: Boolean, silent: Boolean): Tone? {
        if (!audioEnabled(reducedMotion, silent)) return null
        return tone(cause)
    }

    fun emitPulse(cause: String, reducedMotion: Boolean, engine: Boolean): Pulse? {
        if (!hapticEnabled(reducedMotion, engine)) return null
        return pulse(cause)
    }

    fun tone(cause: String): Tone {
        val audio = ShowcaseTokens.snapshot.audio
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

    fun pulse(cause: String): Pulse {
        val h = ShowcaseTokens.snapshot.haptic
        return when (cause) {
            Cause.HELD -> of(cause, h.light, "light", 1, h.gapMs)
            Cause.UNKNOWN -> of(cause, h.medium, h.doubleTap.intensity, h.doubleTap.count, h.doubleTap.gapMs)
            Cause.DONE, Cause.CONFIRM, Cause.TAP -> of(cause, h.light, h.tap.intensity, h.tap.count, h.gapMs)
            Cause.CONTRADICTED -> of(cause, h.medium, "medium", 1, h.gapMs)
            else -> error("unmapped haptic cause $cause")
        }
    }

    fun pcm(cause: String): ShortArray {
        val t = tone(cause)
        val n = ((ShowcaseTokens.SAMPLE_RATE * t.durationMs) / 1000).coerceAtLeast(1)
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
            p1 += twoPi * f1 / ShowcaseTokens.SAMPLE_RATE
            if (dyad) p2 += twoPi * t.endFrequencyHz / ShowcaseTokens.SAMPLE_RATE
        }
        return out
    }

    fun wav(pcm: ShortArray, sampleRate: Int = ShowcaseTokens.SAMPLE_RATE): ByteArray {
        val dataSize = pcm.size * 2
        val out = ByteArray(44 + dataSize)
        fun put(s: String, at: Int) {
            s.encodeToByteArray().copyInto(out, at)
        }
        fun le32(v: Int, at: Int) {
            out[at] = (v and 0xFF).toByte()
            out[at + 1] = ((v shr 8) and 0xFF).toByte()
            out[at + 2] = ((v shr 16) and 0xFF).toByte()
            out[at + 3] = ((v shr 24) and 0xFF).toByte()
        }
        fun le16(v: Int, at: Int) {
            out[at] = (v and 0xFF).toByte()
            out[at + 1] = ((v shr 8) and 0xFF).toByte()
        }
        put("RIFF", 0)
        le32(36 + dataSize, 4)
        put("WAVE", 8)
        put("fmt ", 12)
        le32(16, 16)
        le16(1, 20)
        le16(1, 22)
        le32(sampleRate, 24)
        le32(sampleRate * 2, 28)
        le16(2, 32)
        le16(16, 34)
        put("data", 36)
        le32(dataSize, 40)
        var i = 0
        var o = 44
        while (i < pcm.size) {
            val s = pcm[i].toInt()
            out[o] = (s and 0xFF).toByte()
            out[o + 1] = ((s shr 8) and 0xFF).toByte()
            i++
            o += 2
        }
        return out
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

    fun causesOf(output: RenderedOutput): List<String> {
        val seen = LinkedHashSet<String>()
        for (node in CalendarFixture.flatten(output)) {
            val axis = node.axis ?: continue
            for (cause in causesOf(axis)) seen += cause
        }
        return seen.toList()
    }

    fun causesOf(axis: EpistemicAxis): List<String> {
        val out = ArrayList<String>()
        if (axis.status == EpistemicStatus.HELD) out += Cause.HELD
        if (axis.support == EpistemicSupport.UNKNOWN || axis.action == EpistemicAction.UNKNOWN) out += Cause.UNKNOWN
        if (axis.action == EpistemicAction.DONE) out += Cause.DONE
        if (axis.status == EpistemicStatus.CONTRADICTED) out += Cause.CONTRADICTED
        return out
    }

    fun announcePhrases(axis: EpistemicAxis): List<String> {
        val out = ArrayList<String>()
        if (axis.support == EpistemicSupport.UNKNOWN) out += "uncertain"
        if (axis.freshness == EpistemicFreshness.STALE) out += "stale"
        if (axis.status == EpistemicStatus.HELD) out += "pending review"
        if (axis.status == EpistemicStatus.CONTRADICTED) out += "contradicted"
        if (axis.action == EpistemicAction.UNKNOWN) out += "unknown"
        if (axis.action == EpistemicAction.COMPENSATED) out += "compensated"
        return out
    }

    fun announceAll(output: RenderedOutput): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (node in CalendarFixture.flatten(output)) {
            val axis = node.axis ?: continue
            for (phrase in announcePhrases(axis)) out += node.id to phrase
        }
        return out
    }

    fun audioCauses(): List<String> = listOf(
        Cause.HELD, Cause.UNKNOWN, Cause.DONE, Cause.CONTRADICTED,
        Cause.CONFIRM, Cause.CLOSE, Cause.NOTIFY
    )

    fun hapticCauses(): List<String> = listOf(
        Cause.HELD, Cause.UNKNOWN, Cause.DONE, Cause.CONTRADICTED, Cause.CONFIRM, Cause.TAP
    )

    private fun of(
        cause: String,
        intensity: ShowcaseTokens.Intensity,
        name: String,
        count: Int,
        gapMs: Int
    ) = Pulse(cause, name, intensity.amplitude, intensity.durationMs, intensity.texture, count, gapMs)

    private fun envelope(i: Int, n: Int, t: Tone): Float {
        val sr = ShowcaseTokens.SAMPLE_RATE.toFloat()
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

    fun fmt(v: Float): String = String.format(Locale.US, "%.4f", v)
}
