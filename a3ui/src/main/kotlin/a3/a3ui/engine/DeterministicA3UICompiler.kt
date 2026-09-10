package a3.a3ui.engine

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.GestureMap
import a3.a3ui.model.Node
import a3.projection.model.Density
import a3.projection.model.PresentationState
import a3.projection.model.Projection
import a3.projection.model.ProjectionCandidate
import a3.core.time.SequentialIdGenerator
import java.time.Instant
import java.util.ArrayList

class DeterministicA3UICompiler(
    private val producedAt: Instant,
    private val ids: SequentialIdGenerator,
    private val temporal: TemporalBuilder = TemporalBuilder(),
    val linker: PrefetchLinker = PrefetchLinker()
) : A3UICompiler {
    override fun compile(projection: Projection): A3UISurface {
        val motion = temporal.motion(projection.formFactorHints.density)
        val req = projection.interactionRequirements
        val rawGestures = temporal.gestures(req, "root")
        val nodes = if (rawGestures.bindings.isEmpty()) {
            emptyList()
        } else {
            listOf(Node("root", "stack"))
        }
        val gestures = if (rawGestures.bindings.isEmpty()) {
            rawGestures
        } else {
            GestureMap(rawGestures.bindings)
        }
        ensureTargets(nodes, gestures)
        return A3UISurface(
            id = ids.next("a3ui"),
            projectionRef = projection.id,
            presentationRef = projection.presentationId,
            lineage = projection.lineage,
            densityHint = projection.formFactorHints.density.wire(),
            colorTokens = temporal.colorTokens(req, prefetch = false),
            motion = motion,
            morph = temporal.morph(
                projection.presentationId,
                req,
                motion
            ),
            gestures = gestures,
            haptics = temporal.haptics(req),
            nodes = nodes,
            bindings = emptyList(),
            prefetch = null,
            producedAt = producedAt
        )
    }

    override fun compile(projection: Projection, presentation: PresentationState): A3UISurface {
        val motion = temporal.motion(projection.formFactorHints.density)
        val composed = SurfaceComposer.compose(presentation)
        val req = ArrayList(projection.interactionRequirements)
        req.sort()
        val gestures = SurfaceComposer.gestures(req, composed.gestureTargets)
        ensureTargets(composed.nodes, gestures)
        return A3UISurface(
            id = ids.next("a3ui"),
            projectionRef = projection.id,
            presentationRef = presentation.id,
            lineage = projection.lineage,
            densityHint = projection.formFactorHints.density.wire(),
            colorTokens = temporal.colorTokens(req, prefetch = false),
            motion = motion,
            morph = temporal.morph(
                projection.presentationId,
                req,
                motion
            ),
            gestures = gestures,
            haptics = temporal.haptics(req),
            nodes = composed.nodes,
            bindings = composed.bindings,
            prefetch = null,
            producedAt = producedAt
        )
    }

    override fun compilePrefetch(candidate: ProjectionCandidate): A3UISurface {
        linker.remember(candidate)
        val req = temporal.requirementsFrom(candidate.presentation.atoms)
        val motion = temporal.motion(Density.COMFORTABLE)
        val shared = ArrayList<String>(candidate.presentation.atoms.size)
        for (atom in candidate.presentation.atoms) {
            shared += atom.k
        }
        val composed = SurfaceComposer.compose(candidate.presentation)
        val gestures = SurfaceComposer.gestures(req, composed.gestureTargets)
        ensureTargets(composed.nodes, gestures)
        return A3UISurface(
            id = ids.next("a3ui"),
            projectionRef = candidate.contextRef,
            presentationRef = candidate.presentation.id,
            lineage = candidate.lineage,
            densityHint = Density.COMFORTABLE.wire(),
            colorTokens = temporal.colorTokens(req, prefetch = true),
            motion = motion,
            morph = temporal.morph(candidate.presentation.id, shared, motion),
            gestures = gestures,
            haptics = temporal.haptics(req),
            nodes = composed.nodes,
            bindings = composed.bindings,
            prefetch = linker.describe(candidate, candidate.baseStateVersion, producedAt),
            producedAt = producedAt
        )
    }

    private fun ensureTargets(nodes: List<a3.a3ui.model.Node>, gestures: GestureMap) {
        val ids = SurfaceComposer.collectIds(nodes)
        for (binding in gestures.bindings) {
            if (ids.none { it == binding.targetNodeId }) {
                throw IllegalArgumentException("unknown gesture target ${binding.targetNodeId}")
            }
            if (SurfaceComposer.ACTIONS.none { it == binding.action }) {
                throw IllegalArgumentException("unknown gesture action ${binding.action}")
            }
        }
        for (node in flatten(nodes)) {
            if (SurfaceComposer.ROLES.none { it == node.role }) {
                throw IllegalArgumentException("unknown node role ${node.role}")
            }
        }
    }

    private fun flatten(nodes: List<a3.a3ui.model.Node>): List<a3.a3ui.model.Node> {
        val out = ArrayList<a3.a3ui.model.Node>()
        fun walk(node: a3.a3ui.model.Node) {
            out += node
            for (child in node.children) walk(child)
        }
        for (node in nodes) walk(node)
        return out
    }
}
