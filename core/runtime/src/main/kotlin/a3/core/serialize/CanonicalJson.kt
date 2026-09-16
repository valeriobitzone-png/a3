// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.serialize

import a3.core.json.CanonicalJson as JsonCanonical
import a3.core.model.*
import a3.core.planner.PlanResult
import a3.core.planner.PlannerError
import a3.core.world.BeliefState

/**
 * Core typed facade. Engine rules live in :core:json.
 */
object CanonicalJson {
    fun of(value: Any?): String = JsonCanonical.encode(wire(value, Profile.SCHEMA))

    fun ofState(state: BeliefState): String = JsonCanonical.encode(wire(state, Profile.STATE))

    fun planResult(result: PlanResult): String = JsonCanonical.encode(wire(result, Profile.SCHEMA))

    fun bytes(value: Any?): ByteArray = of(value).toByteArray(Charsets.UTF_8)

    fun bytesState(state: BeliefState): ByteArray = ofState(state).toByteArray(Charsets.UTF_8)

    enum class Profile { SCHEMA, STATE }

    fun errorCode(error: PlannerError): String = when (error) {
        PlannerError.NoPlanFound -> "NO_PLAN_FOUND"
        PlannerError.ConstraintConflict -> "CONSTRAINT_CONFLICT"
        PlannerError.MissingCapability -> "MISSING_CAPABILITY"
        PlannerError.UnmetPrecondition -> "UNMET_PRECONDITION"
        PlannerError.TrustBlocked -> "TRUST_BLOCKED"
        PlannerError.StaleState -> "STALE_STATE"
    }

