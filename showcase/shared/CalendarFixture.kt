// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.showcase

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

/**
 * Calendar fixture copied from the launcher / Mac proof (4 nodes, 4 axes).
 * Showcase compiles it itself so launcher stays frozen.
 */
object CalendarFixture {
    val now: Instant = Instant.parse("2026-08-27T08:00:00Z")

    fun output(formFactor: String, reducedMotion: Boolean): RenderedOutput {
        val presentation = presentation()
        val facts = facts()
        val tree = SurfaceComposer.compose(presentation, now, facts)
        val compiler = DeterministicA3UICompiler(now, SequentialIdGenerator())
        val surface = compiler.compile(projection(formFactor), presentation)
            .copy(nodes = tree.nodes, bindings = tree.bindings)
        return A3UIInterpreter().interpret(surface, presentation, context(formFactor, reducedMotion))
    }

    fun hash(): String = PresentationHash.of(SurfaceComposer.compose(presentation(), now, facts()))

    fun walk(node: RenderedNode): List<RenderedNode> {
        val out = ArrayList<RenderedNode>()
        out += node
        for (child in node.children) out += walk(child)
        return out
    }

    fun flatten(output: RenderedOutput): List<RenderedNode> =
        output.nodes.flatMap { walk(it) }

    private fun lineage() = CausalLineage("ctx", 0, "ev_demo")

    private fun context(formFactor: String, reducedMotion: Boolean) = RendererContext(
        formFactor = formFactor,
        density = "comfortable",
        tokens = TreeMap<String, ColorValue>().apply {
            put("accent", ColorValue(0, 90, 200))
            put("success", ColorValue(0, 140, 70))
            put("anticipation_highlight", ColorValue(200, 140, 0))
        },
        clock = FixedClock(now),
        reducedMotion = reducedMotion
    )

    private fun presentation(): PresentationState = PresentationState(
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

    private fun projection(formFactor: String): Projection = Projection(
        id = "proj_cal",
        presentationId = "ps_cal",
        contextRef = "ctx",
        formFactorHints = FormFactorHints(formFactor, Density.COMFORTABLE),
        interactionRequirements = listOf("attend"),
        lineage = lineage(),
        status = ProjectionStatus.PROPOSED
    )

    private fun facts(): Map<String, EpistemicFacts> {
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
