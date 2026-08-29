package a3.a3ui.engine

import a3.a3ui.model.PrefetchSpec
import a3.a3ui.model.PrefetchStatus
import a3.projection.model.CandidateStatus
import a3.projection.model.ProjectionCandidate
import java.time.Instant
import java.util.ArrayList
import java.util.TreeMap

/**
 * Binds prefetch specs to projection-domain candidates only.
 * Unknown candidate_ref is refused. Does not write committed reality.
 */
class PrefetchLinker {
    private val known = TreeMap<String, ProjectionCandidate>()

    fun remember(candidate: ProjectionCandidate): ProjectionCandidate {
        known[candidate.id] = candidate
        return candidate
    }

    fun bind(candidateRef: String, currentStateVersion: Long, now: Instant): PrefetchSpec {
        val candidate = known[candidateRef]
            ?: throw IllegalArgumentException(
                "prefetch candidate_ref does not resolve in the projection domain"
            )
        return describe(candidate, currentStateVersion, now)
    }

    fun describe(
        candidate: ProjectionCandidate,
        currentStateVersion: Long,
        now: Instant
    ): PrefetchSpec {
        val expiry = candidate.expiresAt
        val ttl = if (expiry == null) {
            0L
        } else {
            val ms = expiry.toEpochMilli() - now.toEpochMilli()
            if (ms < 0L) 0L else ms
        }
        val raw = candidate.priority / 1000.0
        val confidence = when {
            raw < 0.0 -> 0.0
            raw > 1.0 -> 1.0
            else -> raw
        }
        val status = when {
            candidate.baseStateVersion != currentStateVersion -> PrefetchStatus.INVALIDATED
            candidate.status == CandidateStatus.INVALIDATED -> PrefetchStatus.INVALIDATED
            candidate.status == CandidateStatus.EXPIRED -> PrefetchStatus.EXPIRED
            expiry != null && !now.isBefore(expiry) -> PrefetchStatus.EXPIRED
            else -> PrefetchStatus.PREPARED
        }
        val atomKeys = ArrayList<String>()
        for (atom in candidate.presentation.atoms) {
            atomKeys += atom.k
        }
        atomKeys.sort()
        return PrefetchSpec(
            candidateRef = candidate.id,
            baseStateVersion = candidate.baseStateVersion,
            confidence = confidence,
            ttlMs = ttl,
            status = status,
            atomKeys = atomKeys
        )
    }
}
