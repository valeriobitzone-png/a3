package a3.projection.engine

import a3.projection.model.Projection
import a3.projection.model.ProjectionCandidate
import a3.projection.model.CandidateStatus
import java.time.Instant
import java.util.ArrayList
import java.util.Collections
import java.util.TreeMap

data class ProjectionEvent(
    val id: String,
    val t: Instant,
    val type: String,
    val causalId: String? = null,
    val payload: Any? = null
)

class ProjectionEventLog {
    private val events = ArrayList<ProjectionEvent>()

    fun append(event: ProjectionEvent) {
        require(event.id.isNotBlank()) { "event id must not be blank" }
        require(events.none { it.id == event.id }) { "Duplicate event id: ${event.id}" }
        events += event
    }

    fun all(): List<ProjectionEvent> = Collections.unmodifiableList(ArrayList(events))

    fun replay(): List<ProjectionEvent> = all()
}

data class ReplayedProjection(
    val projections: List<Projection>,
    val candidates: List<ProjectionCandidate>
)

object ProjectionReplay {
    fun replay(log: ProjectionEventLog): ReplayedProjection {
        val projections = TreeMap<String, Projection>()
        val candidates = TreeMap<String, ProjectionCandidate>()
        val projectionOrder = ArrayList<String>()
        val candidateOrder = ArrayList<String>()
        for (event in log.all()) {
            when (event.type) {
                "projection.updated" -> {
                    val payload = event.payload as? Projection ?: continue
                    if (payload.id !in projections) projectionOrder += payload.id
                    projections[payload.id] = payload
                }
                "projection.candidate.updated" -> {
                    val payload = event.payload as? ProjectionCandidate ?: continue
                    if (payload.id !in candidates) candidateOrder += payload.id
                    candidates[payload.id] = payload
                }
                "projection.invalidated" -> {
                    when (val payload = event.payload) {
                        is ProjectionCandidate -> {
                            if (payload.id !in candidates) candidateOrder += payload.id
                            candidates[payload.id] = payload
                        }
                        is String -> {
                            val current = candidates[payload] ?: continue
                            candidates[payload] = current.copy(status = CandidateStatus.INVALIDATED)
                        }
                    }
                }
            }
        }
        val projectionList = ArrayList<Projection>(projectionOrder.size)
        for (id in projectionOrder) {
            projections[id]?.let { projectionList += it }
        }
        val candidateList = ArrayList<ProjectionCandidate>(candidateOrder.size)
        for (id in candidateOrder) {
            candidates[id]?.let { candidateList += it }
        }
        return ReplayedProjection(projectionList, candidateList)
    }
}
