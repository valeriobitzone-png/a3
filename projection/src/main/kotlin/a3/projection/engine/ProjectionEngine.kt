package a3.projection.engine

import a3.core.time.InstantSource
import a3.core.time.SequentialIdGenerator
import a3.core.world.api.BeliefReader
import a3.core.world.api.FACT_ORDER
import a3.core.world.api.Claim
import a3.prediction.model.FutureState
import a3.projection.model.CandidateStatus
import a3.projection.model.CausalLineage
import a3.projection.model.FormFactorHints
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.projection.model.Projection
import a3.projection.model.ProjectionCandidate
import a3.projection.model.ProjectionStatus
import java.time.Instant
import java.util.ArrayList
import java.util.TreeSet
import kotlin.math.floor

data class ReadProjection(
    val projection: Projection,
    val presentation: PresentationState
)

/**
 * Read-only transformer: believed/possible reality → presentation intent.
 * Does not apply to committed world. Does not emit renderer-owned artifacts.
 */
class ProjectionEngine(
    private val clock: InstantSource,
    private val ids: SequentialIdGenerator,
    val events: ProjectionEventLog
) {
    fun readBelief(
        contextRef: String,
        reader: BeliefReader,
        hints: FormFactorHints,
        now: Instant = clock.now()
    ): ReadProjection {
        val version = reader.version
        val facts = reader.current(now)
        val causalId = ids.next("evj")
        val presentation = presentationOf(
            version = version,
            facts = facts,
            now = now,
            stateIdentity = contextRef,
            causalEventId = causalId,
            futureStateId = null,
            forecastId = null
        )
        val projection = Projection(
            id = ids.next("proj"),
            presentationId = presentation.id,
            contextRef = contextRef,
            formFactorHints = hints,
            interactionRequirements = requirements(presentation.atoms),
            lineage = presentation.lineage,
            status = ProjectionStatus.PROPOSED
        )
        events.append(
            ProjectionEvent(
                id = causalId,
                t = now,
                type = "projection.updated",
                causalId = projection.id,
                payload = projection
            )
        )
        events.append(
            ProjectionEvent(
                id = ids.next("evj"),
                t = now,
                type = "projection.updated",
                causalId = presentation.id,
                payload = presentation
            )
        )
        return ReadProjection(projection, presentation)
    }

    fun lastPresentation(): PresentationState? {
        for (event in events.all().asReversed()) {
            val payload = event.payload
            if (payload is PresentationState) return payload
        }
        return null
    }

    fun readFutureState(
        readSource: FutureState,
        currentVersion: Long,
        contextRef: String,
        forecastId: String,
        hints: FormFactorHints,
        ttlSeconds: Long = 300,
        now: Instant = clock.now()
    ): ProjectionCandidate {
        require(hints.formFactor in FormFactorHints.FORM_FACTORS)
        val causalId = ids.next("evj")
        val presentation = presentationOf(
            version = currentVersion,
            facts = readSource.facts,
            now = now,
            stateIdentity = contextRef,
            causalEventId = causalId,
            futureStateId = readSource.id,
            forecastId = forecastId
        )
        val priority = floor(readSource.score * 1000.0).toInt()
        return ProjectionCandidate(
            id = ids.next("pjc"),
            futureStateId = readSource.id,
            forecastId = forecastId,
            contextRef = contextRef,
            presentation = presentation,
            baseStateVersion = currentVersion,
            status = CandidateStatus.PREPARED,
            expiresAt = now.plusSeconds(ttlSeconds),
            priority = priority,
            rank = 1,
            lineage = CausalLineage(
                stateIdentity = contextRef,
                sourceStateVersion = currentVersion,
                causalEventId = causalId,
                futureStateId = readSource.id,
                forecastId = forecastId
            )
        )
    }

    fun readFutureStates(
        readSources: List<FutureState>,
        currentVersion: Long,
        contextRef: String,
        forecastId: String,
        hints: FormFactorHints,
        ttlSeconds: Long = 300,
        now: Instant = clock.now()
    ): List<ProjectionCandidate> {
        val ranked = ArrayList<ProjectionCandidate>(readSources.size)
        for (readSource in readSources) {
            ranked += readFutureState(
                readSource, currentVersion, contextRef, forecastId, hints, ttlSeconds, now
            )
        }
        ranked.sortWith(CANDIDATE_ORDER)
        val out = ArrayList<ProjectionCandidate>(ranked.size)
        for (index in ranked.indices) {
            val candidate = ranked[index].copy(rank = index + 1)
            out += candidate
            events.append(
                ProjectionEvent(
                    id = ids.next("evj"),
                    t = now,
                    type = "projection.candidate.updated",
                    causalId = candidate.id,
                    payload = candidate
                )
            )
        }
        return out
    }

    fun liveCandidate(
        candidate: ProjectionCandidate,
        currentVersion: Long,
        now: Instant = clock.now()
    ): ProjectionCandidate? {
        if (candidate.baseStateVersion != currentVersion) {
            val invalidated = candidate.copy(status = CandidateStatus.INVALIDATED)
            events.append(
                ProjectionEvent(
                    id = ids.next("evj"),
                    t = now,
                    type = "projection.invalidated",
                    causalId = candidate.id,
                    payload = invalidated
                )
            )
            return null
        }
        if (candidate.status != CandidateStatus.PREPARED) return null
        val expiry = candidate.expiresAt
        if (expiry != null && !now.isBefore(expiry)) return null
        return candidate
    }

    private fun presentationOf(
        version: Long,
        facts: List<Claim>,
        now: Instant,
        stateIdentity: String,
        causalEventId: String,
        futureStateId: String?,
        forecastId: String?
    ): PresentationState {
        val atoms = atomsFrom(facts)
        return PresentationState(
            id = ids.next("ps"),
            sourceStateVersion = version,
            producedAt = now,
            atoms = atoms,
            lineage = CausalLineage(
                stateIdentity = stateIdentity,
                sourceStateVersion = version,
                causalEventId = causalEventId,
                futureStateId = futureStateId,
                forecastId = forecastId
            )
        )
    }

    private fun atomsFrom(facts: List<Claim>): List<PresentationAtom> {
        val atoms = ArrayList<PresentationAtom>(facts.size)
        for (fact in facts.sortedWith(FACT_ORDER)) {
            atoms += PresentationAtom(
                meaning = "fact",
                k = fact.k,
                v = fact.v,
                priority = floor(fact.confidence * 100.0).toInt()
            )
        }
        atoms.sortWith(ATOM_ORDER)
        return atoms
    }

    private fun requirements(atoms: List<PresentationAtom>): List<String> {
        val req = TreeSet<String>()
        if (atoms.isNotEmpty()) req.add("attend")
        for (atom in atoms) {
            if (atom.k.endsWith(".owned")) req.add("confirm")
        }
        return ArrayList(req)
    }

    companion object {
        val ATOM_ORDER = compareByDescending<PresentationAtom> { it.priority }
            .thenBy { it.k }
            .thenBy { it.meaning }

        val CANDIDATE_ORDER = compareByDescending<ProjectionCandidate> { it.priority }
            .thenBy { it.futureStateId }
            .thenBy { it.id }
    }
}
