package a3.renderers.android.core.interp

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Node
import a3.a3ui.model.PrefetchSpec
import a3.projection.model.PresentationState
import a3.renderers.android.core.model.CatalogProfile
import a3.renderers.android.core.model.RenderedNode
import a3.renderers.android.core.model.RenderedOutput
import a3.renderers.android.core.model.RenderedPrefetch
import a3.renderers.android.core.model.RendererContext
import a3.renderers.android.core.model.ResolvedToken
import java.util.ArrayList

/**
 * Pure interpreter: A3UISurface + injected context → renderer-owned output.
 * No device APIs. No writes to A3 state. Does not return a Composable.
 */
class A3UIInterpreter(
    private val tokens: TokenResolver = TokenResolver(),
    private val density: DensityResolver = DensityResolver(),
    private val motion: MotionInterpreter = MotionInterpreter(),
    private val morph: MorphInterpreter = MorphInterpreter(),
    private val gestures: GestureInterpreter = GestureInterpreter(),
    private val haptics: HapticInterpreter = HapticInterpreter()
) {
    fun interpret(surface: A3UISurface, ctx: RendererContext): RenderedOutput {
        val axisPresent = hasNonDefaultAxis(surface.nodes)
        return assemble(
            surface,
            ctx,
            nodes = emptyList(),
            checkGestureTargets = false,
            profile = CatalogProfile.V1,
            degradation = if (axisPresent) "axis-ignored" else null
        )
    }

    fun interpret(
        surface: A3UISurface,
        presentation: PresentationState,
        ctx: RendererContext
    ): RenderedOutput = interpret(surface, presentation, ctx, CatalogProfile.V2)

    fun interpret(
        surface: A3UISurface,
        presentation: PresentationState,
        ctx: RendererContext,
        profile: CatalogProfile
    ): RenderedOutput {
        val nodes = NodeInterpreter.interpret(surface.nodes, surface.bindings, presentation, profile)
        val axisPresent = hasNonDefaultAxis(surface.nodes)
        val degradation = if (profile == CatalogProfile.V1 && axisPresent) "axis-ignored" else null
        return assemble(
            surface,
            ctx,
            nodes = nodes,
            checkGestureTargets = true,
            profile = profile,
            degradation = degradation
        )
    }

    private fun assemble(
        surface: A3UISurface,
        ctx: RendererContext,
        nodes: List<RenderedNode>,
        checkGestureTargets: Boolean,
        profile: CatalogProfile,
        degradation: String?
    ): RenderedOutput {
        val scale = density.scale(ctx.density)
        val spring = motion.interpret(surface.motion)
        val resolved = ArrayList<ResolvedToken>(surface.colorTokens.size)
        val names = ArrayList(surface.colorTokens)
        names.sort()
        for (token in names) {
            resolved += ResolvedToken(token, tokens.resolve(token, ctx))
        }
        val known = if (checkGestureTargets) NodeInterpreter.ids(nodes) else null
        return RenderedOutput(
            surfaceId = surface.id,
            projectionRef = surface.projectionRef,
            presentationRef = surface.presentationRef,
            lineage = surface.lineage,
            densityScale = scale,
            densityHint = surface.densityHint,
            formFactor = ctx.formFactor,
            resolvedTokens = resolved,
            spring = spring,
            sharedElements = morph.interpret(surface.morph, spring),
            gestures = gestures.interpret(surface.gestures, known),
            haptics = haptics.interpret(surface.haptics),
            nodes = nodes,
            prefetch = surface.prefetch?.let { prefetchOf(it) },
            producedAt = ctx.clock.now(),
            catalogProfile = profile,
            degradation = degradation,
            reducedMotion = ctx.reducedMotion
        )
    }

    private fun hasNonDefaultAxis(nodes: List<Node>): Boolean {
        for (node in nodes) {
            val axis = node.axis
            if (axis != null && !axis.isDefault()) return true
            if (hasNonDefaultAxis(node.children)) return true
        }
        return false
    }

    private fun prefetchOf(spec: PrefetchSpec): RenderedPrefetch = RenderedPrefetch(
        candidateRef = spec.candidateRef,
        baseStateVersion = spec.baseStateVersion,
        confidence = spec.confidence,
        ttlMs = spec.ttlMs,
        status = spec.status,
        atomKeys = spec.atomKeys
    )
}
