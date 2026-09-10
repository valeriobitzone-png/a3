package a3.launcher

import a3.a3ui.engine.EpistemicFacts
import a3.core.model.Capability
import a3.core.model.CapabilityGraph
import a3.core.model.Claim
import a3.core.model.Goal
import a3.core.model.Observation
import a3.core.runtime.Executor
import a3.core.time.FixedClock
import a3.core.time.InstantSource
import a3.intent.IntentContext
import a3.projection.model.CausalLineage
import a3.projection.model.Density
import a3.projection.model.FormFactorHints
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.projection.model.Projection
import a3.projection.model.ProjectionCandidate
import a3.projection.model.CandidateStatus
import a3.projection.model.ProjectionStatus
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RendererContext
import java.time.Instant
import java.util.TreeMap

object DemoFixtures {
    val now: Instant = Instant.parse("2026-08-27T08:00:00Z")
    val clock: InstantSource = FixedClock(now)

    fun intentContext(): IntentContext = IntentContext(
        now = now,
        id = "i_train",
        expression = "book the train",
        modality = "text",
        goalRef = "g_train"
    )

    fun rendererContext(
        stage: String = RendererContext.STAGE_PRONTO
    ): RendererContext = RendererContext(
        formFactor = "phone",
        density = "comfortable",
        tokens = TreeMap<String, ColorValue>().apply {
            put("accent", ColorValue(0, 90, 200))
            put("success", ColorValue(0, 140, 70))
            put("anticipation_highlight", ColorValue(200, 140, 0))
        },
        clock = clock,
        stage = stage
    )

    fun lineage(version: Long = 0): CausalLineage =
        CausalLineage("ctx", version, "ev_demo")

