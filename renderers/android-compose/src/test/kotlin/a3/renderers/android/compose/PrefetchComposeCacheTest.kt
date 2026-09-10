package a3.renderers.android.compose

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.model.PrefetchStatus
import a3.core.time.FixedClock
import a3.core.time.SequentialIdGenerator
import a3.projection.model.CandidateStatus
import a3.projection.model.CausalLineage
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.projection.model.ProjectionCandidate
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RendererContext
import java.time.Instant
import java.util.TreeMap
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrefetchComposeCacheTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private fun ctx() = RendererContext(
        formFactor = "phone",
        density = "comfortable",
        tokens = TreeMap<String, ColorValue>().apply {
            put("accent", ColorValue(0, 90, 200))
            put("success", ColorValue(0, 140, 70))
            put("anticipation_highlight", ColorValue(200, 140, 0))
        },
        clock = FixedClock(t)
    )

    private fun candidate(
        status: CandidateStatus = CandidateStatus.PREPARED,
        expires: Instant? = t.plusSeconds(300)
    ) = ProjectionCandidate(
        id = "pjc_1",
        futureStateId = "fs_1",
        forecastId = "fc_1",
        contextRef = "ctx",
        presentation = PresentationState(
            id = "ps_1",
            sourceStateVersion = 3,
            producedAt = t,
            atoms = listOf(PresentationAtom("fact", "ticket.owned", true, 97)),
            lineage = CausalLineage("ctx", 3, "evj_1", "fs_1", "fc_1")
        ),
        baseStateVersion = 3,
        status = status,
        expiresAt = expires,
        priority = 900,
        rank = 1,
        lineage = CausalLineage("ctx", 3, "evj_1", "fs_1", "fc_1")
    )

    @Test
    fun P52_prefetch_cache_is_offscreen_and_respects_invalidation() {
        val compiler = DeterministicA3UICompiler(t, SequentialIdGenerator())
        val cache = PrefetchComposeCache()
        val prepared = compiler.compilePrefetch(candidate())
        assertEquals(PrefetchStatus.PREPARED, prepared.prefetch!!.status)
        val hit = cache.composeOffscreen(prepared, ctx(), 3)
        assertNotNull(hit)
        assertEquals(prepared.id, hit.surfaceId)
        assertEquals(hit, cache.get("pjc_1"))
        assertNull(cache.composeOffscreen(prepared, ctx(), 4))
        val dead = compiler.compilePrefetch(candidate(status = CandidateStatus.INVALIDATED))
        val missCache = PrefetchComposeCache()
        assertNull(missCache.composeOffscreen(dead, ctx(), 3))
        assertNull(missCache.get(dead.prefetch!!.candidateRef))
        val expired = compiler.compilePrefetch(candidate(expires = t))
        assertTrue(expired.prefetch!!.ttlMs == 0L || expired.prefetch!!.status == PrefetchStatus.EXPIRED)
        assertNull(PrefetchComposeCache().composeOffscreen(expired, ctx(), 3))
        assertTrue(PrefetchComposeCache::class.java.methods.none { it.name == "apply" })
        assertTrue(PrefetchComposeCache::class.java.methods.none { it.name == "commit" })
    }
}
