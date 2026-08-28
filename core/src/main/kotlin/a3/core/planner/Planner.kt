package a3.core.planner

import a3.core.capability.Transition
import a3.core.model.*
import a3.core.schema.ModelValidator
import a3.core.world.BeliefState
import java.time.Instant
import java.util.ArrayDeque
import java.util.TreeMap
import java.util.TreeSet

sealed class PlannerError {
    data object NoPlanFound : PlannerError()
    data object ConstraintConflict : PlannerError()
    data object MissingCapability : PlannerError()
    data object MissingPrecondition : PlannerError()
    data object TrustBlocked : PlannerError()
    data object StaleState : PlannerError()
}

sealed class PlanResult {
    data class Success(val plan: Plan) : PlanResult()
    data class Failure(val error: PlannerError) : PlanResult()
}

class DeterministicPlanner {
    fun plan(goal: Goal, state: BeliefState, graph: CapabilityGraph, now: Instant): PlanResult {
        ModelValidator.goal(goal)

        if (hasStaleRequiredFact(state, graph, now)) {
            return PlanResult.Failure(PlannerError.StaleState)
        }

        if (desiredUnproducible(goal, state, graph, now)) {
            return PlanResult.Failure(PlannerError.MissingCapability)
        }

        data class Node(val state: BeliefState, val steps: List<PlanStep>, val cost: Cost)

        val queue = ArrayDeque<Node>()
        queue.add(Node(state, emptyList(), Cost()))
        val visited = TreeSet<String>()
        val caps = Transition.orderedCapabilities(graph.capabilities)
        var sawConstraintConflict = false
        var sawMissingPrecondition = false

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val signature = signature(node.state, now)
            if (!visited.add(signature)) continue

            if (satisfies(goal, node.state, now)) {
                if (violatesConstraints(goal.constraints, node.cost, node.state.current(now))) {
                    sawConstraintConflict = true
                    continue
                }
                val plan = Plan(
                    id = "plan_${goal.id}",
                    goalRef = goal.id,
                    steps = node.steps,
                    expectedOutcome = ExpectedOutcome(goal.id, goal.desiredState),
                    totalCost = node.cost,
                    status = "proposed"
                )
                ModelValidator.plan(plan)
                return PlanResult.Success(plan)
            }

            for (cap in caps) {
                if (node.steps.any { it.capability == cap.id }) continue
                if (!Transition.canApply(node.state, cap, now)) {
                    if (producesDesired(cap, goal) && missingSatisfiablePrecondition(node.state, cap, now)) {
                        sawMissingPrecondition = true
                    }
                    continue
                }
                val next = Transition.apply(node.state, cap, now)
                val step = PlanStep(
                    seq = node.steps.size + 1,
                    capability = cap.id,
                    trustTier = inferTier(cap),
                    reversible = cap.reversible
                )
                queue.add(Node(next, node.steps + step, node.cost + cap.cost))
            }
        }

        return PlanResult.Failure(
            when {
                sawConstraintConflict -> PlannerError.ConstraintConflict
                sawMissingPrecondition -> PlannerError.MissingPrecondition
                else -> PlannerError.NoPlanFound
            }
        )
    }

    /**
     * STALE_STATE fires only when a live fact required by a graph capability
     * has expires_at < now and no valid replacement exists.
     */
    private fun hasStaleRequiredFact(state: BeliefState, graph: CapabilityGraph, now: Instant): Boolean {
        val live = state.liveFacts()
        for (cap in Transition.orderedCapabilities(graph.capabilities)) {
            for (required in cap.preconditions) {
                val expired = live.any { f ->
                    f.k == required.k && f.v == required.v &&
                        f.expiresAt != null && f.expiresAt.isBefore(now)
                }
                val valid = live.any { f -> f.k == required.k && f.v == required.v && state.validAt(f, now) }
                if (expired && !valid) return true
            }
        }
        return false
    }

    private fun desiredUnproducible(
        goal: Goal,
        state: BeliefState,
        graph: CapabilityGraph,
        now: Instant
    ): Boolean = goal.desiredState.any { desired ->
        state.current(now).none { it.k == desired.k && it.v == desired.v } &&
            Transition.orderedCapabilities(graph.capabilities).none { cap ->
                cap.effects.any { it.k == desired.k && it.v == desired.v }
            }
    }

    private fun satisfies(goal: Goal, state: BeliefState, now: Instant): Boolean =
        goal.desiredState.all { d -> state.current(now).any { f -> f.k == d.k && f.v == d.v } }

    private fun producesDesired(cap: Capability, goal: Goal): Boolean =
        cap.effects.any { e -> goal.desiredState.any { it.k == e.k && it.v == e.v } }

    private fun missingSatisfiablePrecondition(state: BeliefState, cap: Capability, now: Instant): Boolean =
        cap.preconditions.any { required ->
            state.current(now).none { it.k == required.k && it.v == required.v }
        }

    private fun violatesConstraints(constraints: List<Constraint>, cost: Cost, facts: List<Fact>): Boolean {
        if (constraints.isEmpty()) return false
        val byKey = TreeMap<String, Fact>()
        for (fact in facts.sortedWith(compareBy({ it.k }, { it.id }))) {
            byKey[fact.k] = fact
        }
        return constraints.any { c ->
            val actual = when (c.key) {
                "budget" -> cost.money
                "time", "timeMin" -> cost.timeMin
                else -> (byKey[c.key]?.v as? Number)?.toDouble()
            } ?: return@any false
            val bound = (c.value as? Number)?.toDouble() ?: return@any false
            when (c.op) {
                "<=" -> actual > bound
                "<" -> actual >= bound
                ">=" -> actual < bound
                ">" -> actual <= bound
                "==", "=" -> actual != bound
                else -> false
            }
        }
    }

    private fun inferTier(cap: Capability): TrustTier =
        if (cap.reversible) TrustTier.PREPARE else TrustTier.IRREVERSIBLE

    private fun signature(state: BeliefState, now: Instant): String =
        state.current(now).joinToString("|") { "${it.k}=${it.v}" }
}
