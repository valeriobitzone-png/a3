package a3.core.prediction

import a3.core.model.Observation
import a3.core.model.Prediction
import a3.core.world.EpistemicSource
import a3.core.world.WorldState
import a3.core.world.WriteResult
import java.time.Instant

/**
 * Prediction may prepare future states off-path. It has no successful
 * WorldState write pathway.
 */
object PredictionWrite {
    fun attempt(
        world: WorldState,
        prediction: Prediction,
        observation: Observation,
        now: Instant
    ): WriteResult = world.write(EpistemicSource.PREDICTION, observation, now, causalId = prediction.id)
}

object PolicyWrite {
    fun attempt(world: WorldState, observation: Observation, now: Instant): WriteResult =
        world.write(EpistemicSource.POLICY, observation, now)
}

object ExecutionWrite {
    fun attempt(world: WorldState, observation: Observation, now: Instant): WriteResult =
        world.write(EpistemicSource.EXECUTION, observation, now)
}
