package a3.core.action

import java.time.Instant

enum class PlanPhase {
    PROPOSED,
    VALIDATED,
    AUTHORIZED,
    DENIED,
    EXPIRED,
    STALE_BELIEF
}

enum class ActionPhase {
    CREATED,
    DISPATCHED,
    ACKNOWLEDGED,
    COMPLETED,
    FAILED,
    UNKNOWN,
    OBSERVED,
    CONTRADICTED
}

enum class CompensationPhase {
    NOT_REQUIRED,
    AVAILABLE,
    REQUESTED,
    COMPENSATED,
    COMPENSATION_FAILED,
    UNCOMPENSABLE
}

data class Authorization(
    val authorizationId: String,
    val planDigest: String,
    val beliefRevisionHash: String,
    val principal: String,
    val scopes: List<String>,
    val resource: String,
    val maximumImpact: String,
    val expiresAt: Instant,
    val idempotencyKey: String,
    val policyId: String,
    val policyVersion: String,
    val policyDigest: String,
    val consentPresentationHash: String? = null
) {
    init {
        require(authorizationId.isNotBlank())
        require(planDigest.isNotBlank())
        require(beliefRevisionHash.isNotBlank())
        require(idempotencyKey.isNotBlank()) { "Authorization idempotencyKey required" }
    }
}

data class Command(
    val commandId: String,
    val planDigest: String,
    val authorizationId: String,
    val idempotencyKey: String,
    val commandDigest: String
) {
    init {
        require(commandId.isNotBlank())
        require(planDigest.isNotBlank())
        require(authorizationId.isNotBlank())
        require(idempotencyKey.isNotBlank()) { "Command MUST carry an idempotencyKey" }
        require(commandDigest.isNotBlank())
    }
}

data class ExecutionReceipt(
    val receiptId: String,
    val commandId: String,
    val dispatchId: String,
    val ok: Boolean,
    val at: Instant
)

sealed class ActionEvent {
    abstract val at: Instant

    data class PlanProposed(override val at: Instant, val intentDigest: String) : ActionEvent()
    data class PlanValidated(override val at: Instant, val planDigest: String) : ActionEvent()
    data class AuthorizationGranted(
        override val at: Instant,
        val authorizationDigest: String,
        val authorization: Authorization
    ) : ActionEvent()
    data class AuthorizationDenied(override val at: Instant, val reason: String) : ActionEvent()
    data class CommandCreated(
        override val at: Instant,
        val commandDigest: String,
        val command: Command,
        val beliefRevisionHash: String
    ) : ActionEvent()
    data class CommandDispatched(
        override val at: Instant,
        val dispatchId: String,
        val beliefRevisionHash: String
    ) : ActionEvent()
    data class ExecutorAcknowledged(override val at: Instant, val receiptId: String) : ActionEvent()
    data class ExecutorCompleted(override val at: Instant, val receiptId: String) : ActionEvent()
    data class ExecutorFailed(override val at: Instant, val reason: String) : ActionEvent()
    data class TimeoutObserved(override val at: Instant, val timeoutId: String) : ActionEvent()
    data class ObservationLinked(override val at: Instant, val acceptedObservationId: String) : ActionEvent()
    data class ContradictionLinked(override val at: Instant, val acceptedObservationId: String) : ActionEvent()
    data class CompensationRequested(override val at: Instant, val compensationPlanDigest: String) : ActionEvent()
    data class CompensationCompleted(override val at: Instant, val receiptId: String) : ActionEvent()
    data class CompensationFailed(override val at: Instant, val reason: String) : ActionEvent()
}

data class ActionState(
    val actionId: String,
    val planPhase: PlanPhase = PlanPhase.PROPOSED,
    val actionPhase: ActionPhase = ActionPhase.CREATED,
    val compensationPhase: CompensationPhase = CompensationPhase.NOT_REQUIRED,
    val planDigest: String? = null,
    val authorization: Authorization? = null,
    val command: Command? = null,
    val dispatchId: String? = null,
    val linkedAcceptedObservationId: String? = null,
    val originActionId: String? = null,
    val events: List<ActionEvent> = emptyList()
)

fun receiptCannotClaimDomain(receipt: ExecutionReceipt): Nothing =
    throw IllegalStateException("ExecutionReceipt cannot establish an external-world claim")

fun completedIsNotObserved(): Nothing =
    throw IllegalStateException("COMPLETED is not OBSERVED")

fun timeoutIsNotFailed(): Nothing =
    throw IllegalStateException("timeout cannot FAILED without independent evidence")

fun unknownDoesNotSucceed(): Nothing =
    throw IllegalStateException("UNKNOWN does not auto-resolve to SUCCESS")

fun candidateCannotLinkObserved(): Nothing =
    throw IllegalStateException("ObservationCandidate cannot be linked as OBSERVED")

fun rewriteActionHistory(): Nothing =
    throw IllegalStateException("compensation must not rewrite action history")

fun domainSuccessWithoutObservation(): Nothing =
    throw IllegalStateException("success without observation")
