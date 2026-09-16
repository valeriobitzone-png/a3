// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.GestureMap
import a3.a3ui.model.HapticMap
import a3.a3ui.model.MorphSpec
import a3.a3ui.model.MotionSpec
import a3.a3ui.model.Node
import a3.core.time.FixedClock
import a3.projection.model.CausalLineage
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RendererContext
import a3.renderers.android.core.model.SemanticHapticEvents
import android.graphics.Color
import android.media.AudioManager
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.TreeMap
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SensoryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val assets = File("../../review-assets")
    private val reports = File("build/reports")

    private fun savePng(name: String, bitmap: android.graphics.Bitmap) {
        reports.mkdirs()
        assets.mkdirs()
        GlassRaster.write(bitmap, File(reports, name))
        GlassRaster.write(bitmap, File(assets, name))
        assertTrue(File(assets, name).length() > 0)
    }

    private fun saveText(name: String, text: String) {
        reports.mkdirs()
        assets.mkdirs()
        File(reports, name).writeText(text)
        File(assets, name).writeText(text)
        assertTrue(File(assets, name).length() > 0)
    }

    @Test
    fun SE_001_system_audio_maps_gestures_from_tokens() {
        val audio = GraphicsTokens.audio
        assertEquals(false, audio.files)
        assertEquals("oneSoundOneVisibleCause", audio.invariant)
        assertEquals(Theme.voiceCeiling, audio.voiceCeiling)
        assertEquals(440f, audio.morph.frequencyHz)
        val held = SystemAudio.emit(SystemAudio.Cause.HELD, reducedMotion = false, silent = false)!!
        val unknown = SystemAudio.emit(SystemAudio.Cause.UNKNOWN, reducedMotion = false, silent = false)!!
        val done = SystemAudio.emit(SystemAudio.Cause.DONE, reducedMotion = false, silent = false)!!
        val contra = SystemAudio.emit(SystemAudio.Cause.CONTRADICTED, reducedMotion = false, silent = false)!!
        val close = SystemAudio.emit(SystemAudio.Cause.CLOSE, reducedMotion = false, silent = false)!!
        val notify = SystemAudio.emit(SystemAudio.Cause.NOTIFY, reducedMotion = false, silent = false)!!
        val confirm = SystemAudio.emit(SystemAudio.Cause.CONFIRM, reducedMotion = false, silent = false)!!
        assertEquals(220f, held.frequencyHz, 0.01f)
        assertEquals(audio.morph.durationMs, held.durationMs)
        assertEquals(440f, unknown.frequencyHz, 0.01f)
        assertEquals(audio.morph.attackMs + audio.morph.decayMs, unknown.durationMs)
        assertEquals(440f, done.frequencyHz, 0.01f)
        assertEquals(660f, done.endFrequencyHz, 0.01f)
        assertTrue(contra.endFrequencyHz < contra.frequencyHz)
        assertEquals(audio.morph.durationMs, contra.durationMs)
        assertEquals(notify.frequencyHz, unknown.frequencyHz)
        assertEquals(confirm.endFrequencyHz, done.endFrequencyHz)
        assertEquals(close.frequencyHz, contra.endFrequencyHz, 0.01f)
        for (cause in listOf(held, unknown, done, contra, close, notify, confirm)) {
            assertNull(SystemAudio.emit(cause.cause, reducedMotion = true, silent = false))
            val pcm = SystemAudio.pcm(cause.cause)
            assertTrue(pcm.isNotEmpty())
            val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue(peak <= (Short.MAX_VALUE * audio.voiceCeiling).toInt() + 1, "peak $peak")
        }
        saveText("audio-mapping.json", SystemAudio.mappingJson())
    }

    @Test
    fun SE_002_silent_disables_audio_without_crash() {
        assertTrue(SystemAudio.deviceSilent(AudioManager.RINGER_MODE_SILENT))
        assertTrue(!SystemAudio.deviceSilent(AudioManager.RINGER_MODE_NORMAL))
        assertNull(SystemAudio.emit(SystemAudio.Cause.DONE, reducedMotion = false, silent = true))
        val pcm = SystemAudio.pcm(SystemAudio.Cause.DONE)
        assertTrue(pcm.size > 10)
    }

    @Test
    fun SE_003_system_haptic_maps_epistemic_from_tokens() {
        val h = GraphicsTokens.haptic
        assertEquals(Theme.hapticGapMs.toInt(), h.gapMs)
        val held = SystemHaptic.emit(SystemHaptic.Cause.HELD, reducedMotion = false, engine = true)!!
        val unknown = SystemHaptic.emit(SystemHaptic.Cause.UNKNOWN, reducedMotion = false, engine = true)!!
        val done = SystemHaptic.emit(SystemHaptic.Cause.DONE, reducedMotion = false, engine = true)!!
        val contra = SystemHaptic.emit(SystemHaptic.Cause.CONTRADICTED, reducedMotion = false, engine = true)!!
        val tap = SystemHaptic.emit(SystemHaptic.Cause.TAP, reducedMotion = false, engine = true)!!
        assertEquals(h.light.amplitude, held.amplitude)
        assertEquals(1, held.count)
        assertEquals(h.medium.amplitude, unknown.amplitude)
        assertEquals(2, unknown.count)
        assertEquals(h.doubleTap.gapMs, unknown.gapMs)
        assertEquals(h.tap.count, done.count)
        assertEquals(h.light.amplitude, done.amplitude)
        assertEquals(h.medium.amplitude, contra.amplitude)
        assertEquals(1, contra.count)
        assertEquals(tap.amplitude, done.amplitude)
        SystemHaptic.waveform(unknown)
        assertEquals(SystemHaptic.effectTimings(unknown).size, SystemHaptic.effectAmplitudes(unknown).size)
        assertEquals(SystemHaptic.androidAmplitude(h.medium.amplitude), SystemHaptic.effectAmplitudes(unknown)[0])
        for (cause in listOf(held, unknown, done, contra, tap)) {
            assertNull(SystemHaptic.emit(cause.cause, reducedMotion = true, engine = true))
        }
        saveText("haptic-patterns.json", SystemHaptic.mappingJson())
        composeRule.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                Box(Modifier.size(40.dp))
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun SE_004_haptic_fallback_logs_honestly() {
        val logs = ArrayList<String>()
        assertNull(SystemHaptic.emit(SystemHaptic.Cause.DONE, reducedMotion = false, engine = false) { logs += it })
        assertTrue(logs.any { it.contains(SystemHaptic.UNAVAILABLE) }, logs.toString())
        val view = object : View(RuntimeEnvironment.getApplication()) {
            var count = 0
            override fun performHapticFeedback(feedbackConstant: Int): Boolean {
                count++
                return true
            }
        }
        val events = SemanticHapticEvents(
            listOf(a3.renderers.android.core.model.SemanticHapticEvent("confirm", "tap", "light"))
        )
        val viewLogs = ArrayList<String>()
        runBlocking { performHapticMap(view, events, engine = false, log = { viewLogs += it }) }
        assertEquals(0, view.count)
        assertTrue(viewLogs.contains(SystemHaptic.UNAVAILABLE))
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/SystemHaptic.kt").readText().contains("VibrationEffect"))
    }

    @Test
    fun SE_005_glass_refraction_is_measurable_and_subtle() {
        assertTrue(File("src/main/resources/a3ui-graphics/shaders/glass-refraction.agsl").exists())
        assertTrue(GlassOptics.AGSL_REFRACT.contains("half4 main"))
        assertTrue(GlassOptics.METAL_REFRACT.contains("refractionFragment"))
        assertEquals(GlassOptics.LIQUID, "liquid distortion deferred")
        val base = SensoryRaster.glass(refract = false, noise = false, badge = false)
        val refr = SensoryRaster.applyRefraction(base)
        val diffs = SensoryRaster.differCount(base, refr)
        val mean = SensoryRaster.meanAbs(base, refr)
        assertTrue(diffs > 10, "diffs $diffs")
        assertTrue(mean > 0.05 && mean < 40.0, "mean $mean")
        val stampedBase = SensoryRaster.stampBadge(base)
        val stampedRefr = SensoryRaster.stampBadge(refr)
        assertTrue(SensoryRaster.badgeIntact(stampedBase, stampedRefr))
        savePng("refraction.png", SensoryRaster.glass(refract = true, noise = false, badge = true))
        composeRule.setContent { GlassOpticsLayer(refract = true, noise = false) }
        composeRule.onNodeWithTag("glass-refraction").assertIsDisplayed()
    }

    @Test
    fun SE_006_glass_noise_is_fine_grain() {
        assertTrue(File("src/main/resources/a3ui-graphics/shaders/glass-noise.glsl").exists())
        val base = SensoryRaster.glass(refract = false, noise = false, badge = false)
        val noisy = SensoryRaster.applyNoise(base)
        val diffs = SensoryRaster.differCount(base, noisy)
        val mean = SensoryRaster.meanAbs(base, noisy)
        assertTrue(diffs > 100, "diffs $diffs")
        assertTrue(mean > 0.5 && mean < 16.0, "mean $mean")
        val stampedBase = SensoryRaster.stampBadge(base)
        val stampedNoise = SensoryRaster.stampBadge(noisy)
        assertTrue(SensoryRaster.badgeIntact(stampedBase, stampedNoise))
        savePng("noise.png", SensoryRaster.glass(refract = false, noise = true, badge = true))
        composeRule.setContent { GlassOpticsLayer(refract = false, noise = true) }
        composeRule.onNodeWithTag("glass-noise").assertIsDisplayed()
    }

    @Test
    fun SE_007_particles_only_on_trigger() {
        assertTrue(ParticleField.GLSL.contains("emit"))
        assertTrue(ParticleField.frame(false, 0.3f).isEmpty())
        assertEquals(ParticleField.COUNT, ParticleField.frame(true, 0.3f).size)
        val idle = SensoryRaster.particleFrame(false, 0.3f)
        val a = SensoryRaster.particleFrame(true, 0.25f)
        val b = SensoryRaster.particleFrame(true, 0.7f)
        assertEquals(0, SensoryRaster.brightCount(idle))
        assertTrue(SensoryRaster.brightCount(a) > 0)
        assertTrue(SensoryRaster.differCount(a, b) > 0)
        savePng("particle-frames.png", SensoryRaster.particleStrip())
        var trigger by mutableStateOf(true)
        composeRule.setContent { ParticleBurst(trigger = trigger) }
        composeRule.onNodeWithTag("particle-burst").assertIsDisplayed()
        composeRule.runOnIdle { trigger = false }
        composeRule.onNodeWithTag("particle-idle").assertIsDisplayed()
    }

    @Test
    fun SE_008_alpha_mask_fades_text_edges() {
        val mask = SensoryRaster.maskingFrame()
        val edge = Color.alpha(mask.getPixel(1, 24))
        val mid = Color.alpha(mask.getPixel(80, 24))
        val far = Color.alpha(mask.getPixel(158, 24))
        assertTrue(edge < mid && far < mid, "edge=$edge mid=$mid far=$far")
        assertEquals(255, mid)
        assertTrue(AlphaMask.edge(0.5f, 160f) < AlphaMask.edge(80f, 160f))
        savePng("masking.png", mask)
        composeRule.setContent { AlphaMaskedStrip() }
        composeRule.onNodeWithTag("alpha-mask").assertIsDisplayed()
    }

    @Test
    fun SE_009_cross_platform_semantics_mac_haptic_limited() {
        assertEquals(220f, SystemAudio.tone(SystemAudio.Cause.HELD).frequencyHz, 0.01f)
        assertEquals(2, SystemHaptic.pulse(SystemHaptic.Cause.UNKNOWN).count)
        val mac = File("../mac-compose/src/main/kotlin/a3/renderers/mac/compose/SystemHaptic.kt").readText()
        assertTrue(mac.contains("NSHapticFeedbackManager"))
        assertTrue(mac.contains("limited"))
        assertTrue(mac.contains("not localized"))
        assertTrue(!mac.contains("Core Haptics"))
        val macAudio = File("../mac-compose/src/main/kotlin/a3/renderers/mac/compose/SystemAudio.kt").readText()
        assertTrue(macAudio.contains("22050"))
        assertTrue(macAudio.contains("oneSoundOneVisibleCause"))
    }

    @Test
    fun SE_010_audio_haptic_shader_coexist_with_axis() {
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/GlassSurface.kt").exists())
        val glassSrc = File("src/main/kotlin/a3/renderers/android/compose/GlassSurface.kt").readText()
        assertTrue(!glassSrc.contains("refraction") && !glassSrc.contains("noiseTexture"))
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/MotionPhysics.kt").exists())
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/SystemAudio.kt").exists())
        val stamped = SensoryRaster.glass(refract = true, noise = true, badge = true)
        val plain = SensoryRaster.stampBadge(SensoryRaster.glass(refract = false, noise = false, badge = false))
        assertTrue(SensoryRaster.badgeIntact(plain, stamped))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                GlassOpticsLayer(refract = true, noise = true, Modifier.fillMaxSize())
                ComposeRenderer(axisOut(EpistemicAxis(status = EpistemicStatus.HELD)))
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("n1-held").assertIsDisplayed()
        composeRule.onNodeWithTag("glass-surface", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("glass-optics").assertIsDisplayed()
        composeRule.mainClock.autoAdvance = true
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
            val path = line.drop(3).trim().removePrefix("?? ").let {
                if (it.contains(" -> ")) it.substringAfter(" -> ") else it
            }
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

    private fun axisOut(axis: EpistemicAxis) = A3UIInterpreter().interpret(
        A3UISurface(
            id = "s1",
            projectionRef = "p1",
            presentationRef = "ps1",
            lineage = CausalLineage("ctx", 1, "e1"),
            densityHint = "comfortable",
            colorTokens = listOf("accent"),
            motion = MotionSpec(280.0, 24.0, "standard", 240),
            morph = MorphSpec("ps1", "shared-element", emptyList(), MotionSpec(280.0, 24.0, "standard", 240)),
            gestures = GestureMap(emptyList()),
            haptics = HapticMap(emptyList()),
            nodes = listOf(Node("n1", "text", axis = axis)),
            bindings = listOf(Binding("train.price", "n1", "content")),
            producedAt = Instant.parse("2026-08-27T08:00:00Z")
        ),
        PresentationState(
            id = "ps1",
            sourceStateVersion = 1,
            producedAt = Instant.parse("2026-08-27T08:00:00Z"),
            atoms = listOf(PresentationAtom("price", "train.price", "12.40", 60)),
            lineage = CausalLineage("ctx", 1, "e1")
        ),
        RendererContext(
            formFactor = "phone",
            density = "comfortable",
            tokens = TreeMap<String, ColorValue>().apply { put("accent", ColorValue(0, 90, 200)) },
            clock = FixedClock(Instant.parse("2026-08-27T08:00:00Z")),
            reducedMotion = false
        )
    )
}
