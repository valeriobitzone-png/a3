// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.action

/**
 * Pure action transition. Timestamps are event fields. No ambient clock.
 * Never mutates believed reality. Never calls admission evaluate.
 */
fun transition(current: ActionState, event: ActionEvent): ActionState {
    val nextEvents = current.events + event
    return when (event) {
        is ActionEvent.PlanProposed -> current.copy(
            planPhase = PlanPhase.PROPOSED,
            events = nextEvents
        )
        is ActionEvent.PlanValidated -> current.copy(
            planPhase = PlanPhase.VALIDATED,
            planDigest = event.planDigest,
            events = nextEvents
        )
        is ActionEvent.AuthorizationGranted -> {
            require(current.planPhase == PlanPhase.VALIDATED || current.planPhase == PlanPhase.AUTHORIZED) {
                "authorization requires a validated plan"
            }
            current.copy(
                planPhase = PlanPhase.AUTHORIZED,
                authorization = event.authorization,
                events = nextEvents
            )
        }
        is ActionEvent.AuthorizationDenied -> {
            val inFlight = current.actionPhase == ActionPhase.DISPATCHED ||
                current.actionPhase == ActionPhase.ACKNOWLEDGED
            current.copy(
                planPhase = PlanPhase.DENIED,
                actionPhase = if (inFlight) ActionPhase.UNKNOWN else current.actionPhase,
                events = nextEvents
            )
        }
        is ActionEvent.CommandCreated -> createCommand(current, event, nextEvents)
        is ActionEvent.CommandDispatched -> dispatchCommand(current, event, nextEvents)
        is ActionEvent.ExecutorAcknowledged -> {
            require(current.actionPhase == ActionPhase.DISPATCHED) { "ack requires DISPATCHED" }
            current.copy(actionPhase = ActionPhase.ACKNOWLEDGED, events = nextEvents)
        }
        is ActionEvent.ExecutorCompleted -> completeExecutor(current, event, nextEvents)
        is ActionEvent.ExecutorFailed -> failExecutor(current, event, nextEvents)
        is ActionEvent.TimeoutObserved -> timeout(current, event, nextEvents)
        is ActionEvent.ObservationLinked -> linkObserved(current, event, nextEvents)
        is ActionEvent.ContradictionLinked -> {
            require(
                current.actionPhase == ActionPhase.COMPLETED ||
                    current.actionPhase == ActionPhase.UNKNOWN ||
                    current.actionPhase == ActionPhase.OBSERVED
            ) { "contradiction requires COMPLETED, UNKNOWN, or OBSERVED" }
            require(event.acceptedObservationId.isNotBlank())
            current.copy(
                actionPhase = ActionPhase.CONTRADICTED,
                linkedAcceptedObservationId = event.acceptedObservationId,
                events = nextEvents
            )
        }
        is ActionEvent.CompensationRequested -> current.copy(
            compensationPhase = CompensationPhase.REQUESTED,
            events = nextEvents
        )
        is ActionEvent.CompensationCompleted -> current.copy(
            compensationPhase = CompensationPhase.COMPENSATED,
            events = nextEvents
        )
        is ActionEvent.CompensationFailed -> current.copy(
            compensationPhase = CompensationPhase.COMPENSATION_FAILED,
            events = nextEvents
        )
    }
}

fun requestCompensation(
    original: ActionState,
    at: java.time.Instant,
    compensationPlanDigest: String
): Pair<ActionState, ActionState> {
    val parent = transition(original, ActionEvent.CompensationRequested(at, compensationPlanDigest))
    require(parent.events.take(original.events.size) == original.events) {
        "compensation must not rewrite original action history"
    }
    val child = ActionState(
        actionId = "${original.actionId}:compensation",
        originActionId = original.actionId
    )
    val started = transition(child, ActionEvent.PlanProposed(at, compensationPlanDigest))
    return parent to started
}

