package a3.projection

import a3.core.time.FixedClock
import a3.core.time.SequentialIdGenerator
import a3.core.world.api.BeliefReader
import a3.core.world.api.Fact
import a3.core.world.api.ReadBelief
import a3.prediction.engine.PredictionEngine
import a3.prediction.engine.PredictionEventLog
import a3.prediction.engine.PredictionRequest
import a3.prediction.engine.PreparedStateStore
import a3.prediction.model.CapabilityHint
import a3.prediction.model.FutureState
import a3.prediction.model.PredictionPolicy
import a3.projection.engine.PresentationRenderer
import a3.projection.engine.ProjectionEngine
import a3.projection.engine.ProjectionEventLog
import a3.projection.engine.ProjectionReplay
import a3.projection.model.CandidateStatus
import a3.projection.model.Density
import a3.projection.model.FormFactorHints
import a3.projection.model.PresentationState
import a3.projection.model.Projection
import a3.projection.model.ProjectionCandidate
import a3.projection.model.RenderedOutput
import a3.projection.schema.ModelValidator
import a3.projection.schema.SchemaValidator
import a3.projection.serialize.CanonicalJson
import java.time.Instant
import java.util.ArrayList
import java.util.HashMap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P12–P24, P26, P27. Real engine path. No stubs of ProjectionEngine.
 * P11bis / P25 live in architecture and barrier tests.
 */
class ProjectionAcceptanceTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private fun hints(density: Density = Density.COMFORTABLE) =
        FormFactorHints("phone", density)

    private fun engine(events: ProjectionEventLog = ProjectionEventLog()) =
        ProjectionEngine(FixedClock(t), SequentialIdGenerator(), events)

    private fun readConstruct(id: String, score: Double, k: String = "x.done"): FutureState = FutureState(id, "ctx", "cap", listOf(Fact(k, true, 1.0, "pred", t)), score, t)

    private fun loopHints() = listOf(
        CapabilityHint(
            "calendar.read", emptyList(),
            listOf(Fact("calendar.next", "work@08:30", 1.0, "calendar", t, t.plusSeconds(3600))),
            reliability = 1.0
        ),
        CapabilityHint(
            "train.search",
            listOf(Fact("calendar.next", "work@08:30", 0.5, "calendar", t)),
            listOf(Fact("train.selected", true, 0.95, "train", t)),
            reliability = 1.0
        ),
        CapabilityHint(
            "train.commit",
            listOf(Fact("train.selected", true, 0.5, "train", t)),
            listOf(Fact("ticket.owned", true, 0.97, "train", t)),
            reliability = 1.0
        )
    )

    private fun predictionEngine() = PredictionEngine(
        FixedClock(t),
        SequentialIdGenerator(),
        PredictionEventLog(),
        PredictionPolicy("pol"),
        PreparedStateStore(FixedClock(t))
    )

    private class MockRendererA : PresentationRenderer {
        override fun render(state: PresentationState): RenderedOutput =
            RenderedOutput("mock-a", "A|" + state.id + "|" + state.atoms.size)
    }

    private class MockRendererB : PresentationRenderer {
        override fun render(state: PresentationState): RenderedOutput =
            RenderedOutput("mock-b", "B|" + state.sourceStateVersion + "|" + state.lineage.causalEventId)
    }

    @Test
    fun P12_projection_reads_belief_without_mutating_it() {
        val liveFacts = arrayListOf(
            Fact("calendar.next", "work@08:30", 1.0, "calendar", t, id = "f1"),
            Fact("ticket.owned", true, 0.97, "train", t, id = "f2")
        )
        val guarded: BeliefReader = object : BeliefReader {
            override val version: Long = 4L
            override fun validAt(fact: Fact, t: Instant) = fact.validAt(t)
            override fun current(t: Instant): List<Fact> = liveFacts
        }
        val beforeVersion = guarded.version
        val beforeWire = liveFacts.map { listOf(it.id, it.k, it.v, it.confidence, it.source) }

        val out = engine().readBelief("ctx", guarded, hints())

        assertEquals(beforeVersion, guarded.version)
        assertEquals(beforeWire, liveFacts.map { listOf(it.id, it.k, it.v, it.confidence, it.source) })
        assertEquals(4L, out.presentation.sourceStateVersion)
        assertEquals(4L, out.projection.lineage.sourceStateVersion)
        assertEquals("ctx", out.projection.lineage.stateIdentity)
        assertEquals(
            listOf("calendar.next", "ticket.owned"),
            out.presentation.atoms.map { it.k }
        )
        assertEquals(100, out.presentation.atoms[0].priority)
        assertEquals(97, out.presentation.atoms[1].priority)
        ModelValidator.presentation(out.presentation)
        ModelValidator.projection(out.projection)
        assertTrue(ProjectionEngine::class.java.methods.none { it.name == "apply" })
        assertTrue(ProjectionEngine::class.java.methods.none { it.name == "commit" })
        assertTrue(ProjectionEngine::class.java.methods.none { it.name == "write" })
    }

    @Test
    fun P13_presentation_state_is_deterministic() {
        val reader = ReadBelief(
            version = 2,
            facts = listOf(
                Fact("b.key", "two", 0.4, "s", t, id = "b"),
                Fact("a.key", "one", 0.9, "s", t, id = "a")
            )
        )
        val first = engine().readBelief("ctx", reader, hints()).presentation
        val second = engine().readBelief("ctx", reader, hints()).presentation
        val fa = CanonicalJson.of(first)
        val fb = CanonicalJson.of(second)
        assertEquals(fa, fb)
        assertContentEquals(fa.toByteArray(Charsets.UTF_8), fb.toByteArray(Charsets.UTF_8))
        assertFalse(fa.contains(" "))
        assertFalse(fa.contains("\n"))
        assertTrue(fa.indexOf("\"atoms\"") < fa.indexOf("\"id\""))
        assertTrue(fa.indexOf("\"produced_at\"") < fa.lastIndexOf("\"source_state_version\""))
        assertFalse(fa.contains("state_ref"))
        ModelValidator.presentation(first)
        val illegal =
            """{"id":"ps_1","source_state_version":0,"produced_at":"2026-08-27T08:00:00Z","atoms":[],"lineage":{"state_identity":"c","source_state_version":0,"causal_event_id":"e"},"state_ref":"x"}"""
        assertFails { SchemaValidator.validateCanonical("presentationstate.schema.json", illegal) }
    }

    @Test
    fun P14_presentation_state_is_device_agnostic() {
        val reader = ReadBelief(
            1,
            listOf(Fact("calendar.next", "work@08:30", 1.0, "calendar", t, id = "f1"))
        )
        val compact = engine().readBelief("ctx", reader, hints(Density.COMPACT))
        val spacious = engine().readBelief("ctx", reader, hints(Density.SPACIOUS))
        assertContentEquals(
            CanonicalJson.bytes(compact.presentation),
            CanonicalJson.bytes(spacious.presentation)
        )
        assertNotEquals(
            CanonicalJson.of(compact.projection),
            CanonicalJson.of(spacious.projection)
        )
        val names = PresentationState::class.java.declaredFields.map { it.name }
        assertFalse(names.any { it.contains("width", ignoreCase = true) })
        assertFalse(names.any { it.contains("height", ignoreCase = true) })
        assertFalse(names.any { it.contains("color", ignoreCase = true) })
        assertFalse(names.any { it.contains("font", ignoreCase = true) })
        assertFalse(names.any { it.contains("layout", ignoreCase = true) })
        val bannedLayout = "min" + "Width" + "Dp"
        assertFalse(names.contains(bannedLayout))
        val bannedRaster = "pix" + "el"
        assertFalse(names.any { it.contains(bannedRaster, ignoreCase = true) })
        val json = CanonicalJson.of(compact.presentation)
        assertFalse(json.contains(bannedLayout))
        assertFalse(json.contains(bannedRaster))
        val hintFields = FormFactorHints::class.java.declaredFields
            .filter {
                val mods = it.modifiers
                java.lang.reflect.Modifier.isPrivate(mods) && !java.lang.reflect.Modifier.isStatic(mods)
            }
            .map { it.name }
            .toSet()
        assertEquals(setOf("formFactor", "density"), hintFields)
    }

    @Test
    fun P15_candidate_from_possible_reality_without_commit() {
        val pe = predictionEngine()
        val pred = pe.predict(PredictionRequest("ctx", ReadBelief(), loopHints()))
        val readSource = pred.futureStates.first()
        val beforeFacts = readSource.facts.toList()
        val beforeScore = readSource.score
        val preparedId = pred.prepared?.id
        assertNotNull(preparedId)
        assertNotNull(pe.store.get(preparedId))

        val cand = engine().readFutureState(
            readSource, 0L, "ctx", pred.forecast.id, hints()
        )
        assertEquals(beforeFacts, readSource.facts)
        assertEquals(beforeScore, readSource.score)
        assertEquals(CandidateStatus.PREPARED, cand.status)
        assertEquals(readSource.id, cand.futureStateId)
        assertEquals(pred.forecast.id, cand.forecastId)
        assertEquals(0L, cand.baseStateVersion)
        assertNotNull(pe.store.get(preparedId))
        assertTrue(ProjectionEngine::class.java.declaredMethods.none { it.name == "apply" })
        ModelValidator.candidate(cand)
        ModelValidator.presentation(cand.presentation)
    }

    @Test
    fun P16_priority_ranking() {
        val reader = ReadBelief(
            1,
            listOf(
                Fact("low.k", "L", 0.21, "s", t, id = "l"),
                Fact("high.k", "H", 0.94, "s", t, id = "h"),
                Fact("mid.k", "M", 0.50, "s", t, id = "m")
            )
        )
        val atoms = engine().readBelief("ctx", reader, hints()).presentation.atoms
        assertEquals(listOf("high.k", "mid.k", "low.k"), atoms.map { it.k })
        assertEquals(listOf(94, 50, 21), atoms.map { it.priority })
        assertEquals(atoms, atoms.sortedWith(ProjectionEngine.ATOM_ORDER))

        val readSources = listOf(
            readConstruct("fs_z", 0.50, "z.done"),
            readConstruct("fs_a", 0.50, "a.done"),
            readConstruct("fs_b", 0.90, "b.done")
        )
        val ranked = engine().readFutureStates(readSources, 1L, "ctx", "fc_1", hints())
        assertEquals(listOf("fs_b", "fs_a", "fs_z"), ranked.map { it.futureStateId })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
        assertEquals(900, ranked[0].priority)
        assertEquals(500, ranked[1].priority)
        assertEquals(ranked, ranked.sortedWith(ProjectionEngine.CANDIDATE_ORDER))
    }

    @Test
    fun P17_form_factor_hints_use_semantic_density_only() {
        val values = Density.entries.map { it.wire() }
        assertEquals(listOf("compact", "comfortable", "spacious"), values)
        val json = CanonicalJson.of(FormFactorHints("tablet", Density.SPACIOUS))
        assertTrue(json.contains("\"density\":\"spacious\""))
        assertTrue(json.contains("\"form_factor\":\"tablet\""))
        assertFalse(json.contains("min" + "Width" + "Dp"))
        assertFails { FormFactorHints("watch", Density.COMPACT) }
        val proj = engine().readBelief("ctx", ReadBelief(), hints(Density.COMPACT)).projection
        assertEquals(Density.COMPACT, proj.formFactorHints.density)
        ModelValidator.projection(proj)
    }

    @Test
    fun P18_projection_event_log_is_replayable() {
        val events = ProjectionEventLog()
        val eng = engine(events)
        val reader = ReadBelief(
            3,
            listOf(Fact("ticket.owned", true, 0.97, "train", t, id = "f2"))
        )
        val original = eng.readBelief("ctx", reader, hints())
        val readSources = listOf(
            readConstruct("fs_hi", 0.8),
            readConstruct("fs_lo", 0.1)
        )
        val originalCands = eng.readFutureStates(readSources, 3L, "ctx", "fc_1", hints())
        assertEquals(events.all(), events.replay())
        val replayed = ProjectionReplay.replay(events)
        assertEquals(1, replayed.projections.size)
        assertContentEquals(
            CanonicalJson.bytes(original.projection),
            CanonicalJson.bytes(replayed.projections.single())
        )
        assertEquals(originalCands.map { it.id }, replayed.candidates.map { it.id })
        assertContentEquals(
            CanonicalJson.bytes(originalCands),
            CanonicalJson.bytes(replayed.candidates)
        )
        assertTrue(events.all().any { it.type == "projection.updated" })
        assertTrue(events.all().any { it.type == "projection.candidate.updated" })
    }

    @Test
    fun P19_inherited_read_paths_still_compose() {
        val pred = predictionEngine().predict(PredictionRequest("ctx", ReadBelief(), loopHints()))
        assertTrue(pred.futureStates.isNotEmpty())
        val plannedLike = engine().readBelief(
            "ctx",
            ReadBelief(
                0,
                listOf(Fact("ticket.owned", true, 1.0, "obs", t, id = "t1"))
            ),
            hints()
        )
        assertEquals("ticket.owned", plannedLike.presentation.atoms.single().k)
        val composed = engine().readFutureStates(
            pred.futureStates, 0L, "ctx", pred.forecast.id, hints()
        )
        assertEquals(pred.futureStates.size, composed.size)
        assertEquals(pred.futureStates.map { it.id }.toSet(), composed.map { it.futureStateId }.toSet())
    }

    @Test
    fun P20_canonical_determinism_presentation_and_candidate() {
        val hashed = HashMap<String, Fact>()
        hashed["z"] = Fact("z.key", "Z", 0.3, "s", t, id = "z")
        hashed["a"] = Fact("a.key", "A", 0.8, "s", t, id = "a")
        hashed["m"] = Fact("m.key", "M", 0.8, "s", t, id = "m")
        val shuffled = ArrayList(hashed.values)
        val reader: BeliefReader = object : BeliefReader {
            override val version: Long = 5L
            override fun validAt(fact: Fact, t: Instant) = fact.validAt(t)
            override fun current(t: Instant): List<Fact> = shuffled
        }
        val a = engine().readBelief("ctx", reader, hints()).presentation
        val b = engine().readBelief("ctx", reader, hints()).presentation
        assertContentEquals(CanonicalJson.bytes(a), CanonicalJson.bytes(b))
        assertEquals(listOf("a.key", "m.key", "z.key"), a.atoms.map { it.k })

        val readSources = listOf(
            readConstruct("fs_m", 0.4),
            readConstruct("fs_a", 0.9)
        )
        val ca = engine().readFutureStates(readSources, 5L, "ctx", "fc_x", hints())
        val cb = engine().readFutureStates(readSources, 5L, "ctx", "fc_x", hints())
        assertContentEquals(CanonicalJson.bytes(ca), CanonicalJson.bytes(cb))
        val wire = CanonicalJson.of(ca.first())
        assertFalse(wire.contains(" "))
        assertFalse(wire.contains("\n"))
        assertTrue(wire.indexOf("\"base_state_version\"") < wire.indexOf("\"context_ref\""))
        ModelValidator.candidate(ca.first())
    }

    @Test
    fun P21_renderer_independence() {
        val presentation = engine().readBelief(
            "ctx",
            ReadBelief(1, listOf(Fact("calendar.next", "work@08:30", 1.0, "calendar", t))),
            hints()
        ).presentation
        val before = CanonicalJson.bytes(presentation)
        val outA = MockRendererA().render(presentation)
        val outB = MockRendererB().render(presentation)
        assertNotEquals(outA, outB)
        assertNotEquals(outA.body, outB.body)
        assertNotEquals(outA.kind, outB.kind)
        assertContentEquals(before, CanonicalJson.bytes(presentation))
        assertEquals("PresentationState", presentation::class.simpleName)
        assertEquals("RenderedOutput", outA::class.simpleName)
        assertTrue(presentation::class.java != outA::class.java)
    }

    @Test
    fun P22_projection_does_not_carry_renderer_artifact() {
        val out = engine().readBelief("ctx", ReadBelief(), hints())
        val forbidden = "rendered" + "_output"
        assertTrue(Projection::class.java.declaredFields.none { it.name == forbidden })
        assertTrue(Projection::class.java.declaredFields.none { it.type == RenderedOutput::class.java })
        assertFalse(CanonicalJson.of(out.projection).contains(forbidden))
        ModelValidator.projection(out.projection)
        val illegal =
            """{"id":"p","presentation_id":"ps","context_ref":"c","form_factor_hints":{"form_factor":"phone","density":"compact"},"lineage":{"state_identity":"c","source_state_version":0,"causal_event_id":"e"},"status":"proposed","$forbidden":"x"}"""
        assertFails { SchemaValidator.validateCanonical("projection-core.schema.json", illegal) }
    }

    @Test
    fun P23_candidate_auto_invalidated_when_base_version_differs() {
        val events = ProjectionEventLog()
        val eng = engine(events)
        val prepared = eng.readFutureStates(
            listOf(readConstruct("fs_1", 0.7)), 4L, "ctx", "fc_1", hints()
        ).single()
        assertEquals(CandidateStatus.PREPARED, prepared.status)
        assertEquals(prepared, eng.liveCandidate(prepared, 4L, t))
        assertNull(eng.liveCandidate(prepared, 5L, t))
        assertEquals(CandidateStatus.PREPARED, prepared.status)
        val replayed = ProjectionReplay.replay(events).candidates.single()
        assertEquals(CandidateStatus.INVALIDATED, replayed.status)
        assertEquals(prepared.id, replayed.id)
        assertTrue(events.all().any { it.type == "projection.invalidated" })
        assertNull(eng.liveCandidate(prepared, 4L, t.plusSeconds(300)))
        assertEquals(CandidateStatus.PREPARED, prepared.status)
    }

    @Test
    fun P24_causal_lineage_is_complete() {
        val believed = engine().readBelief(
            "home",
            ReadBelief(7, listOf(Fact("calendar.next", "work@08:30", 1.0, "calendar", t))),
            hints()
        )
        val lin = believed.projection.lineage
        assertEquals("home", lin.stateIdentity)
        assertEquals(7L, lin.sourceStateVersion)
        assertTrue(lin.causalEventId.isNotBlank())
        assertEquals(lin, believed.presentation.lineage)
        assertNull(lin.futureStateId)
        assertNull(lin.forecastId)

        val readSource = readConstruct("fs_line", 0.6)
        val cand = engine().readFutureState(readSource, 7L, "home", "fc_line", hints())
        val cl = cand.lineage
        assertEquals("home", cl.stateIdentity)
        assertEquals(7L, cl.sourceStateVersion)
        assertTrue(cl.causalEventId.isNotBlank())
        assertEquals("fs_line", cl.futureStateId)
        assertEquals("fc_line", cl.forecastId)
        assertEquals(readSource.id, cand.futureStateId)
        assertEquals(cand.presentation.lineage.futureStateId, cl.futureStateId)
        ModelValidator.candidate(cand)
    }

    @Test
    fun P26_presentation_state_has_no_raster_or_typography_fields() {
        val fields = PresentationState::class.java.declaredFields.map { it.name }
        val forbidden = listOf("width", "height", "color", "font", "min" + "Width" + "Dp", "pix" + "el")
        for (name in forbidden) {
            assertTrue(fields.none { it.contains(name, ignoreCase = true) }, name)
        }
        val json = CanonicalJson.of(engine().readBelief("ctx", ReadBelief(), hints()).presentation)
        for (name in forbidden) {
            assertFalse(json.contains(name), name)
        }
        val schema = javaClass.classLoader.getResource("a3/schemas/presentationstate.schema.json")!!
            .readText()
        assertFalse(schema.contains("min" + "Width" + "Dp"))
        assertFalse(schema.contains("state_ref"))
    }

    @Test
    fun P27_projection_type_rejects_renderer_payload() {
        val forbidden = "rendered" + "_output"
        assertTrue(Projection::class.java.declaredFields.none { it.name.contains("rendered", ignoreCase = true) })
        assertTrue(ProjectionCandidate::class.java.declaredFields.none { it.type == RenderedOutput::class.java })
        val schema = javaClass.classLoader.getResource("a3/schemas/projection-core.schema.json")!!
            .readText()
        assertFalse(schema.contains(forbidden))
        val out = engine().readBelief("ctx", ReadBelief(), hints()).projection
        assertFalse(CanonicalJson.of(out).contains(forbidden))
    }
}
