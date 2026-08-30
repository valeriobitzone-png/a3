package a3.renderers.android.compose

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.core.time.FixedClock
import a3.core.time.SequentialIdGenerator
import a3.projection.model.CausalLineage
import a3.projection.model.Density
import a3.projection.model.FormFactorHints
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.projection.model.Projection
import a3.projection.model.ProjectionStatus
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RendererContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import java.io.File
import java.time.Instant
import java.util.TreeMap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class T10ComposeAcceptanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val t = Instant.parse("2026-08-27T08:00:00Z")
    private val interpreter = A3UIInterpreter()
    private val compiler = DeterministicA3UICompiler(FixedClock(t), SequentialIdGenerator())

    private fun ctx() = RendererContext(
        formFactor = "phone",
        density = "comfortable",
        tokens = TreeMap<String, ColorValue>().apply {
            put("accent", ColorValue(0, 90, 200))
            put("success", ColorValue(0, 140, 70))
            put("anticipation_highlight", ColorValue(200, 140, 0))
        },
        clock = FixedClock(t)
    )

    private fun lineage() = CausalLineage("ctx", 0, "ev_demo")

    private fun presentation(id: String) = PresentationState(
        id = id,
        sourceStateVersion = 0,
        producedAt = t,
        atoms = listOf(
            PresentationAtom("timetable", "train.slot.a", "08:45", 50),
            PresentationAtom("passenger", "train.passenger", "Ada", 40),
            PresentationAtom("price", "train.price", "12.40", 60),
            PresentationAtom("confirm", "ticket.owned", "hold 08:45", 97)
        ),
        lineage = lineage()
    )

    private fun projection(presentationId: String, density: Density) = Projection(
        id = "proj_train",
        presentationId = presentationId,
        contextRef = "ctx",
        formFactorHints = FormFactorHints("phone", density),
        interactionRequirements = listOf("attend", "confirm"),
        lineage = lineage(),
        status = ProjectionStatus.PROPOSED
    )

    private fun output(id: String, density: Density) = interpreter.interpret(
        compiler.compile(projection(id, density), presentation(id)),
        presentation(id),
        ctx()
    )

    private fun mainSources(vararg roots: String): List<File> =
        roots.flatMap { root ->
            File(root).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

    @Test
    fun A1_binding_and_gain_use_visible_causes_on_counting_sink() {
        assertTrue(Theme.voiceCeiling > 0f)
        assertTrue(Theme.voiceCeiling < 1f)
        val sink = CountingFoleySink()
        assertTrue(sink.masterGain() <= Theme.voiceCeiling)
        assertEquals(Theme.voiceCeiling, sink.masterGain())
        val compact = output("ps_compact", Density.COMPACT)
        val spacious = output("ps_spacious", Density.SPACIOUS)
        assertTrue(compact.spring.durationHint > 0)
        assertTrue(compact.sharedElements.shared.isNotEmpty())
        var rendered by mutableStateOf(compact)
        var stage by mutableStateOf(RendererContext.STAGE_ASCOLTO)
        var crack by mutableStateOf(false)
        composeRule.setContent {
            ComposeRenderer(rendered, stage = stage, crack = crack, foley = sink)
        }
        composeRule.waitForIdle()
        assertTrue(FoleyCause.ASCOLTO in sink.starts)
        composeRule.runOnIdle { rendered = spacious }
        composeRule.waitForIdle()
        assertTrue(FoleyCause.MORPH in sink.starts)
        composeRule.runOnIdle { stage = RendererContext.STAGE_APPROVA }
        composeRule.waitForIdle()
        assertTrue(FoleyCause.APPROVA in sink.starts)
        assertFalse("tap" in sink.starts)
        assertFalse("double-tap" in sink.starts)
        composeRule.runOnIdle { crack = true }
        composeRule.waitForIdle()
        assertTrue(FoleyCause.CRACK in sink.starts)
        composeRule.runOnIdle {
            crack = false
            stage = RendererContext.STAGE_LAVORO
        }
        composeRule.waitForIdle()
        assertEquals(0, sink.generators())
        val frozen = sink.starts.size
        composeRule.runOnIdle { rendered = compact }
        composeRule.waitForIdle()
        assertEquals(0, sink.generators())
        assertEquals(frozen, sink.starts.size)
        composeRule.runOnIdle { stage = RendererContext.STAGE_PRONTO }
        composeRule.waitForIdle()
        assertEquals(0, sink.generators())
        val counting = File("src/main/kotlin/a3/renderers/android/compose/FoleySink.kt").readText()
        assertFalse(counting.contains("Audio" + "Track"))
        assertFalse(counting.contains("android.media"))
        val composeMain = File("src/main").walkTopDown().filter { it.extension == "kt" }
        for (file in composeMain) {
            val text = file.readText()
            assertFalse(text.contains(".wav"), file.path)
            assertFalse(text.contains(".ogg"), file.path)
            assertFalse(text.contains("res/raw"), file.path)
        }
    }

    @Test
    fun A2_no_audio_assets_or_web_audio() {
        val sources = mainSources(
            "src/main",
            "../android-core/src/main",
            "../../launcher/src/main"
        )
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            assertFalse(text.contains(".wav"), file.path)
            assertFalse(text.contains(".ogg"), file.path)
            assertFalse(text.contains("res/raw"), file.path)
            assertFalse(text.contains("AudioContext"), file.path)
            assertFalse(text.contains("webkitAudio"), file.path)
            assertFalse(text.contains("Core Haptics"), file.path)
        }
        val raw = File("src/main/res/raw")
        assertFalse(raw.exists())
    }

    @Test
    fun A3_android_core_has_no_device_audio_or_vibrator() {
        val sources = File("../android-core/src/main").walkTopDown()
            .filter { it.extension == "kt" }
            .toList()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            assertFalse(text.contains("android.media"), file.path)
            assertFalse(text.contains("Audio" + "Track"), file.path)
            assertFalse(text.contains("Sound" + "Pool"), file.path)
            assertFalse(text.contains("Vibra" + "tor"), file.path)
            assertFalse(text.contains("perform" + "HapticFeedback"), file.path)
        }
    }

    @Test
    fun A5_compose_stays_on_foundation() {
        val forbidden = listOf("Button(", "Card(", "Dialog(", "AlertDialog(", "Snackbar(")
        val sources = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            for (token in forbidden) {
                assertFalse(text.contains(token), "${file.path} $token")
            }
            assertFalse(text.contains("androidx.compose.material"), file.path)
        }
        composeRule.setContent {
            ComposeRenderer(
                output("ps_train", Density.COMFORTABLE),
                stage = RendererContext.STAGE_LAVORO,
                foley = CountingFoleySink()
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("a3-catalog").assertExists()
    }
}
