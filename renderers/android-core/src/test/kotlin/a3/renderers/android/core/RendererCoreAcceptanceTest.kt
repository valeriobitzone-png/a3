package a3.renderers.android.core

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.serialize.CanonicalJson as SurfaceCanonical
import a3.core.time.FixedClock
import a3.core.time.SequentialIdGenerator
import a3.core.world.api.Fact
import a3.core.world.api.ReadBelief
import a3.projection.engine.ProjectionEngine
import a3.projection.engine.ProjectionEventLog
import a3.projection.model.Density
import a3.projection.model.FormFactorHints
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.interp.DensityResolver
import a3.renderers.android.core.interp.GestureInterpreter
import a3.renderers.android.core.interp.HapticInterpreter
import a3.renderers.android.core.interp.MorphInterpreter
import a3.renderers.android.core.interp.MotionInterpreter
import a3.renderers.android.core.interp.TokenResolver
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RenderedOutput
import a3.renderers.android.core.model.RendererContext
import a3.renderers.android.core.serialize.CanonicalJson
import java.io.File
import java.time.Instant
import java.util.TreeMap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RendererCoreAcceptanceTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")
    private val interpreter = A3UIInterpreter()

    private fun tokens(): TreeMap<String, ColorValue> {
        val map = TreeMap<String, ColorValue>()
        map["accent"] = ColorValue(0, 90, 200)
        map["success"] = ColorValue(0, 140, 70)
        map["anticipation_highlight"] = ColorValue(200, 140, 0)
        return map
    }

    private fun ctx(
        density: String = "comfortable",
        formFactor: String = "phone"
    ) = RendererContext(
        formFactor = formFactor,
        density = density,
        tokens = tokens(),
        clock = FixedClock(t),
        currentStateVersion = 0
    )

    private fun trainSurface(density: Density = Density.COMFORTABLE) =
        DeterministicA3UICompiler(FixedClock(t), SequentialIdGenerator()).compile(
            ProjectionEngine(FixedClock(t), SequentialIdGenerator(), ProjectionEventLog())
                .readBelief(
                    "ctx",
                    ReadBelief(
                        1,
                        listOf(
                            Fact("calendar.next", "work@08:30", 1.0, "calendar", t, id = "c"),
                            Fact("train.selected", true, 0.95, "train", t, id = "s"),
                            Fact("ticket.owned", true, 0.97, "train", t, id = "o")
                        )
                    ),
                    FormFactorHints("phone", density)
                ).projection
        )

    @Test
    fun P46_token_resolver_uses_injected_context_only() {
        val surface = trainSurface()
        val out = interpreter.interpret(surface, ctx())
        assertEquals(ColorValue(0, 90, 200), out.resolvedTokens.first { it.token == "accent" }.color)
        assertTrue(out.resolvedTokens.any { it.token == "success" })
        val empty = RendererContext("phone", "comfortable", TreeMap(), FixedClock(t))
        assertFails { interpreter.interpret(surface, empty) }
        val resolverSrc = File("src/main/kotlin/a3/renderers/android/core/interp/TokenResolver.kt").readText()
        assertFalse(Regex("#[0-9a-fA-F]{6}").containsMatchIn(resolverSrc))
        assertFalse(Regex("#[0-9a-fA-F]{6}").containsMatchIn(CanonicalJson.of(out)))
    }

    @Test
    fun P47_density_resolver_maps_three_hints() {
        val resolver = DensityResolver()
        assertEquals(0.85, resolver.scale("compact"))
        assertEquals(1.0, resolver.scale("comfortable"))
        assertEquals(1.2, resolver.scale("spacious"))
        assertFails { resolver.scale("dense") }
        assertEquals(0.85, interpreter.interpret(trainSurface(), ctx("compact")).densityScale)
        assertEquals(1.2, interpreter.interpret(trainSurface(), ctx("spacious")).densityScale)
    }

    @Test
    fun P48_motion_interpreter_is_pure_and_deterministic() {
        val surface = trainSurface()
        val a = MotionInterpreter().interpret(surface.motion)
        val b = MotionInterpreter().interpret(surface.motion)
        assertEquals(surface.motion.stiffness, a.stiffness)
        assertEquals(surface.motion.damping, a.damping)
        assertEquals(surface.motion.curve, a.curve)
        assertEquals(surface.motion.durationHint, a.durationHint)
        assertContentEquals(CanonicalJson.bytes(a), CanonicalJson.bytes(b))
        assertTrue(MotionInterpreter::class.java.methods.none { it.name in setOf("animate", "start", "play") })
        val out = interpreter.interpret(surface, ctx())
        assertEquals(a, out.spring)
    }

    @Test
    fun P49_morph_interpreter_keeps_semantic_ids_without_coordinates() {
        val surface = trainSurface()
        val spring = MotionInterpreter().interpret(surface.motion)
        val plan = MorphInterpreter().interpret(surface.morph, spring)
        assertEquals(surface.morph.to, plan.to)
        assertEquals("shared-element", plan.mode)
        assertEquals(surface.morph.shared.sorted(), plan.shared)
        val fields = plan::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it == "x" || it == "y" })
        assertContentEquals(
            CanonicalJson.bytes(plan),
            CanonicalJson.bytes(MorphInterpreter().interpret(surface.morph, spring))
        )
        assertTrue(plan.shared.none { it.contains(",") })
    }

    @Test
    fun P50_gesture_interpreter_is_semantic_names_only() {
        val surface = trainSurface()
        val actions = GestureInterpreter().interpret(surface.gestures)
        assertEquals(surface.gestures.bindings.map { it.gesture }.sorted(), actions.actions.map { it.gesture })
        assertTrue(actions.actions.any { it.gesture == "swipe-left" && it.action == "dismiss" })
        assertTrue(actions.actions.any { it.gesture == "confirm" && it.action == "confirm" })
        val fields = actions.actions.first()::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isPrivate(it.modifiers) }
            .map { it.name }
            .toSet()
        assertEquals(setOf("gesture", "action", "targetNodeId"), fields)
        assertFalse(fields.any { it == "x" || it == "y" })
    }

    @Test
    fun P51_haptic_interpreter_has_no_device_api_in_core() {
        val surface = trainSurface()
        val events = HapticInterpreter().interpret(surface.haptics)
        assertTrue(events.events.any { it.event == "confirm" && it.pattern == "double-tap" })
        assertTrue(events.events.all { it.intensityHint in listOf("light", "medium", "strong") })
        val coreRoot = File("src/main")
        val sources = coreRoot.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            assertFalse(text.contains("Vibra" + "tor"), file.name)
            assertFalse(text.contains("perform" + "HapticFeedback"), file.name)
        }
    }

    @Test
    fun P53_inherited_compiler_path_still_composes() {
        val surface = trainSurface()
        val out = interpreter.interpret(surface, ctx())
        assertEquals(surface.id, out.surfaceId)
        assertEquals(surface.projectionRef, out.projectionRef)
    }

    @Test
    fun P54_form_factor_independence() {
        val surface = trainSurface()
        val before = SurfaceCanonical.bytes(surface)
        val phone = interpreter.interpret(surface, ctx("compact", "phone"))
        val tablet = interpreter.interpret(surface, ctx("spacious", "tablet"))
        assertContentEquals(before, SurfaceCanonical.bytes(surface))
        assertNotEquals(phone.densityScale, tablet.densityScale)
        assertNotEquals(phone.formFactor, tablet.formFactor)
        assertEquals(surface.id, phone.surfaceId)
        assertEquals(surface.id, tablet.surfaceId)
        assertContentEquals(CanonicalJson.bytes(phone.spring), CanonicalJson.bytes(tablet.spring))
    }

    @Test
    fun P56_language_sufficiency_train_booking_is_a_declared_gap() {
        val surface = trainSurface()
        val out = interpreter.interpret(surface, ctx())
        val wire = CanonicalJson.of(out)
        assertTrue(out.resolvedTokens.any { it.token == "accent" })
        assertTrue(out.gestures.actions.any { it.action == "confirm" })
        assertFalse(wire.contains("Milano"))
        assertFalse(wire.contains("binario"))
        assertFalse(wire.contains("passenger"))
        assertFalse(wire.contains("timetable"))
        val interp = File("src/main/kotlin/a3/renderers/android/core/interp").walkTopDown()
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(interp.contains("train.selected"))
        assertFalse(interp.contains("ticket.owned"))
        val hardening = File("../../SCHEMA_HARDENING.md").readText()
        assertTrue(hardening.contains("LANGUAGE GAP"))
        assertTrue(hardening.contains("prenotazione treno"))
        assertTrue(hardening.contains("(b) LANGUAGE GAP"))
        assertEquals("RenderedOutput", out::class.simpleName)
        assertTrue(RenderedOutput::class.java.declaredFields.none { it.name.contains("widget") })
    }

    @Test
    fun T9_stage_is_closed_renderer_owned_set() {
        val empty = RendererContext("phone", "comfortable", TreeMap(), FixedClock(t))
        assertEquals(RendererContext.STAGE_PRONTO, empty.stage)
        assertFails { RendererContext("phone", "comfortable", TreeMap(), FixedClock(t), stage = "crack") }
        for (stage in RendererContext.STAGES) {
            assertEquals(stage, ctx().copy(stage = stage).stage)
        }
        assertEquals(
            listOf("ascolto", "lavoro", "pronto", "approva"),
            RendererContext.STAGES
        )
    }
}
