// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.showcase.mac

import a3.showcase.ShowcaseJournal
import a3.showcase.ShowcaseSensory
import a3.showcase.ShowcaseSink
import a3.showcase.ShowcaseTokens
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

class MacShowcaseSink(
    private val journal: ShowcaseJournal
) : ShowcaseSink {
    override fun playAudio(cause: String, reduced: Boolean, silent: Boolean) {
        val tone = ShowcaseSensory.tone(cause)
        val emit = ShowcaseSensory.emitTone(cause, reduced, silent)
        val pcm = if (emit != null) ShowcaseSensory.pcm(cause) else null
        journal.recordAudio(tone, emit != null, reduced, pcm)
        if (pcm == null) return
        Thread {
            try {
                val format = AudioFormat(ShowcaseTokens.SAMPLE_RATE.toFloat(), 16, 1, true, false)
                val info = DataLine.Info(SourceDataLine::class.java, format)
                val line = AudioSystem.getLine(info) as SourceDataLine
                line.open(format)
                line.start()
                val bytes = ByteArray(pcm.size * 2)
                var i = 0
                var o = 0
                while (i < pcm.size) {
                    val s = pcm[i].toInt()
                    bytes[o] = (s and 0xFF).toByte()
                    bytes[o + 1] = ((s shr 8) and 0xFF).toByte()
                    i++
                    o += 2
                }
                line.write(bytes, 0, bytes.size)
                line.drain()
                line.stop()
                line.close()
            } catch (_: Throwable) { }
        }.start()
    }

    override fun playHaptic(cause: String, reduced: Boolean, engine: Boolean) {
        val pulse = ShowcaseSensory.pulse(cause)
        val emit = ShowcaseSensory.emitPulse(cause, reduced, engine)
        journal.recordHaptic(pulse, emit != null, reduced)
        if (emit == null) return
        println("haptic ${pulse.cause} ${pulse.intensity} ${pulse.durationMs}ms generic Force Touch")
    }

    override fun announce(nodeId: String, phrase: String) {
        journal.recordAnnounce(nodeId, phrase)
        println("announce $nodeId $phrase")
    }

    fun flush(journal: ShowcaseJournal) {
        val dir = assetDir().apply { mkdirs() }
        File(dir, "showcase-haptic.json").writeText(journal.hapticJson("NSHapticFeedbackManager"))
        File(dir, "showcase-announce.json").writeText(journal.announceJson())
        val pcm = journal.sessionPcm()
        if (pcm.isNotEmpty()) {
            File(dir, "showcase-session.wav").writeBytes(ShowcaseSensory.wav(pcm))
        }
    }
}
