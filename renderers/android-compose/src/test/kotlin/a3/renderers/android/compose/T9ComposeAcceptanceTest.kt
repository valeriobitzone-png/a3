package a3.renderers.android.compose

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.model.HapticEvent
import a3.a3ui.model.HapticMap
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
import a3.renderers.android.core.model.SemanticHapticEvents
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import java.io.File
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class T9ComposeAcceptanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val t = Instant.parse("2026-08-27T08:00:00Z")
    private val interpreter = A3UIInterpreter()
    private val compiler = DeterministicA3UICompiler(FixedClock(t), SequentialIdGenerator())

    private fun ctx(stage: String = RendererContext.STAGE_PRONTO) = RendererContext(
        formFactor = "phone",
        density = "comfortable",
        tokens = TreeMap<String, ColorValue>().apply {
            put("accent", ColorValue(0, 90, 200))
            put("success", ColorValue(0, 140, 70))
            put("anticipation_highlight", ColorValue(200, 140, 0))
        },
        clock = FixedClock(t),
        stage = stage
    )

    private fun lineage() = CausalLineage("ctx", 0, "ev_demo")

    private fun presentation(id: String = "ps_train") = PresentationState(
        id = id,
        sourceStateVersion = 0,
        producedAt = t,
        atoms = listOf(
            PresentationAtom("timetable", "train.slot.a", "08:45", 50),
            PresentationAtom("timetable", "train.slot.b", "09:12", 50),
            PresentationAtom("timetable", "train.slot.c", "10:03", 50),
            PresentationAtom("departure", "train.departure", "08:45", 90),
            PresentationAtom("passenger", "train.passenger", "Ada", 40),
            PresentationAtom("price", "train.price", "12.40", 60),
            PresentationAtom("confirm", "ticket.owned", "hold 08:45", 97)
        ),
        lineage = lineage()
    )

    private fun projection(presentationId: String, density: Density = Density.COMFORTABLE) = Projection(
        id = "proj_train",
        presentationId = presentationId,
        contextRef = "ctx",
        formFactorHints = FormFactorHints("phone", density),
        interactionRequirements = listOf("attend", "confirm"),
        lineage = lineage(),
        status = ProjectionStatus.PROPOSED
    )

    private fun trainOutput(stage: String = RendererContext.STAGE_PRONTO) =
        interpreter.interpret(
            compiler.compile(projection("ps_train"), presentation()),
            presentation(),
            ctx(stage)
        )

    private fun catalogSource() =
        File("src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt").readText()

    private fun mainSources(vararg roots: String): List<File> =
        roots.flatMap { root ->
            File(root).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

    @Test
    fun G1_train_list_item_occupy_width_and_action_has_weight() {
        val out = trainOutput()
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp).testTag("host-box")) {
                ComposeRenderer(out)
            }
        }
        composeRule.waitForIdle()
        val catalog = composeRule.onNodeWithTag("a3-catalog").fetchSemanticsNode().boundsInRoot
        val list = composeRule.onNodeWithTag("timetable").fetchSemanticsNode().boundsInRoot
        val item = composeRule.onNodeWithTag("item_train.slot.a").fetchSemanticsNode().boundsInRoot
        val action = composeRule.onNodeWithTag("action_ticket.owned").fetchSemanticsNode().boundsInRoot
        assertTrue(list.width > catalog.width * 0.8f, "list width ${list.width} catalog ${catalog.width}")
        assertTrue(item.width > catalog.width * 0.8f, "item width ${item.width} catalog ${catalog.width}")
        assertTrue(action.height > catalog.height * 0.08f, "action height ${action.height} catalog ${catalog.height}")
        val catalogKt = catalogSource()
        assertTrue(catalogKt.contains("weight(Theme.listWeight)"))
        assertTrue(catalogKt.contains("weight(Theme.slotWeight)"))
        assertTrue(catalogKt.contains("Theme.actionMin"))
        assertFalse(catalogKt.contains("160.dp"))
        assertFalse(catalogKt.contains("48.dp"))
        assertTrue(Theme.actionMin != 48.dp)
    }

    @Test
    fun G2_no_new_spec_role_or_meaning_in_renderers_or_launcher() {
        val sources = mainSources(
            "src/main",
            "../android-core/src/main",
            "../../launcher/src/main"
        )
        assertTrue(sources.isNotEmpty())
        val spec = Regex("""(?:data\s+)?class\s+\w+Spec\b""")
        val allowedRoles = setOf("stack", "row", "list", "item", "action", "field", "text")
        val allowedMeanings = setOf("timetable", "departure", "passenger", "price", "confirm")
        val meaningCall = Regex("""PresentationAtom\(\s*"([^"]+)"""")
        for (file in sources) {
            val text = file.readText()
            assertFalse(spec.containsMatchIn(text), "new Spec in ${file.path}")
            for (match in meaningCall.findAll(text)) {
                assertTrue(
                    match.groupValues[1] in allowedMeanings,
                    "${file.path} new meaning ${match.groupValues[1]}"
                )
            }
        }
        val catalog = catalogSource()
        val roleBranch = Regex(""""(\w+)"\s*->""")
        val roles = roleBranch.findAll(catalog).map { it.groupValues[1] }.toSet()
        assertTrue(roles.all { it in allowedRoles }, "catalog roles $roles")
        assertEquals(allowedRoles, allowedRoles.intersect(roles))
    }

    @Test
    fun G3_verbs_bind_prefetch_morph_hold_and_crack() {
        val listening = ctx(RendererContext.STAGE_ASCOLTO)
        val prepared = compiler.compilePrefetch(
            a3.projection.model.ProjectionCandidate(
                id = "pjc_train",
                futureStateId = "fs_demo",
                forecastId = "fc_demo",
                contextRef = "ctx",
                presentation = presentation(),
                baseStateVersion = 0,
                status = a3.projection.model.CandidateStatus.PREPARED,
                expiresAt = t.plusSeconds(300),
                priority = 900,
                rank = 1,
                lineage = lineage()
            )
        )
        val hit = PrefetchComposeCache().composeOffscreen(prepared, listening, 0)
        assertNotNull(hit)
        assertEquals(RendererContext.STAGE_ASCOLTO, listening.stage)

        val motion = File("src/main/kotlin/a3/renderers/android/compose/ComposeMotionApplier.kt").readText()
        assertTrue(motion.contains("params.durationHint"))
        assertTrue(motion.contains("Theme.holdScale"))
        assertTrue(motion.contains("STAGE_APPROVA"))
        assertTrue(motion.contains("Theme.crackMs"))
        assertEquals(1.03f, Theme.holdScale)
        assertTrue(Theme.crackMs <= 80)

        val morph = File("src/main/kotlin/a3/renderers/android/compose/ComposeMorphApplier.kt").readText()
        assertTrue(morph.contains("plan.shared"))
        assertTrue(morph.contains("LaunchedEffect(plan.to, plan.shared)"))

        val compact = presentation("ps_compact")
        val spacious = presentation("ps_spacious")
        val a = interpreter.interpret(
            compiler.compile(projection("ps_compact", Density.COMPACT), compact),
            compact,
            ctx(RendererContext.STAGE_ASCOLTO)
        )
        val b = interpreter.interpret(
            compiler.compile(projection("ps_spacious", Density.SPACIOUS), spacious),
            spacious,
            ctx(RendererContext.STAGE_APPROVA)
        )
        assertEquals("ps_compact", a.sharedElements.to)
        assertEquals("ps_spacious", b.sharedElements.to)
        var output by mutableStateOf(a)
        var stage by mutableStateOf(RendererContext.STAGE_ASCOLTO)
        var crack by mutableStateOf(false)
        composeRule.setContent { ComposeRenderer(output, stage = stage, crack = crack) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
        composeRule.runOnIdle { output = b }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
        composeRule.runOnIdle { stage = RendererContext.STAGE_APPROVA }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("action_ticket.owned").assertIsDisplayed()
        composeRule.runOnIdle { crack = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
    }

    @Test
    fun G4_four_stages_are_four_materials_without_copy_or_dots() {
        val matrices = RendererContext.STAGES.map { Theme.material(it).values.toList() }
        assertEquals(4, matrices.size)
        assertEquals(4, matrices.distinct().size)
        val out = trainOutput()
        var stage by mutableStateOf(RendererContext.STAGE_PRONTO)
        composeRule.setContent { ComposeRenderer(out, stage = stage) }
        for (next in RendererContext.STAGES) {
            composeRule.runOnIdle { stage = next }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("a3-material").assertIsDisplayed()
            composeRule.onNodeWithText(next, substring = false).assertDoesNotExist()
            composeRule.onNodeWithText("•").assertDoesNotExist()
            composeRule.onNodeWithText("●").assertDoesNotExist()
        }
        val renderer = File("src/main/kotlin/a3/renderers/android/compose/ComposeRenderer.kt").readText()
        assertTrue(renderer.contains("Theme.material(stage)"))
        assertFalse(renderer.contains("BasicText(text = stage"))
    }

    @Test
    fun G5_compose_performs_haptic_map() {
        val view = object : View(RuntimeEnvironment.getApplication()) {
            var count = 0
            override fun performHapticFeedback(feedbackConstant: Int): Boolean {
                count++
                return true
            }
        }
        val events = SemanticHapticEvents(
            listOf(
                a3.renderers.android.core.model.SemanticHapticEvent("attend", "tap", "light"),
                a3.renderers.android.core.model.SemanticHapticEvent("confirm", "double-tap", "medium")
            )
        )
        runBlocking { performHapticMap(view, events) }
        assertEquals(3, view.count)
        val haptic = File("src/main/kotlin/a3/renderers/android/compose/ComposeHapticApplier.kt").readText()
        assertTrue(haptic.contains("performHapticFeedback"))
        val train = trainOutput()
        assertTrue(train.haptics.events.any { it.pattern == "tap" })
        assertTrue(train.haptics.events.any { it.pattern == "double-tap" })
        composeRule.setContent { ComposeRenderer(train) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
        val unused = HapticMap(listOf(HapticEvent("attend", "tap", "light")))
        assertEquals("tap", unused.events.first().pattern)
        val core = File("../android-core/src/main").walkTopDown().filter { it.extension == "kt" }
        for (file in core) {
            val text = file.readText()
            assertFalse(text.contains("Vibra" + "tor"), file.name)
            assertFalse(text.contains("perform" + "HapticFeedback"), file.name)
        }
    }

    @Test
    fun G6_renderers_and_launcher_main_stay_on_foundation() {
        val forbidden = listOf("Button(", "Card(", "Dialog(", "AlertDialog(", "Snackbar(")
        val sources = mainSources(
            "src/main",
            "../android-core/src/main",
            "../../launcher/src/main"
        )
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            for (token in forbidden) {
                assertFalse(text.contains(token), "${file.path} $token")
            }
            assertFalse(text.contains("androidx.compose.material"), file.path)
            for (line in text.lineSequence()) {
                if (line.contains("TextField(") && !line.contains("BasicTextField(")) {
                    throw AssertionError("${file.path} TextField: $line")
                }
            }
        }
    }
}
