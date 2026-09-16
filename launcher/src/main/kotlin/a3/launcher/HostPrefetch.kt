// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.launcher

import a3.a3ui.model.A3UISurface
import a3.renderers.android.compose.PrefetchComposeCache
import a3.renderers.android.core.model.RenderedOutput
import a3.renderers.android.core.model.RendererContext

/**
 * ViewModel-owned invalidation around the frozen prefetch cache.
 * Does not change PrefetchComposeCache.
 */
class HostPrefetch(
    private val cache: PrefetchComposeCache = PrefetchComposeCache()
) {
    private var boundVersion: Long? = null

    fun composeOffscreen(
        surface: A3UISurface,
        ctx: RendererContext,
        currentStateVersion: Long
    ): RenderedOutput? {
        if (boundVersion != null && boundVersion != currentStateVersion) {
            return null
        }
        val hit = cache.composeOffscreen(surface, ctx, currentStateVersion)
        if (hit != null) boundVersion = currentStateVersion
        return hit
    }

    fun get(candidateRef: String, currentStateVersion: Long): RenderedOutput? {
        if (boundVersion != currentStateVersion) return null
        return cache.get(candidateRef)
    }
}
