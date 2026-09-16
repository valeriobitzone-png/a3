// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.awt.Color
import java.io.File
import java.security.MessageDigest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SensoryMacTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val assets = File("../../review-assets")
    private val reports = File("build/reports")

    private fun savePng(name: String, image: java.awt.image.BufferedImage) {
        reports.mkdirs()
        MacGlassRaster.write(image, File(reports, name))
        assertTrue(File(reports, name).length() > 0)
    }

    private fun saveText(name: String, text: String) {
        reports.mkdirs()
        File(reports, name).writeText(text)
        if (name == "audio-mapping.json") {
            assets.mkdirs()
            File(assets, name).writeText(text)
            assertTrue(File(assets, name).length() > 0)
        }
    }

    @Test
    fun SE_001_system_audio_maps_gestures_from_tokens() {
        val audio = GraphicsTokens.audio
        assertEquals(false, audio.files)
        assertEquals("oneSoundOneVisibleCause", audio.invariant)
        assertEquals(440f, audio.morph.frequencyHz)
        val held = SystemAudio.emit(SystemAudio.Cause.HELD, reducedMotion = false, silent = false)!!
        val unknown = SystemAudio.emit(SystemAudio.Cause.UNKNOWN, reducedMotion = false, silent = false)!!
        val done = SystemAudio.emit(SystemAudio.Cause.DONE, reducedMotion = false, silent = false)!!
        val contra = SystemAudio.emit(SystemAudio.Cause.CONTRADICTED, reducedMotion = false, silent = false)!!
        assertEquals(220f, held.frequencyHz, 0.01f)
        assertEquals(audio.morph.durationMs, held.durationMs)
        assertEquals(440f, unknown.frequencyHz, 0.01f)
        assertEquals(audio.morph.attackMs + audio.morph.decayMs, unknown.durationMs)
        assertEquals(660f, done.endFrequencyHz, 0.01f)
        assertTrue(contra.endFrequencyHz < contra.frequencyHz)
        assertNull(SystemAudio.emit(SystemAudio.Cause.HELD, reducedMotion = true, silent = false))
        val pcm = SystemAudio.pcm(SystemAudio.Cause.DONE)
        val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(peak <= (Short.MAX_VALUE * audio.voiceCeiling).toInt() + 1)
        saveText("audio-mapping.json", SystemAudio.mappingJson())
    }

    @Test
    fun SE_002_silent_disables_audio_without_crash() {
        assertTrue(SystemAudio.deviceSilent(true))
        assertTrue(!SystemAudio.deviceSilent(false))
        assertNull(SystemAudio.emit(SystemAudio.Cause.DONE, reducedMotion = false, silent = true))
        assertTrue(SystemAudio.pcm(SystemAudio.Cause.NOTIFY).isNotEmpty())
    }

    @Test
    fun SE_003_system_haptic_maps_epistemic_from_tokens() {
        val h = GraphicsTokens.haptic
        val held = SystemHaptic.emit(SystemHaptic.Cause.HELD, reducedMotion = false, engine = true)!!
        val unknown = SystemHaptic.emit(SystemHaptic.Cause.UNKNOWN, reducedMotion = false, engine = true)!!
        val done = SystemHaptic.emit(SystemHaptic.Cause.DONE, reducedMotion = false, engine = true)!!
        val contra = SystemHaptic.emit(SystemHaptic.Cause.CONTRADICTED, reducedMotion = false, engine = true)!!
        assertEquals(h.light.amplitude, held.amplitude)
        assertEquals(2, unknown.count)
        assertEquals(h.tap.count, done.count)
        assertEquals(h.medium.amplitude, contra.amplitude)
        assertNull(SystemHaptic.emit(SystemHaptic.Cause.DONE, reducedMotion = true, engine = true))
        saveText("haptic-patterns.json", SystemHaptic.mappingJson())
    }

    @Test
    fun SE_004_haptic_fallback_logs_honestly() {
        val logs = ArrayList<String>()
        assertNull(SystemHaptic.emit(SystemHaptic.Cause.DONE, reducedMotion = false, engine = false) { logs += it })
        assertTrue(logs.contains(SystemHaptic.UNAVAILABLE), logs.toString())
        assertTrue(SystemHaptic.LIMIT.contains("limited"))
        assertEquals("generic", SystemHaptic.trackpadKind())
    }

    @Test
    fun SE_005_glass_refraction_is_measurable_and_subtle() {
        assertTrue(File("src/main/resources/a3ui-graphics/shaders/glass-refraction.metal").exists())
        assertTrue(GlassOptics.METAL_REFRACT.contains("refractionFragment"))
        assertEquals(GlassOptics.LIQUID, "liquid distortion deferred")
        val base = SensoryRaster.glass(refract = false, noise = false, badge = false)
        val refr = SensoryRaster.applyRefraction(base)
        val diffs = SensoryRaster.differCount(base, refr)
        val mean = SensoryRaster.meanAbs(base, refr)
        assertTrue(diffs > 10, "diffs $diffs")
        assertTrue(mean > 0.05 && mean < 40.0, "mean $mean")
        assertTrue(SensoryRaster.badgeIntact(SensoryRaster.stampBadge(base), SensoryRaster.stampBadge(refr)))
        savePng("refraction.png", SensoryRaster.glass(refract = true, noise = false, badge = true))
        composeRule.setContent { GlassOpticsLayer(refract = true, noise = false) }
        composeRule.onNodeWithTag("glass-refraction").assertIsDisplayed()
    }

    @Test
    fun SE_006_glass_noise_is_fine_grain() {
        val base = SensoryRaster.glass(refract = false, noise = false, badge = false)
        val noisy = SensoryRaster.applyNoise(base)
        val diffs = SensoryRaster.differCount(base, noisy)
        val mean = SensoryRaster.meanAbs(base, noisy)
        assertTrue(diffs > 100, "diffs $diffs")
        assertTrue(mean > 0.5 && mean < 16.0, "mean $mean")
        assertTrue(SensoryRaster.badgeIntact(SensoryRaster.stampBadge(base), SensoryRaster.stampBadge(noisy)))
        savePng("noise.png", SensoryRaster.glass(refract = false, noise = true, badge = true))
        composeRule.setContent { GlassOpticsLayer(refract = false, noise = true) }
        composeRule.onNodeWithTag("glass-noise").assertIsDisplayed()
    }

    @Test
    fun SE_007_particles_only_on_trigger() {
        assertTrue(ParticleField.frame(false, 0.3f).isEmpty())
        assertEquals(ParticleField.COUNT, ParticleField.frame(true, 0.3f).size)
        val idle = SensoryRaster.particleFrame(false, 0.3f)
        val a = SensoryRaster.particleFrame(true, 0.25f)
        val b = SensoryRaster.particleFrame(true, 0.7f)
        assertEquals(0, SensoryRaster.brightCount(idle))
        assertTrue(SensoryRaster.brightCount(a) > 0)
        assertTrue(SensoryRaster.differCount(a, b) > 0)
        savePng("particle-frames.png", SensoryRaster.particleStrip())
        composeRule.setContent { ParticleBurst(trigger = true) }
        composeRule.onNodeWithTag("particle-burst").assertIsDisplayed()
    }

    @Test
    fun SE_008_alpha_mask_fades_text_edges() {
        val mask = SensoryRaster.maskingFrame()
        val edge = Color(mask.getRGB(1, 24), true).alpha
        val mid = Color(mask.getRGB(80, 24), true).alpha
        val far = Color(mask.getRGB(158, 24), true).alpha
        assertTrue(edge < mid && far < mid, "edge=$edge mid=$mid far=$far")
        assertEquals(255, mid)
        savePng("masking.png", mask)
        composeRule.setContent { AlphaMaskedStrip() }
        composeRule.onNodeWithTag("alpha-mask").assertIsDisplayed()
    }

    @Test
    fun SE_009_cross_platform_semantics_mac_haptic_limited() {
        assertEquals(220f, SystemAudio.tone(SystemAudio.Cause.HELD).frequencyHz, 0.01f)
        assertEquals(2, SystemHaptic.pulse(SystemHaptic.Cause.UNKNOWN).count)
        assertTrue(SystemHaptic.HOST.contains("NSHapticFeedbackManager"))
        assertTrue(SystemHaptic.LIMIT.contains("limited"))
        assertTrue(SystemHaptic.LIMIT.contains("not localized"))
        val androidAudio = File("../android-compose/src/main/kotlin/a3/renderers/android/compose/SystemAudio.kt").readText()
        assertTrue(androidAudio.contains("RINGER_MODE_SILENT"))
        val androidHaptic = File("../android-compose/src/main/kotlin/a3/renderers/android/compose/SystemHaptic.kt").readText()
        assertTrue(androidHaptic.contains("VibrationEffect"))
    }

    @Test
    fun SE_010_audio_haptic_shader_coexist_with_axis() {
        val glassSrc = File("src/main/kotlin/a3/renderers/mac/compose/MacGlassSurface.kt").readText()
        assertTrue(!glassSrc.contains("refraction") && !glassSrc.contains("noiseTexture"))
        val stamped = SensoryRaster.glass(refract = true, noise = true, badge = true)
        val plain = SensoryRaster.stampBadge(SensoryRaster.glass(refract = false, noise = false, badge = false))
        assertTrue(SensoryRaster.badgeIntact(plain, stamped))
        System.setProperty("a3.reduce.motion", "true")
        val (_, calendar) = MacFixtures.composeCalendar()
        composeRule.mainClock.autoAdvance = true
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                GlassOpticsLayer(refract = true, noise = true, Modifier.fillMaxSize())
                MacRenderer(calendar)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("a3-material").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.hotel-held").assertIsDisplayed()
        composeRule.onAllNodesWithTag("glass-surface", useUnmergedTree = true)[0].assertIsDisplayed()
        composeRule.onNodeWithTag("glass-optics").assertIsDisplayed()
    }

    @Test
    fun SE_011_freeze_only_compose_renderers() {
        val root = File("../..")
        val proc = ProcessBuilder(
            "git", "diff", "--stat", "--",
            "core/", "core/json", "core/admission", "core/action",
            "prediction/", "projection/", "a3ui/", "renderers/android-core/",
            "adapters/", "intent-model/", "broker/", "launcher/"
        ).directory(root).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor())
        assertTrue(out.isBlank(), out)
        val status = ProcessBuilder("git", "status", "--porcelain")
            .directory(root).redirectErrorStream(true).start()
        val porcelain = status.inputStream.bufferedReader().readText()
        assertEquals(0, status.waitFor())
        val allowed = listOf(
            "renderers/android-compose/",
            "renderers/mac-compose/",
            "overlay/",
            "showcase/",
            "docs/",
            "REVIEW_A3UI_PERF.md",
            "REVIEW_RENDERER_MOTION.md",
            "REVIEW_RENDERER_DYNAMIC.md",
            "REVIEW_RENDERER_SHADERS.md",
            "REVIEW_RENDERER_SENSORY.md",
            "REVIEW_RENDERER_BACKDROP.md",
            "review-assets/"
        )
        val ignore = listOf(".kotlin/", ".DS_Store")
        for (line in porcelain.lineSequence().filter { it.isNotBlank() }) {
            val path = line.drop(3).trim().let { if (it.contains(" -> ")) it.substringAfter(" -> ") else it }
            if (ignore.any { path.startsWith(it) }) continue
            assertTrue(allowed.any { path.startsWith(it) }, "unexpected path $line")
        }
        val names = listOf(
            "audio-mapping.json", "haptic-patterns.json",
            "refraction.png", "noise.png", "particle-frames.png", "masking.png"
        )
        for (name in names) {
            val f = File(assets, name)
            if (f.exists()) println("SE asset $name sha1=" + sha1(f))
        }
    }

    private fun sha1(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(file.readBytes())
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
