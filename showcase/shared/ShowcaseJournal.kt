package a3.showcase

import java.util.ArrayList
import java.util.Collections

class ShowcaseJournal {
    data class HapticHit(
        val cause: String,
        val intensity: String,
        val amplitude: Float,
        val durationMs: Int,
        val count: Int,
        val gapMs: Int,
        val emitted: Boolean,
        val reducedMotion: Boolean,
        val atMs: Long
    )

    data class AudioHit(
        val cause: String,
        val frequencyHz: Float,
        val durationMs: Int,
        val emitted: Boolean,
        val reducedMotion: Boolean,
        val atMs: Long
    )

    data class AnnounceHit(
        val nodeId: String,
        val phrase: String,
        val atMs: Long
    )

    private val t0 = System.currentTimeMillis()
    private val hapticHits = Collections.synchronizedList(ArrayList<HapticHit>())
    private val audioHits = Collections.synchronizedList(ArrayList<AudioHit>())
    private val announceHits = Collections.synchronizedList(ArrayList<AnnounceHit>())
    private val pcmTape = Collections.synchronizedList(ArrayList<Short>())

    fun haptic(): List<HapticHit> = synchronized(hapticHits) { hapticHits.toList() }
    fun audio(): List<AudioHit> = synchronized(audioHits) { audioHits.toList() }
    fun announce(): List<AnnounceHit> = synchronized(announceHits) { announceHits.toList() }

    fun recordHaptic(pulse: ShowcaseSensory.Pulse, emitted: Boolean, reduced: Boolean) {
        hapticHits += HapticHit(
            pulse.cause, pulse.intensity, pulse.amplitude, pulse.durationMs,
            pulse.count, pulse.gapMs, emitted, reduced, now()
        )
    }

    fun recordAudio(tone: ShowcaseSensory.Tone, emitted: Boolean, reduced: Boolean, pcm: ShortArray? = null) {
        audioHits += AudioHit(tone.cause, tone.frequencyHz, tone.durationMs, emitted, reduced, now())
        if (emitted && pcm != null) {
            synchronized(pcmTape) {
                pcm.forEach { pcmTape += it }
            }
        }
    }

    fun recordAnnounce(nodeId: String, phrase: String) {
        announceHits += AnnounceHit(nodeId, phrase, now())
    }

    fun sessionPcm(): ShortArray = synchronized(pcmTape) { pcmTape.toShortArray() }

    fun hapticJson(host: String): String {
        val mapping = ShowcaseSensory.hapticCauses().joinToString(",\n") { c ->
            val p = ShowcaseSensory.pulse(c)
            "    \"$c\": {\"intensity\": \"${p.intensity}\", \"amplitude\": ${ShowcaseSensory.fmt(p.amplitude)}, \"durationMs\": ${p.durationMs}, \"texture\": \"${p.texture}\", \"count\": ${p.count}, \"gapMs\": ${p.gapMs}}"
        }
        val events = haptic().joinToString(",\n") { e ->
            "    {\"cause\": \"${e.cause}\", \"intensity\": \"${e.intensity}\", \"amplitude\": ${ShowcaseSensory.fmt(e.amplitude)}, \"durationMs\": ${e.durationMs}, \"count\": ${e.count}, \"emitted\": ${e.emitted}, \"reducedMotion\": ${e.reducedMotion}, \"atMs\": ${e.atMs}}"
        }
        val h = ShowcaseTokens.snapshot.haptic
        return """
{
  "host": "$host",
  "gapMs": ${h.gapMs},
  "light": {"amplitude": ${ShowcaseSensory.fmt(h.light.amplitude)}, "durationMs": ${h.light.durationMs}, "texture": "${h.light.texture}"},
  "medium": {"amplitude": ${ShowcaseSensory.fmt(h.medium.amplitude)}, "durationMs": ${h.medium.durationMs}, "texture": "${h.medium.texture}"},
  "patterns": {
$mapping
  },
  "events": [
$events
  ],
  "gates": { "reducedMotion": "zeros audio and haptic", "keepChroma": ${ShowcaseTokens.snapshot.keepChroma} }
}
""".trimIndent() + "\n"
    }

    fun announceJson(): String {
        val body = announce().joinToString(",\n") { e ->
            "    {\"nodeId\": \"${e.nodeId}\", \"phrase\": \"${e.phrase}\", \"atMs\": ${e.atMs}}"
        }
        return "{\n  \"announces\": [\n$body\n  ]\n}\n"
    }

    private fun now(): Long = System.currentTimeMillis() - t0
}

interface ShowcaseSink {
    fun playAudio(cause: String, reduced: Boolean, silent: Boolean)
    fun playHaptic(cause: String, reduced: Boolean, engine: Boolean)
    fun announce(nodeId: String, phrase: String)
}

class LoggingSink(
    private val journal: ShowcaseJournal,
    private val host: String = "log"
) : ShowcaseSink {
    override fun playAudio(cause: String, reduced: Boolean, silent: Boolean) {
        val tone = ShowcaseSensory.tone(cause)
        val emit = ShowcaseSensory.emitTone(cause, reduced, silent)
        val pcm = if (emit != null) ShowcaseSensory.pcm(cause) else null
        journal.recordAudio(tone, emit != null, reduced, pcm)
    }

    override fun playHaptic(cause: String, reduced: Boolean, engine: Boolean) {
        val pulse = ShowcaseSensory.pulse(cause)
        val emit = ShowcaseSensory.emitPulse(cause, reduced, engine)
        journal.recordHaptic(pulse, emit != null, reduced)
    }

    override fun announce(nodeId: String, phrase: String) {
        journal.recordAnnounce(nodeId, phrase)
    }

    fun host(): String = host
}
