package a3.renderers.android.core.interp

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.PrefetchSpec
import a3.renderers.android.core.model.RenderedOutput
import a3.renderers.android.core.model.RenderedPrefetch
import a3.renderers.android.core.model.RendererContext
import a3.renderers.android.core.model.ResolvedToken
import java.util.ArrayList

/**
 * Pure interpreter: A3UISurface + injected context → renderer-owned output.
 * No device APIs. No writes to A3 state.
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
        val scale = density.scale(ctx.density)
        val spring = motion.interpret(surface.motion)
        val resolved = ArrayList<ResolvedToken>(surface.colorTokens.size)
        val names = ArrayList(surface.colorTokens)
        names.sort()
        for (token in names) {
            resolved += ResolvedToken(token, tokens.resolve(token, ctx))
        }
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
            gestures = gestures.interpret(surface.gestures),
            haptics = haptics.interpret(surface.haptics),
            prefetch = surface.prefetch?.let { prefetchOf(it) },
            producedAt = ctx.clock.now()
        )
    }

    private fun prefetchOf(spec: PrefetchSpec): RenderedPrefetch = RenderedPrefetch(
        candidateRef = spec.candidateRef,
        baseStateVersion = spec.baseStateVersion,
        confidence = spec.confidence,
        ttlMs = spec.ttlMs,
        status = spec.status
    )
}
