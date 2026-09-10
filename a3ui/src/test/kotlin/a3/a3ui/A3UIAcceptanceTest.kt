package a3.a3ui

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.engine.PrefetchLinker
import a3.a3ui.engine.TemporalBuilder
import a3.a3ui.model.A3UISurface
import a3.a3ui.model.GestureBinding
import a3.a3ui.model.HapticEvent
import a3.a3ui.model.MorphSpec
import a3.a3ui.model.MotionSpec
import a3.a3ui.model.PrefetchSpec
import a3.a3ui.model.PrefetchStatus
import a3.a3ui.schema.ModelValidator
import a3.a3ui.schema.SchemaValidator
import a3.a3ui.serialize.CanonicalJson
import a3.core.time.FixedClock
import a3.core.time.SequentialIdGenerator
import a3.core.world.api.Claim
import a3.core.world.api.ReadBelief
import a3.projection.engine.ProjectionEngine
import a3.projection.engine.ProjectionEventLog
import a3.projection.model.CandidateStatus
import a3.projection.model.CausalLineage
import a3.projection.model.Density
import a3.projection.model.FormFactorHints
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.projection.model.Projection
import a3.projection.model.ProjectionCandidate
import a3.projection.model.ProjectionStatus
import a3.projection.model.RenderedOutput
import a3.projection.serialize.CanonicalJson as ProjectionCanonical
import java.time.Instant
import java.util.ArrayList
import java.util.HashMap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class A3UIAcceptanceTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private fun hints(density: Density = Density.COMFORTABLE) =
        FormFactorHints("phone", density)

    private fun projectionEngine() =
        ProjectionEngine(FixedClock(t), SequentialIdGenerator(), ProjectionEventLog())

    private fun compiler() =
        DeterministicA3UICompiler(t, SequentialIdGenerator())

    private fun samplePresentation(): PresentationState = PresentationState(
        id = "ps_1",
        sourceStateVersion = 3,
        producedAt = t,
        atoms = listOf(PresentationAtom("fact", "ticket.owned", true, 97)),
        lineage = CausalLineage("ctx", 3, "evj_1", "fs_1", "fc_1")
    )

    private fun sampleCandidate(
        status: CandidateStatus = CandidateStatus.PREPARED,
        base: Long = 3,
        expires: Instant? = t.plusSeconds(300)
    ): ProjectionCandidate = ProjectionCandidate(
        id = "pjc_1",
        futureStateId = "fs_1",
        forecastId = "fc_1",
        contextRef = "ctx",
        presentation = samplePresentation(),
        baseStateVersion = base,
        status = status,
        expiresAt = expires,
        priority = 900,
        rank = 1,
        lineage = CausalLineage("ctx", 3, "evj_1", "fs_1", "fc_1")
    )

    @Test
    fun P28_a3ui_consumes_projection_without_mutating_it() {
        val readOut = projectionEngine().readBelief(
            "ctx",
            ReadBelief(4, listOf(Claim("ticket.owned", true, 0.97, "train", t, id = "f2"))),
            hints()
        )
        val before = ProjectionCanonical.bytes(readOut.projection)
        val beforePres = ProjectionCanonical.bytes(readOut.presentation)
        val surface = compiler().compile(readOut.projection)
        assertContentEquals(before, ProjectionCanonical.bytes(readOut.projection))
        assertContentEquals(beforePres, ProjectionCanonical.bytes(readOut.presentation))
        assertEquals(readOut.projection.id, surface.projectionRef)
        assertEquals(readOut.projection.presentationId, surface.presentationRef)
        ModelValidator.surface(surface)
        assertTrue(A3UICompilerMethods.noneNamed("apply"))
    }

    @Test
    fun P29_a3ui_surface_is_deterministic() {
        val readOut = projectionEngine().readBelief(
            "ctx",
            ReadBelief(2, listOf(Claim("calendar.next", "work@08:30", 1.0, "calendar", t))),
            hints()
        )
        val a = compiler().compile(readOut.projection)
        val b = compiler().compile(readOut.projection)
        assertEquals(CanonicalJson.of(a), CanonicalJson.of(b))
        assertContentEquals(CanonicalJson.bytes(a), CanonicalJson.bytes(b))
        assertFalse(CanonicalJson.of(a).contains(" "))
        assertFalse(CanonicalJson.of(a).contains("\n"))
        ModelValidator.surface(a)
    }

    @Test
    fun P30_motion_spec_is_declarative_data() {
        val builder = TemporalBuilder()
        val compact = builder.motion(Density.COMPACT)
        val comfortable = builder.motion(Density.COMFORTABLE)
        val spacious = builder.motion(Density.SPACIOUS)
        assertEquals(400.0, compact.stiffness)
        assertEquals(28.0, compact.damping)
        assertEquals("standard", compact.curve)
        assertEquals(180, compact.durationHint)
        assertEquals(280.0, comfortable.stiffness)
        assertEquals(240, comfortable.durationHint)
        assertEquals(180.0, spacious.stiffness)
        assertTrue(MotionSpec::class.java.methods.none { it.name in setOf("animate", "start", "play") })
        val names = MotionSpec::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isPrivate(it.modifiers) && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        assertEquals(setOf("stiffness", "damping", "curve", "durationHint"), names)
        val surface = compiler().compile(
            projectionEngine().readBelief("ctx", ReadBelief(), hints(Density.SPACIOUS)).projection
        )
        assertEquals(spacious, surface.motion)
    }

    @Test
    fun P31_morph_spec_shared_elements_are_declarative() {
        val readOut = projectionEngine().readBelief(
            "ctx",
            ReadBelief(1, listOf(Claim("ticket.owned", true, 0.97, "train", t))),
            hints()
        )
        val surface = compiler().compile(readOut.projection)
        assertEquals("shared-element", surface.morph.mode)
        assertEquals(readOut.projection.presentationId, surface.morph.to)
        assertTrue(surface.morph.shared.contains("confirm") || surface.morph.shared.contains("attend"))
        val morphFields = MorphSpec::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isPrivate(it.modifiers) && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
        assertFalse(morphFields.any { it == "x" || it == "y" })
        val prefetch = compiler().compilePrefetch(sampleCandidate())
        assertEquals(listOf("ticket.owned"), prefetch.morph.shared)
        assertEquals("ps_1", prefetch.morph.to)
    }

    @Test
    fun P32_gesture_map_is_semantic() {
        val readOut = projectionEngine().readBelief(
            "ctx",
            ReadBelief(1, listOf(Claim("ticket.owned", true, 0.97, "train", t))),
            hints()
        )
        val surface = compiler().compile(readOut.projection)
        assertEquals(
            listOf(
                GestureBinding("confirm", "confirm", "root"),
                GestureBinding("swipe-left", "dismiss", "root")
            ),
            surface.gestures.bindings
        )
        val fields = GestureBinding::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isPrivate(it.modifiers) && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        assertEquals(setOf("gesture", "action", "targetNodeId"), fields)
        assertFalse(fields.any { it == "x" || it == "y" || it.contains("coord", ignoreCase = true) })
    }

    @Test
    fun P33_haptic_map_is_semantic() {
        val readOut = projectionEngine().readBelief(
            "ctx",
            ReadBelief(1, listOf(Claim("ticket.owned", true, 0.97, "train", t))),
            hints()
        )
        val surface = compiler().compile(readOut.projection)
        assertEquals(
            listOf(
                HapticEvent("attend", "tap", "light"),
                HapticEvent("confirm", "double-tap", "medium")
            ),
            surface.haptics.events
        )
        assertTrue(
            surface.haptics.events.all { it.intensityHint in listOf("light", "medium", "strong") }
        )
        assertTrue(HapticEvent::class.java.methods.none { it.name.contains("Vibrator") })
        assertTrue(HapticEvent::class.java.methods.none { it.name.contains("perform") })
    }

    @Test
    fun P34_prefetch_spec_is_projection_domain_and_invalidates() {
        val linker = PrefetchLinker()
        val candidate = sampleCandidate()
        linker.remember(candidate)
        val live = linker.bind(candidate.id, 3L, t)
        assertEquals(candidate.id, live.candidateRef)
        assertEquals(3L, live.baseStateVersion)
        assertEquals(0.9, live.confidence)
        assertEquals(300_000L, live.ttlMs)
        assertEquals(PrefetchStatus.PREPARED, live.status)
        assertEquals(PrefetchStatus.INVALIDATED, linker.bind(candidate.id, 4L, t).status)
        assertFails { linker.bind("not-registered", 3L, t) }
        val expired = linker.describe(sampleCandidate(expires = t), 3L, t)
        assertEquals(PrefetchStatus.EXPIRED, expired.status)
        val invalidated = compiler().compilePrefetch(
            sampleCandidate(status = CandidateStatus.INVALIDATED)
        ).prefetch
        assertNotNull(invalidated)
        assertEquals(PrefetchStatus.INVALIDATED, invalidated.status)
        assertTrue(PrefetchSpec::class.java.declaredFields.none { it.name == "may_commit" })
        assertFalse(CanonicalJson.of(live).contains("may_commit"))
        val illegal =
            """{"id":"s","projection_ref":"p","presentation_ref":"ps","lineage":{"state_identity":"c","source_state_version":0,"causal_event_id":"e"},"density_hint":"compact","color_tokens":["accent"],"motion":{"stiffness":1,"damping":1,"curve":"standard","duration_hint":1},"morph":{"to":"ps","mode":"shared-element","shared":[],"motion":{"stiffness":1,"damping":1,"curve":"standard","duration_hint":1}},"gestures":{"bindings":[]},"haptics":{"events":[]},"nodes":[],"bindings":[],"produced_at":"2026-08-27T08:00:00Z","prefetch":{"candidate_ref":"x","base_state_version":0,"confidence":0,"ttl_ms":0,"status":"prepared","may_commit":true}}"""
        assertFails { SchemaValidator.validateCanonical("a3uisurface.schema.json", illegal) }
        val believed = "World" + "State"
        assertTrue(
            DeterministicA3UICompiler::class.java.methods.none { m ->
                m.parameterTypes.any { it.simpleName == believed }
            }
        )
        val prefetchParam = DeterministicA3UICompiler::class.java.methods
            .first { it.name == "compilePrefetch" }
            .parameterTypes
            .single()
        assertEquals("a3.projection.model.ProjectionCandidate", prefetchParam.name)
    }

    @Test
    fun P35_surface_preserves_causal_lineage() {
        val readOut = projectionEngine().readBelief(
            "home",
            ReadBelief(7, listOf(Claim("calendar.next", "work@08:30", 1.0, "calendar", t))),
            hints()
        )
        val surface = compiler().compile(readOut.projection)
        assertEquals(readOut.projection.lineage, surface.lineage)
        assertEquals(readOut.presentation.lineage, surface.lineage)
        assertEquals(readOut.projection.id, surface.projectionRef)
        assertEquals(readOut.presentation.id, surface.presentationRef)
        assertEquals(7L, surface.lineage.sourceStateVersion)
        assertEquals("home", surface.lineage.stateIdentity)
        val prefetch = compiler().compilePrefetch(sampleCandidate())
        assertEquals(sampleCandidate().lineage, prefetch.lineage)
        assertEquals("fs_1", prefetch.lineage.futureStateId)
        assertEquals("fc_1", prefetch.lineage.forecastId)
        assertEquals("ps_1", prefetch.presentationRef)
    }

    @Test
    fun P36_inherited_read_paths_still_compose() {
        val readOut = projectionEngine().readBelief(
            "ctx",
            ReadBelief(0, listOf(Claim("ticket.owned", true, 1.0, "obs", t, id = "t1"))),
            hints()
        )
        val surface = compiler().compile(readOut.projection)
        assertEquals("ticket.owned", readOut.presentation.atoms.single().k)
        assertEquals(readOut.projection.id, surface.projectionRef)
        ModelValidator.surface(surface)
    }

    @Test
    fun P37_canonical_determinism_a3ui_surface() {
        val hashed = HashMap<String, String>()
        hashed["confirm"] = "confirm"
        hashed["attend"] = "attend"
        val shuffled = ArrayList(hashed.keys)
        val projection = Projection(
            id = "proj_1",
            presentationId = "ps_x",
            contextRef = "ctx",
            formFactorHints = hints(),
            interactionRequirements = shuffled,
            lineage = CausalLineage("ctx", 1, "evj_1"),
            status = ProjectionStatus.PROPOSED
        )
        val a = compiler().compile(projection)
        val b = compiler().compile(projection.copy(interactionRequirements = listOf("attend", "confirm")))
        assertContentEquals(CanonicalJson.bytes(a), CanonicalJson.bytes(b))
        val wire = CanonicalJson.of(a)
        assertTrue(wire.indexOf("\"color_tokens\"") < wire.indexOf("\"density_hint\""))
        assertFalse(wire.contains(" "))
        ModelValidator.surface(a)
    }

    @Test
    fun P38_a3ui_does_not_produce_renderer_artifact() {
        val surface = compiler().compile(
            projectionEngine().readBelief("ctx", ReadBelief(), hints()).projection
        )
        val forbidden = "rendered" + "_output"
        assertTrue(A3UISurface::class.java.declaredFields.none { it.type == RenderedOutput::class.java })
        assertTrue(A3UISurface::class.java.declaredFields.none { it.name == forbidden })
        assertFalse(CanonicalJson.of(surface).contains(forbidden))
        val compiles = DeterministicA3UICompiler::class.java.methods.filter { it.name == "compile" }
        assertTrue(compiles.isNotEmpty())
        assertTrue(compiles.all { it.returnType == A3UISurface::class.java })
        assertTrue(compiles.all { it.returnType != RenderedOutput::class.java })
    }

    @Test
    fun P39_a3ui_does_not_mutate_projection_or_presentation() {
        val readOut = projectionEngine().readBelief(
            "ctx",
            ReadBelief(1, listOf(Claim("calendar.next", "work@08:30", 1.0, "calendar", t))),
            hints()
        )
        val beforeProj = ProjectionCanonical.bytes(readOut.projection)
        val beforePres = ProjectionCanonical.bytes(readOut.presentation)
        compiler().compile(readOut.projection)
        compiler().compilePrefetch(sampleCandidate())
        assertContentEquals(beforeProj, ProjectionCanonical.bytes(readOut.projection))
        assertContentEquals(beforePres, ProjectionCanonical.bytes(readOut.presentation))
        val believed = "Belief" + "State"
        assertTrue(
            DeterministicA3UICompiler::class.java.methods.none { m ->
                m.parameterTypes.any { it.simpleName == believed }
            }
        )
    }

    @Test
    fun P41_surface_has_no_raster_or_hardcoded_fill_fields() {
        val fields = A3UISurface::class.java.declaredFields.map { it.name }
        val forbidden = listOf("width", "height", "font", "min" + "Width" + "Dp", "pix" + "el")
        for (name in forbidden) {
            assertTrue(fields.none { it.contains(name, ignoreCase = true) }, name)
        }
        val json = CanonicalJson.of(
            compiler().compile(projectionEngine().readBelief("ctx", ReadBelief(), hints()).projection)
        )
        assertFalse(json.contains(forbidden[4]))
        assertFalse(Regex("#[0-9a-fA-F]{6}").containsMatchIn(json))
        val schema = javaClass.classLoader.getResource("a3/schemas/a3uisurface.schema.json")!!.readText()
        assertFalse(schema.contains(forbidden[4]))
        assertFalse(Regex("#[0-9a-fA-F]{6}").containsMatchIn(schema))
        assertTrue(json.contains("\"accent\""))
    }
}

private object A3UICompilerMethods {
    fun noneNamed(name: String): Boolean =
        DeterministicA3UICompiler::class.java.methods.none { it.name == name }
}
