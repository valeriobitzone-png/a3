package a3.a3ui.engine

import a3.a3ui.model.Binding
import a3.a3ui.model.EpistemicAction
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.EpistemicFreshness
import a3.a3ui.model.EpistemicStatus
import a3.a3ui.model.EpistemicSupport
import a3.a3ui.model.Node
import a3.core.world.api.Claim
import java.time.Instant
import java.util.ArrayList
import java.util.TreeMap

/**
 * Boundary facts for axis derivation. [asOf] is stamped at the composition
 * boundary — never read from a clock here.
 *
 * [actionPhase] / [compensationPhase] are wire names of
 * `a3.core.action.ActionPhase` / `CompensationPhase` (consumed, not owned).
 */
data class EpistemicFacts(
    val claim: Claim? = null,
    val admissionHeld: Boolean = false,
    val revisionContradicted: Boolean = false,
    val actionPhase: String? = null,
    val compensationPhase: String? = null
)

object Epistemic {
    fun derive(facts: EpistemicFacts, asOf: Instant): EpistemicAxis {
        val claim = facts.claim
        val support = supportOf(claim)
        val freshness = freshnessOf(claim, asOf)
        val status = statusOf(facts)
        val action = actionOf(facts)
        return EpistemicAxis(support, freshness, status, action)
    }

    fun supportOf(claim: Claim?): EpistemicSupport {
        if (claim == null) return EpistemicSupport.HIGH
        val confidence = claim.confidence
        return when {
            confidence >= 1.0 -> EpistemicSupport.HIGH
            confidence >= 0.5 -> EpistemicSupport.MEDIUM
            confidence > 0.0 -> EpistemicSupport.LOW
            else -> EpistemicSupport.UNKNOWN
        }
    }

    fun freshnessOf(claim: Claim?, asOf: Instant): EpistemicFreshness {
        val expires = claim?.expiresAt ?: return EpistemicFreshness.FRESH
        if (!asOf.isBefore(expires)) return EpistemicFreshness.STALE
        val total = expires.toEpochMilli() - claim.observedAt.toEpochMilli()
        if (total <= 0L) return EpistemicFreshness.STALE
        val remaining = expires.toEpochMilli() - asOf.toEpochMilli()
        return if (remaining * 3L <= total) EpistemicFreshness.AGING else EpistemicFreshness.FRESH
    }

    fun statusOf(facts: EpistemicFacts): EpistemicStatus {
        val phase = facts.actionPhase?.lowercase()
        return when {
            facts.revisionContradicted || phase == "contradicted" -> EpistemicStatus.CONTRADICTED
            facts.admissionHeld -> EpistemicStatus.HELD
            else -> EpistemicStatus.BELIEVED
        }
    }

    fun actionOf(facts: EpistemicFacts): EpistemicAction {
        val compensation = facts.compensationPhase?.lowercase()
        if (compensation == "compensated") return EpistemicAction.COMPENSATED
        return when (facts.actionPhase?.lowercase()) {
            null, "", "na" -> EpistemicAction.NA
            "created", "dispatched", "acknowledged" -> EpistemicAction.PENDING
            "unknown", "failed" -> EpistemicAction.UNKNOWN
            "completed", "observed" -> EpistemicAction.DONE
            else -> EpistemicAction.NA
        }
    }

    fun requiresExposure(facts: EpistemicFacts, asOf: Instant): Boolean {
        val derived = derive(facts, asOf)
        val claim = facts.claim
        val confUncertain = claim != null && claim.confidence < 1.0
        val stale = derived.freshness == EpistemicFreshness.STALE
        val held = derived.status == EpistemicStatus.HELD
        val contradicted = derived.status == EpistemicStatus.CONTRADICTED
        val actionUncertain =
            derived.action == EpistemicAction.UNKNOWN ||
                derived.action == EpistemicAction.COMPENSATED
        return confUncertain || stale || held || contradicted || actionUncertain
    }

    fun requireExposed(axis: EpistemicAxis, facts: EpistemicFacts, asOf: Instant) {
        if (requiresExposure(facts, asOf) && axis.isDefault()) {
            throw IllegalStateException("uncertain rendered as certain")
        }
    }

    fun attach(
        tree: ComposedTree,
        asOf: Instant,
        factsByKey: Map<String, EpistemicFacts>
    ): ComposedTree {
        if (factsByKey.isEmpty()) return tree
        val byNode = TreeMap<String, EpistemicAxis>()
        for (binding in tree.bindings) {
            val facts = factsByKey[binding.atomKey] ?: continue
            val derived = derive(facts, asOf)
            requireExposed(derived, facts, asOf)
            val stored = if (derived.isDefault()) null else derived
            if (stored != null) {
                val existing = byNode[binding.nodeId]
                byNode[binding.nodeId] = if (existing == null) stored else merge(existing, stored)
            }
        }
        for ((key, facts) in factsByKey) {
            if (tree.bindings.none { it.atomKey == key }) continue
            val derived = derive(facts, asOf)
            requireExposed(derived, facts, asOf)
        }
        val nodes = applyAxis(tree.nodes, byNode)
        return ComposedTree(nodes, tree.bindings, tree.gestureTargets)
    }

    private fun merge(a: EpistemicAxis, b: EpistemicAxis): EpistemicAxis = EpistemicAxis(
        support = minSupport(a.support, b.support),
        freshness = maxFreshness(a.freshness, b.freshness),
        status = maxStatus(a.status, b.status),
        action = maxAction(a.action, b.action)
    )

    private fun minSupport(a: EpistemicSupport, b: EpistemicSupport): EpistemicSupport {
        val order = listOf(
            EpistemicSupport.UNKNOWN,
            EpistemicSupport.LOW,
            EpistemicSupport.MEDIUM,
            EpistemicSupport.HIGH
        )
        return if (order.indexOf(a) <= order.indexOf(b)) a else b
    }

    private fun maxFreshness(a: EpistemicFreshness, b: EpistemicFreshness): EpistemicFreshness {
        val order = listOf(
            EpistemicFreshness.FRESH,
            EpistemicFreshness.AGING,
            EpistemicFreshness.STALE
        )
        return if (order.indexOf(a) >= order.indexOf(b)) a else b
    }

    private fun maxStatus(a: EpistemicStatus, b: EpistemicStatus): EpistemicStatus {
        val order = listOf(
            EpistemicStatus.BELIEVED,
            EpistemicStatus.HELD,
            EpistemicStatus.CONTRADICTED
        )
        return if (order.indexOf(a) >= order.indexOf(b)) a else b
    }

    private fun maxAction(a: EpistemicAction, b: EpistemicAction): EpistemicAction {
        val order = listOf(
            EpistemicAction.NA,
            EpistemicAction.DONE,
            EpistemicAction.PENDING,
            EpistemicAction.UNKNOWN,
            EpistemicAction.COMPENSATED
        )
        return if (order.indexOf(a) >= order.indexOf(b)) a else b
    }

    private fun applyAxis(nodes: List<Node>, byNode: Map<String, EpistemicAxis>): List<Node> {
        val out = ArrayList<Node>(nodes.size)
        for (node in nodes) {
            val axis = byNode[node.id]
            out += node.copy(
                children = applyAxis(node.children, byNode),
                axis = axis
            )
        }
        return out
    }
}
