// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.engine.EpistemicFacts
import a3.a3ui.engine.PresentationHash
import a3.a3ui.engine.SurfaceComposer
import a3.core.time.FixedClock
import a3.core.time.SequentialIdGenerator
import a3.core.world.api.Claim
import a3.projection.model.CausalLineage
import a3.projection.model.Density
import a3.projection.model.FormFactorHints
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.projection.model.Projection
import a3.projection.model.ProjectionStatus
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RenderedNode
import a3.renderers.android.core.model.RenderedOutput
import a3.renderers.android.core.model.RendererContext
import java.time.Instant
import java.util.ArrayList
import java.util.TreeMap

object MacFixtures {
    const val ANDROID_CALENDAR_HASH =
        "4159f43f6537a84eea0ea920983a974d97c4ca6805df3efb9607e89302ee5dde"

    val now: Instant = Instant.parse("2026-08-27T08:00:00Z")
    private val interpreter = A3UIInterpreter()
    private val compiler = DeterministicA3UICompiler(now, SequentialIdGenerator())

    fun lineage() = CausalLineage("ctx", 0, "ev_demo")

    fun rendererContext(reducedMotion: Boolean = false) = RendererContext(
        formFactor = "desktop",
        density = "comfortable",
        tokens = TreeMap<String, ColorValue>().apply {
            put("accent", ColorValue(0, 90, 200))
            put("success", ColorValue(0, 140, 70))
            put("anticipation_highlight", ColorValue(200, 140, 0))
        },
        clock = FixedClock(now),
        reducedMotion = reducedMotion
    )

    fun trainPresentation(): PresentationState = PresentationState(
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

    fun trainProjection(): Projection = Projection(
        id = "proj_train",
        presentationId = "ps_train",
        contextRef = "ctx",
        formFactorHints = FormFactorHints("desktop", Density.COMFORTABLE),
        interactionRequirements = listOf("attend", "confirm"),
        lineage = lineage(),
        status = ProjectionStatus.PROPOSED
    )

    fun calendarPresentation(): PresentationState = PresentationState(
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

    fun calendarProjection(): Projection = Projection(
        id = "proj_cal",
        presentationId = "ps_cal",
        contextRef = "ctx",
        formFactorHints = FormFactorHints("desktop", Density.COMFORTABLE),
        interactionRequirements = listOf("attend"),
        lineage = lineage(),
        status = ProjectionStatus.PROPOSED
    )

    fun calendarFacts(): Map<String, EpistemicFacts> {
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

    fun pricePresentation(): PresentationState = PresentationState(
        id = "ps_price",
        sourceStateVersion = 0,
        producedAt = now,
        atoms = listOf(PresentationAtom("price", "train.price", "12.40", 60)),
        lineage = lineage()
    )

    fun priceProjection(): Projection = Projection(
        id = "proj_price",
        presentationId = "ps_price",
        contextRef = "ctx",
        formFactorHints = FormFactorHints("desktop", Density.COMFORTABLE),
        interactionRequirements = listOf("attend"),
        lineage = lineage(),
        status = ProjectionStatus.PROPOSED
    )

    fun unknownSupportFacts(): Map<String, EpistemicFacts> = mapOf(
        "train.price" to EpistemicFacts(
            claim = Claim("train.price", "12.40", 0.0, "train", now, now.plusSeconds(3600))
        )
    )

    fun lowSupportFacts(): Map<String, EpistemicFacts> = mapOf(
        "train.price" to EpistemicFacts(
            claim = Claim("train.price", "12.40", 0.4, "train", now, now.plusSeconds(3600))
        )
    )

    fun composeCalendar(): Pair<String, RenderedOutput> {
        val presentation = calendarPresentation()
        val facts = calendarFacts()
        val tree = SurfaceComposer.compose(presentation, now, facts)
        val hash = PresentationHash.of(tree)
        val surface = compiler.compile(calendarProjection(), presentation)
            .copy(nodes = tree.nodes, bindings = tree.bindings)
        val output = interpreter.interpret(surface, presentation, rendererContext())
        return hash to output
    }

    fun interpret(
        presentation: PresentationState,
        projection: Projection,
        facts: Map<String, EpistemicFacts>,
        reducedMotion: Boolean = false
    ): Pair<String, RenderedOutput> {
        val tree = SurfaceComposer.compose(presentation, now, facts)
        val hash = PresentationHash.of(tree)
        val surface = compiler.compile(projection, presentation)
            .copy(nodes = tree.nodes, bindings = tree.bindings)
        val output = interpreter.interpret(
            surface,
            presentation,
            rendererContext(reducedMotion)
        )
        return hash to output
    }

    fun walk(node: RenderedNode): List<RenderedNode> {
        val out = ArrayList<RenderedNode>()
        out += node
        for (child in node.children) out += walk(child)
        return out
    }

    fun byId(output: RenderedOutput, id: String): RenderedNode =
        output.nodes.flatMap { walk(it) }.single { it.id == id }
}