    private fun wire(value: Any?, profile: Profile): Any? = when (value) {
        null, is Boolean, is Number, is String, is java.time.Instant -> value
        is Claim -> factFields(value, profile)
        is Observation -> mapOf(
            "execution_ref" to value.executionRef,
            "facts" to wire(sortedFacts(value.facts), profile),
            "id" to value.id,
            "t" to value.t
        )
        is Intent -> buildMap {
            put("confidence", value.confidence)
            put("expression", value.expression)
            value.goalRef?.let { put("goal_ref", it) }
            put("id", value.id)
            put("modality", value.modality)
            put("source", value.source)
        }
        is Goal -> buildMap {
            if (value.constraints.isNotEmpty()) {
                put("constraints", wire(value.constraints, profile))
            }
            value.deadline?.let { put("deadline", it) }
            put("desired_state", wire(sortedFacts(value.desiredState), profile))
            put("id", value.id)
            put("intent_ref", value.intentRef)
            put("priority", value.priority)
        }
        is Constraint -> mapOf("key" to value.key, "op" to value.op, "value" to wire(value.value, profile))
        is Cost -> mapOf(
            "energy" to value.energy,
            "money" to value.money,
            "timeMin" to value.timeMin
        )
        is Capability -> capability(value, profile)
        is PlanStep -> mapOf(
            "capability" to value.capability,
            "reversible" to value.reversible,
            "seq" to value.seq,
            "trust_tier" to value.trustTier.wire()
        )
        is ExpectedOutcome -> mapOf(
            "facts" to wire(sortedFacts(value.facts), profile),
            "goal_ref" to value.goalRef
        )
        is Plan -> mapOf(
            "expected_outcome" to wire(value.expectedOutcome, profile),
            "goal_ref" to value.goalRef,
            "id" to value.id,
            "status" to value.status,
            "steps" to wire(value.steps.sortedBy { it.seq }, profile)
        )
        is Outcome -> buildMap {
            put("confidence", value.confidence)
            value.cost?.let { put("cost", wire(it, profile)) }
            put("goal_ref", value.goalRef)
            put("id", value.id)
            value.reversibility?.let { put("reversibility", it) }
            put("state_delta", wire(sortedFacts(value.stateDelta), profile))
            put("status", value.status)
        }
        is ObservedOutcome -> mapOf(
            "execution_ref" to value.executionRef,
            "facts" to wire(sortedFacts(value.facts), profile)
        )
        is TrustGrant -> buildMap {
            put("capability_ref", value.capabilityRef)
            put("granted", value.granted)
            put("id", value.id)
            put("receipt", value.receipt)
            put("revocable", value.revocable)
            put("scope", value.scope)
            put("tier", value.tier.wire())
            value.ttl?.let { put("ttl", it) }
        }
        is Event -> buildMap {
            value.causalId?.let { put("causal_id", it) }
            put("id", value.id)
            value.integrateMode?.let { put("integrate_mode", it) }
            value.payload?.let { put("payload", wire(it, profile)) }
            put("source", value.source)
            value.stateVersion?.let { put("state_version", it) }
            put("t", value.t)
            put("type", value.type)
        }
        is Prediction -> buildMap {
            value.committed?.let { put("committed", it) }
            put("context_ref", value.contextRef)
            put("id", value.id)
            put("states", wire(value.states, profile))
        }
        is Projection -> buildMap {
            put("form_factor", value.formFactor)
            put("id", value.id)
            if (value.interactionRequirements.isNotEmpty()) {
                put("interaction_requirements", value.interactionRequirements.sorted())
            }
            put("state_ref", value.stateRef)
            put("ui_state_ref", value.uiStateRef)
        }
        is WorldStateSnapshot -> buildMap {
            put("facts", wire(sortedFacts(value.facts), profile))
            put("schema", value.schema)
            put("t", value.t)
            if (value.transitions.isNotEmpty()) {
                put(
                    "transitions",
                    value.transitions.map { tx ->
                        mapOf("event_id" to tx.eventId, "from" to tx.from, "to" to tx.to)
                    }
                )
            }
            put("version", value.version)
        }
        is BeliefState -> mapOf(
            "facts" to wire(sortedFacts(value.facts), Profile.STATE),
            "version" to value.version
        )
        is PlanResult.Success -> mapOf("ok" to true, "plan" to wire(value.plan, profile))
        is PlanResult.Failure -> mapOf("error" to errorCode(value.error), "ok" to false)
        is Map<*, *> -> {
            val out = LinkedHashMap<String, Any?>()
            for ((k, v) in value) {
                if (k != null) out[k.toString()] = wire(v, profile)
            }
            out
        }
        is Iterable<*> -> value.map { wire(it, profile) }
        is Enum<*> -> value.name.lowercase(java.util.Locale.ROOT)
        else -> value.toString()
    }

    private fun capability(value: Capability, profile: Profile): Map<String, Any?> = buildMap {
        value.adapter?.let { put("adapter", it) }
        put("cost", wire(value.cost, profile))
        if (value.deps.isNotEmpty()) put("deps", value.deps.sorted())
        put("effects", wire(sortedFacts(value.effects), profile))
        put("id", value.id)
        if (value.inputs.isNotEmpty()) {
            put("inputs", value.inputs.sorted().map { mapOf("name" to it) })
        }
        put("name", value.name)
        put("preconditions", wire(sortedFacts(value.preconditions), profile))
        put("privacy", value.privacy)
        put("reliability", value.reliability)
        put("reversible", value.reversible)
        put("risk", value.risk.wire())
    }

    private fun factFields(fact: Claim, profile: Profile): Map<String, Any?> = buildMap {
        put("confidence", fact.confidence)
        fact.expiresAt?.let { put("expires_at", it) }
        if (profile == Profile.STATE && fact.id.isNotBlank()) put("id", fact.id)
        put("k", fact.k)
        put("observed_at", fact.observedAt)
        put("source", fact.source)
        if (profile == Profile.STATE && fact.supersededBy != null) {
            put("superseded_by", fact.supersededBy)
        }
        put("v", wire(fact.v, profile))
    }

    private fun sortedFacts(facts: List<Claim>): List<Claim> =
        facts.sortedWith(
            compareBy<Claim> { it.k }
                .thenBy { it.observedAt }
                .thenBy { it.id }
                .thenBy { it.source }
        )
}