    fun presentation(): PresentationState = PresentationState(
        id = "ps_train",
        sourceStateVersion = 0,
        producedAt = now,
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

    fun projection(density: Density = Density.COMFORTABLE): Projection = Projection(
        id = "proj_train",
        presentationId = "ps_train",
        contextRef = "ctx",
        formFactorHints = FormFactorHints("phone", density),
        interactionRequirements = listOf("attend", "confirm"),
        lineage = lineage(),
        status = ProjectionStatus.PROPOSED
    )

    fun candidate(baseVersion: Long): ProjectionCandidate = ProjectionCandidate(
        id = "pjc_train",
        futureStateId = "fs_demo",
        forecastId = "fc_demo",
        contextRef = "ctx",
        presentation = presentation(),
        baseStateVersion = baseVersion,
        status = CandidateStatus.PREPARED,
        expiresAt = now.plusSeconds(300),
        priority = 900,
        rank = 1,
        lineage = lineage(baseVersion)
    )

    fun trainGraph(): CapabilityGraph = CapabilityGraph(
        mapOf(
            "calendar.read" to Capability(
                "calendar.read", "calendar.read", emptyList(),
                effects = listOf(
                    Claim("calendar.next", "work@08:30", 1.0, "calendar", now, now.plusSeconds(3600))
                )
            ),
            "train.search" to Capability(
                "train.search", "train.search",
                preconditions = listOf(Claim("calendar.next", "work@08:30", 0.5, "calendar", now)),
                effects = listOf(Claim("train.selected", true, 0.95, "train", now))
            ),
            "train.reserve" to Capability(
                "train.reserve", "train.reserve",
                preconditions = listOf(Claim("train.selected", true, 0.5, "train", now)),
                effects = listOf(Claim("ticket.owned", true, 0.97, "train", now)),
                reversible = false
            )
        )
    )

    fun reversibleGraph(): CapabilityGraph = CapabilityGraph(
        mapOf(
            "calendar.read" to Capability(
                "calendar.read", "calendar.read", emptyList(),
                effects = listOf(
                    Claim("calendar.next", "work@08:30", 1.0, "calendar", now, now.plusSeconds(3600))
                )
            )
        )
    )

    fun trainGoal(intentId: String): Goal = Goal(
        "g_train",
        intentId,
        listOf(Claim("ticket.owned", true, 0.5, "goal", now))
    )

    fun reversibleGoal(intentId: String): Goal = Goal(
        "g_cal",
        intentId,
        listOf(Claim("calendar.next", "work@08:30", 0.5, "goal", now))
    )

    fun matchingExecutor(): Executor = Executor { cap, t ->
        Observation("o_${cap.id}", "e", t, cap.effects.map { it.copy(id = "") })
    }

    fun mismatchExecutor(): Executor = Executor { cap, t ->
        Observation(
            "o_${cap.id}",
            "e",
            t,
            cap.effects.map { it.copy(id = "", v = false) }
        )
    }

    fun priceFacts(
        confidence: Double = 0.6,
        expiresAt: Instant? = now.plusSeconds(3600),
        admissionHeld: Boolean = false,
        actionPhase: String? = null
    ): Map<String, EpistemicFacts> = mapOf(
        "train.price" to EpistemicFacts(
            claim = Claim("train.price", "12.40", confidence, "train", now, expiresAt),
            admissionHeld = admissionHeld,
            actionPhase = actionPhase
        )
    )

    fun uncertainFacts(): Map<String, EpistemicFacts> = mapOf(
        "train.price" to EpistemicFacts(
            claim = Claim("train.price", "12.40", 0.4, "train", now, now.minusSeconds(1)),
            admissionHeld = true,
            actionPhase = "unknown"
        )
    )

    fun uncertainCalendar(): PresentationState = PresentationState(
        id = "ps_cal",
        sourceStateVersion = 0,
        producedAt = now,
        atoms = listOf(
            PresentationAtom("price", "cal.meeting", "Riunione 09:00 — Ada", 50),
            PresentationAtom("price", "cal.flight", "Volo 14:30 — FCO→LIN", 50),
            PresentationAtom("price", "cal.hotel", "Hotel Milano", 50),
            PresentationAtom("price", "cal.dinner", "Prenotazione ristorante", 50)
        ),
        lineage = lineage()
    )

    fun uncertainCalendarProjection(): Projection = Projection(
        id = "proj_cal",
        presentationId = "ps_cal",
        contextRef = "ctx",
        formFactorHints = FormFactorHints("phone", Density.COMFORTABLE),
        interactionRequirements = listOf("attend"),
        lineage = lineage(),
        status = ProjectionStatus.PROPOSED
    )

    fun uncertainCalendarFacts(): Map<String, EpistemicFacts> {
        val agingObserved = now.minusSeconds(3600)
        val agingExpires = now.plusSeconds(600)
        return mapOf(
            "cal.meeting" to EpistemicFacts(
                claim = Claim(
                    "cal.meeting",
                    "Riunione 09:00 — Ada",
                    1.0,
                    "calendar",
                    now,
                    now.plusSeconds(86400)
                ),
                actionPhase = "completed"
            ),
            "cal.flight" to EpistemicFacts(
                claim = Claim(
                    "cal.flight",
                    "Volo 14:30 — FCO→LIN",
                    0.7,
                    "calendar",
                    agingObserved,
                    agingExpires
                ),
                actionPhase = "created"
            ),
            "cal.hotel" to EpistemicFacts(
                claim = Claim(
                    "cal.hotel",
                    "Hotel Milano",
                    0.4,
                    "calendar",
                    now,
                    now.minusSeconds(1)
                ),
                admissionHeld = true,
                actionPhase = "unknown"
            ),
            "cal.dinner" to EpistemicFacts(
                claim = Claim(
                    "cal.dinner",
                    "Prenotazione ristorante",
                    0.9,
                    "calendar",
                    now,
                    now.plusSeconds(86400)
                ),
                revisionContradicted = true,
                compensationPhase = "compensated"
            )
        )
    }
}
