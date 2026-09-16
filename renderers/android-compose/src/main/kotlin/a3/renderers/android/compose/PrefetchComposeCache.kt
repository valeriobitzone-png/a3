// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.PrefetchStatus
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.RenderedOutput
import a3.renderers.android.core.model.RendererContext
import java.util.TreeMap

/**
 * Off-screen interpretation cache for prepared prefetch surfaces.
 * Does not write A3 state. Invalid/expired specs are refused.
 */
class PrefetchComposeCache(
    private val interpreter: A3UIInterpreter = A3UIInterpreter()
) {
    private val cache = TreeMap<String, RenderedOutput>()

    fun composeOffscreen(
        surface: A3UISurface,
        ctx: RendererContext,
        currentStateVersion: Long
    ): RenderedOutput? {
        val spec = surface.prefetch ?: return null
        if (spec.status != PrefetchStatus.PREPARED) return null
        if (spec.baseStateVersion != currentStateVersion) return null
        if (spec.ttlMs <= 0L) return null
        val rendered = interpreter.interpret(surface, ctx)
        cache[spec.candidateRef] = rendered
        return rendered
    }

    fun get(candidateRef: String): RenderedOutput? = cache[candidateRef]
}
