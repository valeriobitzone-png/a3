package a3.showcase

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.accessibility.AccessibilityManager
import java.io.File

class AndroidShowcaseSink(
    context: Context,
    private val journal: ShowcaseJournal
) : ShowcaseSink {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator: Vibrator? = vibratorOf(app)
    private var track: AudioTrack? = null

    fun silent(): Boolean = audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT

    fun engine(): Boolean = vibrator?.hasVibrator() == true

    override fun playAudio(cause: String, reduced: Boolean, silent: Boolean) {
        val tone = ShowcaseSensory.tone(cause)
        val deviceSilent = silent || this.silent()
        val emit = ShowcaseSensory.emitTone(cause, reduced, deviceSilent)
        val pcm = if (emit != null) ShowcaseSensory.pcm(cause) else null
        journal.recordAudio(tone, emit != null, reduced, pcm)
        if (pcm == null) return
        try {
            track?.run {
                stop()
                release()
            }
            val minBytes = AudioTrack.getMinBufferSize(
                ShowcaseTokens.SAMPLE_RATE,
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
                        .setSampleRate(ShowcaseTokens.SAMPLE_RATE)
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
        } catch (t: Throwable) {
            Log.i(TAG, "audio unavailable ${t.javaClass.simpleName}")
        }
    }

    override fun playHaptic(cause: String, reduced: Boolean, engine: Boolean) {
        val pulse = ShowcaseSensory.pulse(cause)
        val hasEngine = engine && this.engine()
        val emit = ShowcaseSensory.emitPulse(cause, reduced, hasEngine)
        journal.recordHaptic(pulse, emit != null, reduced)
        if (emit == null) {
            if (!hasEngine && !reduced) Log.i(TAG, "haptic engine unavailable")
            return
        }
        try {
            val v = vibrator ?: return
            val effect = VibrationEffect.createWaveform(
                ShowcaseSensory.effectTimings(emit),
                ShowcaseSensory.effectAmplitudes(emit),
                -1
            )
            v.vibrate(effect)
        } catch (t: Throwable) {
            Log.i(TAG, "haptic engine unavailable")
        }
    }

    override fun announce(nodeId: String, phrase: String) {
        journal.recordAnnounce(nodeId, phrase)
        try {
            val am = app.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            if (am.isEnabled) {
                Log.i(TAG, "talkback $nodeId $phrase")
            }
        } catch (_: Throwable) { }
        Log.i(TAG, "announce $nodeId $phrase")
    }

    fun flush(context: Context, journal: ShowcaseJournal) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        dir.mkdirs()
        File(dir, "showcase-haptic.json").writeText(journal.hapticJson("VibrationEffect"))
        File(dir, "showcase-announce.json").writeText(journal.announceJson())
        val pcm = journal.sessionPcm()
        if (pcm.isNotEmpty()) {
            File(dir, "showcase-session.wav").writeBytes(ShowcaseSensory.wav(pcm))
        }
    }

    companion object {
        private const val TAG = "a3-showcase"

        private fun vibratorOf(context: Context): Vibrator? {
            return try {
                if (Build.VERSION.SDK_INT >= 31) {
                    val mgr = context.getSystemService(VibratorManager::class.java)
                    mgr?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            } catch (_: Throwable) {
                null
            }
        }
    }
}
