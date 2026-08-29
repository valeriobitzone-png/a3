package a3.core.runtime

import a3.core.policy.PolicyDecision
import a3.core.world.AcceptedObservation
import a3.core.world.IntegrateMode
import a3.core.world.Observation
import java.time.Instant

/**
 * The only constructible [AcceptedObservation]. Constructor is `internal` to
 * `:core:runtime`, so other Gradle modules cannot mint commit tokens.
 */
class AcceptedObservationToken internal constructor(
    observation: Observation,
    now: Instant,
    causalId: String?,
    integrateMode: IntegrateMode,
    policyDecision: String
) : AcceptedObservation(observation, now, causalId, integrateMode, policyDecision)

fun mintAcceptedObservation(
    observation: Observation,
    policyDecision: PolicyDecision,
    now: Instant,
    causalId: String? = null,
    integrateMode: IntegrateMode = IntegrateMode.SUPERSEDE_KEYS
): AcceptedObservation = AcceptedObservationToken(
    observation,
    now,
    causalId,
    integrateMode,
    policyDecision.name
)

fun acceptedObservation(
    observation: Observation,
    now: Instant,
    causalId: String? = null
): AcceptedObservation = mintAcceptedObservation(
    observation,
    PolicyDecision.ALLOW,
    now,
    causalId
)
