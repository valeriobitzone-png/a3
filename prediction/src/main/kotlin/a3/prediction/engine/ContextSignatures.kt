package a3.prediction.engine

import a3.core.world.api.BeliefReader
import a3.core.world.api.Claim
import a3.prediction.model.CapabilityHint
import a3.prediction.model.GoalHint
import java.time.Instant

object ContextSignatures {
    fun of(
        contextRef: String,
        belief: BeliefReader,
        now: Instant,
        goal: GoalHint? = null,
        intentId: String? = null
    ): String {
        val facts = belief.current(now)
            .sortedWith(compareBy<Claim> { it.k }.thenBy { it.id })
            .joinToString(";") { "${it.k}=${it.v}" }
        val goalId = goal?.id ?: ""
        val intent = intentId ?: ""
        return "$contextRef|$goalId|$intent|$facts"
    }
}

object CapabilityHints {
    fun ordered(capabilities: List<CapabilityHint>): List<CapabilityHint> {
        val byId = java.util.TreeMap<String, CapabilityHint>()
        for (cap in capabilities) {
            byId[cap.id] = cap
        }
        return ArrayList(byId.values)
    }

    fun canApply(belief: BeliefReader, cap: CapabilityHint, now: Instant): Boolean =
        cap.preconditions.all { required ->
            belief.current(now).any { actual -> actual.k == required.k && actual.v == required.v }
        }

    fun hypothesize(belief: BeliefReader, cap: CapabilityHint, now: Instant, score: Double): List<Claim> {
        val byKey = java.util.TreeMap<String, Claim>()
        for (fact in belief.current(now).sortedWith(FACT_ORDER)) {
            byKey[fact.k] = fact
        }
        for (effect in cap.effects.sortedWith(FACT_ORDER)) {
            byKey[effect.k] = effect.copy(
                confidence = score,
                observedAt = now,
                id = "",
                supersededBy = null
            )
        }
        return byKey.values.sortedWith(FACT_ORDER)
    }

    fun overlapsGoal(cap: CapabilityHint, goal: GoalHint): Boolean =
        cap.effects.any { effect ->
            goal.desired.any { desired -> desired.k == effect.k && desired.v == effect.v }
        }

    val FACT_ORDER = compareBy<Claim> { it.k }
        .thenBy { it.observedAt }
        .thenBy { it.id }
        .thenBy { it.source }
}
