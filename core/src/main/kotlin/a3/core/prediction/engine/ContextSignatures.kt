package a3.core.prediction.engine

import a3.core.model.Fact
import a3.core.model.Goal
import a3.core.model.Intent
import a3.core.world.BeliefState
import java.time.Instant

object ContextSignatures {
    fun of(
        contextRef: String,
        belief: BeliefState,
        now: Instant,
        goal: Goal? = null,
        intent: Intent? = null
    ): String {
        val facts = belief.current(now)
            .sortedWith(compareBy<Fact> { it.k }.thenBy { it.id })
            .joinToString(";") { "${it.k}=${it.v}" }
        val goalId = goal?.id ?: ""
        val intentId = intent?.id ?: ""
        return "$contextRef|$goalId|$intentId|$facts"
    }
}
