package a3.core.runtime

import a3.core.action.ActionEvent
import a3.core.action.ActionPhase
import a3.core.action.ActionState
import a3.core.action.Authorization
import a3.core.action.Command
import a3.core.action.ExecutionReceipt
import a3.core.action.receiptCannotClaimDomain
import a3.core.action.transition
import a3.core.serialize.CanonicalJson
import a3.core.world.BeliefState
import java.security.MessageDigest
import java.time.Instant

fun beliefRevisionHash(state: BeliefState): String =
    sha256Hex(CanonicalJson.bytesState(state))

fun applyActionEventToBelief(state: BeliefState, event: ActionEvent): BeliefState {
    throw IllegalStateException("action transition must not mutate BeliefState")
}

fun applyReceiptToBelief(state: BeliefState, receipt: ExecutionReceipt): BeliefState {
    receiptCannotClaimDomain(receipt)
}

internal fun seedAuthorizedAction(
    actionId: String,
    planDigest: String,
    now: Instant,
    revisionHash: String
): ActionState {
    val auth = Authorization(
        authorizationId = "auth_$actionId",
        planDigest = planDigest,
        beliefRevisionHash = revisionHash,
        principal = "runtime",
        scopes = listOf("execute"),
        resource = actionId,
        maximumImpact = "plan",
        expiresAt = now.plusSeconds(3600),
        idempotencyKey = "idem_$actionId",
        policyId = "runtime-permissive",
        policyVersion = "1",
        policyDigest = "runtime"
    )
    var state = ActionState(actionId = actionId)
    state = transition(state, ActionEvent.PlanProposed(now, actionId))
    state = transition(state, ActionEvent.PlanValidated(now, planDigest))
    state = transition(state, ActionEvent.AuthorizationGranted(now, auth.authorizationId, auth))
    return state
}

internal fun dispatchCommand(
    state: ActionState,
    commandId: String,
    now: Instant,
    revisionHash: String
): ActionState {
    val planDigest = state.planDigest ?: error("planDigest")
    val authorizationId = state.authorization?.authorizationId ?: error("authorization")
    val command = Command(
        commandId = commandId,
        planDigest = planDigest,
        authorizationId = authorizationId,
        idempotencyKey = commandId,
        commandDigest = commandId
    )
    var next = transition(state, ActionEvent.CommandCreated(now, command.commandDigest, command, revisionHash))
    next = transition(next, ActionEvent.CommandDispatched(now, "disp_$commandId", revisionHash))
    return next
}

internal fun completeWithReceipt(state: ActionState, receipt: ExecutionReceipt): ActionState =
    transition(state, ActionEvent.ExecutorCompleted(receipt.at, receipt.receiptId))

internal fun linkAccepted(state: ActionState, acceptedObservationId: String, at: Instant): ActionState =
    transition(state, ActionEvent.ObservationLinked(at, acceptedObservationId))

fun ActionState.isCompletedNotObserved(): Boolean =
    actionPhase == ActionPhase.COMPLETED && linkedAcceptedObservationId == null

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { b -> "%02x".format(b) }
}
