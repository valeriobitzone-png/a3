package a3.a3ui.engine

import a3.a3ui.model.A3UISurface
import a3.core.time.InstantSource
import a3.core.time.SequentialIdGenerator
import a3.projection.model.Density
import a3.projection.model.Projection
import a3.projection.model.ProjectionCandidate
import java.util.ArrayList

class DeterministicA3UICompiler(
    private val clock: InstantSource,
    private val ids: SequentialIdGenerator,
    private val temporal: TemporalBuilder = TemporalBuilder(),
    val linker: PrefetchLinker = PrefetchLinker()
) : A3UICompiler {
    override fun compile(projection: Projection): A3UISurface {
        val now = clock.now()
        val motion = temporal.motion(projection.formFactorHints.density)
        return A3UISurface(
            id = ids.next("a3ui"),
            projectionRef = projection.id,
            presentationRef = projection.presentationId,
            lineage = projection.lineage,
            densityHint = projection.formFactorHints.density.wire(),
            colorTokens = temporal.colorTokens(projection.interactionRequirements, prefetch = false),
            motion = motion,
            morph = temporal.morph(
                projection.presentationId,
                projection.interactionRequirements,
                motion
            ),
            gestures = temporal.gestures(projection.interactionRequirements),
            haptics = temporal.haptics(projection.interactionRequirements),
            prefetch = null,
            producedAt = now
        )
    }

    override fun compilePrefetch(candidate: ProjectionCandidate): A3UISurface {
        val now = clock.now()
        linker.remember(candidate)
        val req = temporal.requirementsFrom(candidate.presentation.atoms)
        val motion = temporal.motion(Density.COMFORTABLE)
        val shared = ArrayList<String>(candidate.presentation.atoms.size)
        for (atom in candidate.presentation.atoms) {
            shared += atom.k
        }
        return A3UISurface(
            id = ids.next("a3ui"),
            projectionRef = candidate.contextRef,
            presentationRef = candidate.presentation.id,
            lineage = candidate.lineage,
            densityHint = Density.COMFORTABLE.wire(),
            colorTokens = temporal.colorTokens(req, prefetch = true),
            motion = motion,
            morph = temporal.morph(candidate.presentation.id, shared, motion),
            gestures = temporal.gestures(req),
            haptics = temporal.haptics(req),
            prefetch = linker.describe(candidate, candidate.baseStateVersion, now),
            producedAt = now
        )
    }
}
