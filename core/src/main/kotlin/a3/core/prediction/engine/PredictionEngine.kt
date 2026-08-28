package a3.core.prediction.engine

import a3.core.events.EventLog
import a3.core.model.CapabilityGraph
import a3.core.model.Event
import a3.core.model.Goal
import a3.core.model.Intent
import a3.core.prediction.model.Forecast
import a3.core.prediction.model.FutureState
import a3.core.prediction.model.PreparedState
import a3.core.prediction.model.PredictionInvalidation
import a3.core.prediction.model.PredictionPolicy
import a3.core.prediction.model.PredictionStatus
import a3.core.prediction.model.ProjectionCandidate
import a3.core.time.InstantSource
import a3.core.time.SequentialIdGenerator
import a3.core.world.BeliefState
import java.util.ArrayList

data class PredictionRequest(
    val contextRef: String,
    val belief: BeliefState,
    val graph: CapabilityGraph,
    val goal: Goal? = null,
    val intent: Intent? = null
)

data class PredictionResult(
    val forecast: Forecast,
    val futureStates: List<FutureState>,
    val prepared: PreparedState?,
    val projectionCandidates: List<ProjectionCandidate>,
    val invalidation: PredictionInvalidation?
)

/**
 * Off-path prediction. Prepares FutureState / PreparedState / ProjectionCandidate.
 * Never writes WorldState. Never produces Outcome.
 */
class PredictionEngine(
    private val clock: InstantSource,
    private val ids: SequentialIdGenerator,
    val events: EventLog,
    private val policy: PredictionPolicy,
    val store: PreparedStateStore,
    private val forecaster: RuleBasedForecaster = RuleBasedForecaster(),
    private val invalidator: PredictionInvalidator =
        PredictionInvalidator(clock, ids, events)
) {
    fun predict(request: PredictionRequest): PredictionResult {
        val now = clock.now()
        val signature = ContextSignatures.of(
            request.contextRef,
            request.belief,
            now,
            request.goal,
            request.intent
        )
        val invalidation = invalidator.invalidateMismatched(store, request.contextRef, signature)
        val bundle = forecaster.forecast(
            contextRef = request.contextRef,
            contextSignature = signature,
            belief = request.belief,
            graph = request.graph,
            policy = policy,
            now = now,
            ids = ids,
            goal = request.goal
        )
        append("prediction.updated", bundle.forecast, causalId = bundle.forecast.id)
        val prepared = bundle.futureStates.firstOrNull()?.let { future ->
            val state = PreparedState(
                id = ids.next("prep"),
                forecastId = bundle.forecast.id,
                futureStateId = future.id,
                contextRef = request.contextRef,
                contextSignature = signature,
                facts = future.facts,
                status = PredictionStatus.PREPARED,
                preparedAt = now,
                expiresAt = now.plusSeconds(policy.ttlSeconds),
                ttlSeconds = policy.ttlSeconds
            )
            store.put(state)
            append("prediction.updated", state, causalId = state.id)
            state
        }
        val projections = ArrayList<ProjectionCandidate>(bundle.futureStates.size)
        for (future in bundle.futureStates) {
            projections += project(future)
        }
        return PredictionResult(
            forecast = bundle.forecast,
            futureStates = bundle.futureStates,
            prepared = prepared,
            projectionCandidates = projections,
            invalidation = invalidation
        )
    }

    fun project(futureState: FutureState): ProjectionCandidate =
        ProjectionCandidate(
            id = ids.next("pjc"),
            futureStateRef = futureState.id,
            contextRef = futureState.contextRef,
            uiStateHint = "prefetch:${futureState.id}"
        )

    private fun append(type: String, payload: Any, causalId: String?) {
        events.append(
            Event(
                id = ids.next("evp"),
                t = clock.now(),
                source = "prediction",
                type = type,
                causalId = causalId,
                payload = payload
            )
        )
    }
}
