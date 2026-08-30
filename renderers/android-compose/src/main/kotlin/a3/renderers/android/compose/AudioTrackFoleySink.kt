package a3.renderers.android.compose

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * Device PCM synthesis. Dry one-shots, no assets, no loop.
 */
class AudioTrackFoleySink : FoleySink {
    var durationHintMs: Long = 240
    private var track: AudioTrack? = null
    private var running = 0

    override fun start(cause: String) {
        stop()
        val pcm = render(cause) ?: return
        running = when (cause) {
            FoleyCause.APPROVA -> 2
            FoleyCause.ASCOLTO, FoleyCause.MORPH, FoleyCause.CRACK -> 1
            else -> 0
        }
        log("start cause=$cause")
        try {
            val minBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val needed = (pcm.size * 2).coerceAtLeast(minBytes.coerceAtLeast(2))
            val frames = needed / 2
            val buffer = if (pcm.size == frames) pcm else ShortArray(frames).also { pcm.copyInto(it) }
            val created = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(needed)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            created.write(buffer, 0, buffer.size)
            created.play()
            track = created
        } catch (_: Throwable) {
            track = null
        }
    }

    override fun stop() {
        val playing = track
        track = null
        running = 0
        if (playing != null) {
            try {
                playing.stop()
                playing.release()
            } catch (_: Throwable) { }
        }
        log("stop")
    }

    override fun generators(): Int = running

    override fun masterGain(): Float = Theme.voiceCeiling

    private fun log(event: String) {
        Log.i(
            TAG,
            "$event generators=${generators()} gain=${masterGain()}"
        )
    }

    private fun render(cause: String): ShortArray? = when (cause) {
        FoleyCause.ASCOLTO -> airBed()
        FoleyCause.MORPH -> morphTone()
        FoleyCause.APPROVA -> twoBeats()
        FoleyCause.CRACK -> sawtooth()
        else -> null
    }

    private fun airBed(): ShortArray {
        val n = samples(Theme.airBedMs)
        val out = ShortArray(n)
        var state = 0f
        var seed = 1_103_515_245
        for (i in 0 until n) {
            seed = seed * 1664525 + 1013904223
            val white = ((seed ushr 16) and 0xFFFF) / 32768f - 1f
            state = state * 0.86f + white * 0.14f
            val env = envelope(i, n, fade = 0.2f)
            out[i] = pcm(state * 0.35f * env)
        }
        return out
    }

    private fun morphTone(): ShortArray {
        val n = samples(durationHintMs)
        val out = ShortArray(n)
        val freq = 440.0 * Math.pow(2.0, Theme.morphSemitones / 12.0)
        val step = 2.0 * PI * freq / SAMPLE_RATE
        var phase = 0.0
        for (i in 0 until n) {
            val env = envelope(i, n, fade = 0.15f)
            out[i] = pcm(sin(phase).toFloat() * 0.45f * env)
            phase += step
        }
        return out
    }

    private fun twoBeats(): ShortArray {
        val beat = samples(12)
        val gap = samples(Theme.hapticGapMs)
        val out = ShortArray(beat * 2 + gap)
        writeSharp(out, 0, beat)
        writeSharp(out, beat + gap, beat)
        return out
    }

    private fun sawtooth(): ShortArray {
        val n = samples(Theme.crackMs.toLong())
        val out = ShortArray(n)
        val freq = 220.0
        val step = freq / SAMPLE_RATE
        var phase = 0.0
        for (i in 0 until n) {
            val frac = phase - phase.toInt()
            val saw = (2f * frac.toFloat()) - 1f
            val env = 1f - i.toFloat() / n
            out[i] = pcm(saw * env)
            phase += step
        }
        return out
    }

    private fun writeSharp(out: ShortArray, start: Int, length: Int) {
        var sign = 1f
        for (i in 0 until length) {
            val idx = start + i
            if (idx >= out.size) return
            if (i % 4 == 0) sign = -sign
            val env = envelope(i, length, fade = 0.08f)
            out[idx] = pcm(sign * env)
        }
    }

    private fun samples(ms: Long): Int =
        ((SAMPLE_RATE * ms.coerceAtLeast(1) / 1000L).toInt()).coerceAtLeast(1)

    private fun envelope(i: Int, n: Int, fade: Float): Float {
        val edge = (n * fade).toInt().coerceAtLeast(1)
        val a = when {
            i < edge -> i.toFloat() / edge
            i > n - edge -> (n - i).toFloat() / edge
            else -> 1f
        }
        return a.coerceIn(0f, 1f)
    }

    private fun pcm(raw: Float): Short {
        val v = (raw * Theme.voiceCeiling).coerceIn(-1f, 1f)
        return (v * Short.MAX_VALUE).toInt().toShort()
    }

    companion object {
        private const val TAG = "a3.foley"
        private const val SAMPLE_RATE = 22050
    }
}