private fun createCommand(
    current: ActionState,
    event: ActionEvent.CommandCreated,
    nextEvents: List<ActionEvent>
): ActionState {
    val auth = current.authorization
        ?: throw IllegalStateException("command requires authorization")
    if (event.at >= auth.expiresAt) {
        return current.copy(planPhase = PlanPhase.EXPIRED, events = nextEvents)
    }
    if (event.command.planDigest != auth.planDigest) {
        throw IllegalArgumentException("command planDigest mismatch")
    }
    if (event.beliefRevisionHash != auth.beliefRevisionHash) {
        return current.copy(planPhase = PlanPhase.STALE_BELIEF, events = nextEvents)
    }
    return current.copy(
        command = event.command,
        actionPhase = ActionPhase.CREATED,
        events = nextEvents
    )
}

private fun dispatchCommand(
    current: ActionState,
    event: ActionEvent.CommandDispatched,
    nextEvents: List<ActionEvent>
): ActionState {
    val auth = current.authorization
        ?: throw IllegalStateException("dispatch requires authorization")
    if (current.command == null) {
        throw IllegalStateException("dispatch requires a command")
    }
    if (current.planPhase == PlanPhase.EXPIRED || event.at >= auth.expiresAt) {
        return current.copy(planPhase = PlanPhase.EXPIRED, events = nextEvents)
    }
    if (current.planPhase == PlanPhase.STALE_BELIEF ||
        event.beliefRevisionHash != auth.beliefRevisionHash
    ) {
        return current.copy(planPhase = PlanPhase.STALE_BELIEF, events = nextEvents)
    }
    if (current.planPhase != PlanPhase.AUTHORIZED) {
        throw IllegalStateException("dispatch requires AUTHORIZED plan")
    }
    return current.copy(
        actionPhase = ActionPhase.DISPATCHED,
        dispatchId = event.dispatchId,
        events = nextEvents
    )
}

private fun completeExecutor(
    current: ActionState,
    event: ActionEvent.ExecutorCompleted,
    nextEvents: List<ActionEvent>
): ActionState {
    if (current.actionPhase == ActionPhase.UNKNOWN) {
        unknownDoesNotSucceed()
    }
    if (current.actionPhase == ActionPhase.OBSERVED) {
        completedIsNotObserved()
    }
    require(
        current.actionPhase == ActionPhase.DISPATCHED ||
            current.actionPhase == ActionPhase.ACKNOWLEDGED ||
            current.actionPhase == ActionPhase.COMPLETED
    ) { "ExecutorCompleted requires DISPATCHED or ACKNOWLEDGED" }
    return current.copy(actionPhase = ActionPhase.COMPLETED, events = nextEvents)
}

private fun failExecutor(
    current: ActionState,
    event: ActionEvent.ExecutorFailed,
    nextEvents: List<ActionEvent>
): ActionState {
    if (event.reason.contains("timeout", ignoreCase = true) && current.actionPhase != ActionPhase.FAILED) {
        timeoutIsNotFailed()
    }
    require(
        current.actionPhase == ActionPhase.DISPATCHED ||
            current.actionPhase == ActionPhase.ACKNOWLEDGED
    ) { "ExecutorFailed requires in-flight action" }
    return current.copy(actionPhase = ActionPhase.FAILED, events = nextEvents)
}

private fun timeout(
    current: ActionState,
    event: ActionEvent.TimeoutObserved,
    nextEvents: List<ActionEvent>
): ActionState {
    if (current.actionPhase == ActionPhase.FAILED) {
        return current.copy(events = nextEvents)
    }
    require(
        current.actionPhase == ActionPhase.DISPATCHED ||
            current.actionPhase == ActionPhase.ACKNOWLEDGED ||
            current.actionPhase == ActionPhase.UNKNOWN
    ) { "TimeoutObserved requires in-flight or UNKNOWN" }
    return current.copy(actionPhase = ActionPhase.UNKNOWN, events = nextEvents)
}

private fun linkObserved(
    current: ActionState,
    event: ActionEvent.ObservationLinked,
    nextEvents: List<ActionEvent>
): ActionState {
    if (event.acceptedObservationId.isBlank()) {
        candidateCannotLinkObserved()
    }
    require(
        current.actionPhase == ActionPhase.COMPLETED ||
            current.actionPhase == ActionPhase.UNKNOWN
    ) { "ObservationLinked requires COMPLETED or UNKNOWN" }
    return current.copy(
        actionPhase = ActionPhase.OBSERVED,
        linkedAcceptedObservationId = event.acceptedObservationId,
        events = nextEvents
    )
}
