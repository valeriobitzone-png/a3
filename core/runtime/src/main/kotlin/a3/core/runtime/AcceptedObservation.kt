package a3.core.runtime

import a3.core.world.AcceptedObservation
import a3.core.world.Observation
import java.time.Instant

/**
 * The only constructible [AcceptedObservation]. Constructor is `internal` to
 * `:core:runtime`, so other Gradle modules cannot mint commit tokens.
 */
class AcceptedObservationToken internal constructor(
    observation: Observation,
    now: Instant,
    causalId: String?
) : AcceptedObservation(observation, now, causalId)

fun acceptedObservation(
    observation: Observation,
    now: Instant,
    causalId: String? = null
): AcceptedObservation = AcceptedObservationToken(observation, now, causalId)
