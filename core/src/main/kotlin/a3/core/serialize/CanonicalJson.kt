package a3.core.serialize

import a3.core.model.*
import a3.core.planner.PlanResult
import a3.core.planner.PlannerError
import a3.core.prediction.model.Forecast
import a3.core.prediction.model.ForecastCandidate
import a3.core.prediction.model.FutureState
import a3.core.prediction.model.PreparedState
import a3.core.prediction.model.PredictionInvalidation
import a3.core.prediction.model.PredictionPolicy
import a3.core.prediction.model.PredictionStatus
import a3.core.prediction.model.ProjectionCandidate
import a3.core.world.BeliefState
import java.time.Instant
import java.util.Locale
import java.util.TreeMap

/**
 * Canonical JSON for A3 core.
 *
 * Rules (byte-stable):
 * 1. UTF-8, compact, no whitespace, no BOM.
 * 2. Object keys sorted lexicographically (UTF-16 code unit order, Java String.compareTo).
 * 3. Arrays that represent unordered collections are sorted by a stable key
 *    (facts: k, observed_at, id, source). Sequence arrays (plan steps) are ordered by `seq`.
 * 4. Instant → ISO-8601 via Instant.toString() (UTC, Z).
 * 5. Null optional fields are omitted.
 * 6. Numbers: integer-valued values render as integers; otherwise Double.toString().
 * 7. Strings JSON-escaped; booleans true/false.
 * 8. SCHEMA profile omits internal fact fields `id` and `superseded_by`.
 *    STATE profile includes them so replay can round-trip history.
 * 9. Clock and IDs are never generated here; callers inject them.
 * 10. Decision path must not iterate HashMap/HashSet; CanonicalJson uses TreeMap.
 */
object CanonicalJson {
    fun of(value: Any?): String = encode(value, Profile.SCHEMA)

    fun ofState(state: BeliefState): String = encode(state, Profile.STATE)

    fun planResult(result: PlanResult): String = encode(result, Profile.SCHEMA)

    fun bytes(value: Any?): ByteArray = of(value).toByteArray(Charsets.UTF_8)

    fun bytesState(state: BeliefState): ByteArray = ofState(state).toByteArray(Charsets.UTF_8)

    enum class Profile { SCHEMA, STATE }

    internal fun encode(value: Any?, profile: Profile): String = when (value) {
        null -> "null"
        is Boolean -> if (value) "true" else "false"
        is Number -> number(value)
        is String -> string(value)
        is Instant -> string(value.toString())
        is Fact -> obj(factFields(value, profile), profile)
        is Observation -> obj(
            mapOf(
                "execution_ref" to value.executionRef,
                "facts" to sortedFacts(value.facts),
                "id" to value.id,
                "t" to value.t
            ),
            profile
        )
        is Intent -> obj(
            buildMap {
                put("confidence", value.confidence)
                put("expression", value.expression)
                value.goalRef?.let { put("goal_ref", it) }
                put("id", value.id)
                put("modality", value.modality)
                put("source", value.source)
            },
            profile
        )
        is Goal -> obj(
            buildMap {
                if (value.constraints.isNotEmpty()) {
                    put("constraints", value.constraints)
                }
                value.deadline?.let { put("deadline", it) }
                put("desired_state", sortedFacts(value.desiredState))
                put("id", value.id)
                put("intent_ref", value.intentRef)
                put("priority", value.priority)
            },
            profile
        )
        is Constraint -> obj(
            mapOf("key" to value.key, "op" to value.op, "value" to value.value),
            profile
        )
        is Cost -> obj(
            mapOf(
                "energy" to value.energy,
                "money" to value.money,
                "timeMin" to value.timeMin
            ),
            profile
        )
        is Capability -> capability(value, profile)
        is PlanStep -> obj(
            mapOf(
                "capability" to value.capability,
                "reversible" to value.reversible,
                "seq" to value.seq,
                "trust_tier" to value.trustTier.wire()
            ),
            profile
        )
        is ExpectedOutcome -> obj(
            mapOf(
                "facts" to sortedFacts(value.facts),
                "goal_ref" to value.goalRef
            ),
            profile
        )
        is Plan -> obj(
            mapOf(
                "expected_outcome" to value.expectedOutcome,
                "goal_ref" to value.goalRef,
                "id" to value.id,
                "status" to value.status,
                "steps" to value.steps.sortedBy { it.seq }
            ),
            profile
        )
        is Outcome -> obj(
            buildMap {
                put("confidence", value.confidence)
                value.cost?.let { put("cost", it) }
                put("goal_ref", value.goalRef)
                put("id", value.id)
                value.reversibility?.let { put("reversibility", it) }
                put("state_delta", sortedFacts(value.stateDelta))
                put("status", value.status)
            },
            profile
        )
        is ObservedOutcome -> obj(
            mapOf(
                "execution_ref" to value.executionRef,
                "facts" to sortedFacts(value.facts)
            ),
            profile
        )
        is TrustGrant -> obj(
            buildMap {
                put("capability_ref", value.capabilityRef)
                put("granted", value.granted)
                put("id", value.id)
                put("receipt", value.receipt)
                put("revocable", value.revocable)
                put("scope", value.scope)
                put("tier", value.tier.wire())
                value.ttl?.let { put("ttl", it) }
            },
            profile
        )
        is Event -> obj(
            buildMap {
                value.causalId?.let { put("causal_id", it) }
                put("id", value.id)
                value.payload?.let { put("payload", it) }
                put("source", value.source)
                value.stateVersion?.let { put("state_version", it) }
                put("t", value.t)
                put("type", value.type)
            },
            profile
        )
        is Prediction -> obj(
            buildMap {
                value.committed?.let { put("committed", it) }
                put("context_ref", value.contextRef)
                put("id", value.id)
                put("states", value.states)
            },
            profile
        )
        is Projection -> obj(
            buildMap {
                put("form_factor", value.formFactor)
                put("id", value.id)
                if (value.interactionRequirements.isNotEmpty()) {
                    put("interaction_requirements", value.interactionRequirements.sorted())
                }
                put("state_ref", value.stateRef)
                put("ui_state_ref", value.uiStateRef)
            },
            profile
        )
        is Forecast -> obj(
            buildMap {
                put("candidates", value.candidates)
                put("context_ref", value.contextRef)
                put("context_signature", value.contextSignature)
                put("id", value.id)
                value.policyRef?.let { put("policy_ref", it) }
                put("produced_at", value.producedAt)
            },
            profile
        )
        is ForecastCandidate -> obj(
            mapOf(
                "capability_ref" to value.capabilityRef,
                "future_state_id" to value.futureStateId,
                "id" to value.id,
                "rank" to value.rank,
                "score" to value.score
            ),
            profile
        )
        is FutureState -> obj(
            mapOf(
                "capability_ref" to value.capabilityRef,
                "context_ref" to value.contextRef,
                "facts" to sortedFacts(value.facts),
                "id" to value.id,
                "produced_at" to value.producedAt,
                "score" to value.score
            ),
            profile
        )
        is PreparedState -> obj(
            mapOf(
                "context_ref" to value.contextRef,
                "context_signature" to value.contextSignature,
                "expires_at" to value.expiresAt,
                "facts" to sortedFacts(value.facts),
                "forecast_id" to value.forecastId,
                "future_state_id" to value.futureStateId,
                "id" to value.id,
                "prepared_at" to value.preparedAt,
                "status" to value.status.wire(),
                "ttl_seconds" to value.ttlSeconds
            ),
            profile
        )
        is PredictionPolicy -> obj(
            mapOf(
                "id" to value.id,
                "max_candidates" to value.maxCandidates,
                "min_score" to value.minScore,
                "ttl_seconds" to value.ttlSeconds
            ),
            profile
        )
        is PredictionStatus -> string(value.wire())
        is ProjectionCandidate -> obj(
            mapOf(
                "context_ref" to value.contextRef,
                "future_state_ref" to value.futureStateRef,
                "id" to value.id,
                "ui_state_hint" to value.uiStateHint
            ),
            profile
        )
        is PredictionInvalidation -> obj(
            mapOf(
                "context_ref" to value.contextRef,
                "context_signature" to value.contextSignature,
                "id" to value.id,
                "prepared_ids" to value.preparedIds.sorted(),
                "reason" to value.reason,
                "t" to value.t
            ),
            profile
        )
        is WorldStateSnapshot -> obj(
            buildMap {
                put("facts", sortedFacts(value.facts))
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
            },
            profile
        )
        is BeliefState -> obj(
            mapOf(
                "facts" to sortedFacts(value.facts),
                "version" to value.version
            ),
            Profile.STATE
        )
        is PlanResult.Success -> obj(mapOf("ok" to true, "plan" to value.plan), profile)
        is PlanResult.Failure -> obj(mapOf("error" to errorCode(value.error), "ok" to false), profile)
        is Enum<*> -> string(value.name.lowercase(Locale.ROOT))
        is Map<*, *> -> {
            val sorted = TreeMap<String, Any?>()
            for ((k, v) in value) {
                if (k != null) sorted[k.toString()] = v
            }
            obj(sorted, profile)
        }
        is Iterable<*> -> arr(value.toList(), profile)
        else -> string(value.toString())
    }

    private fun capability(value: Capability, profile: Profile): String = obj(
        buildMap {
            value.adapter?.let { put("adapter", it) }
            put("cost", value.cost)
            if (value.deps.isNotEmpty()) put("deps", value.deps.sorted())
            put("effects", sortedFacts(value.effects))
            put("id", value.id)
            if (value.inputs.isNotEmpty()) {
                put("inputs", value.inputs.sorted().map { mapOf("name" to it) })
            }
            put("name", value.name)
            put("preconditions", sortedFacts(value.preconditions))
            put("privacy", value.privacy)
            put("reliability", value.reliability)
            put("reversible", value.reversible)
            put("risk", value.risk.wire())
        },
        profile
    )

    private fun factFields(fact: Fact, profile: Profile): Map<String, Any?> {
        val fields = TreeMap<String, Any?>()
        fields["confidence"] = fact.confidence
        fact.expiresAt?.let { fields["expires_at"] = it }
        if (profile == Profile.STATE && fact.id.isNotBlank()) fields["id"] = fact.id
        fields["k"] = fact.k
        fields["observed_at"] = fact.observedAt
        fields["source"] = fact.source
        if (profile == Profile.STATE && fact.supersededBy != null) {
            fields["superseded_by"] = fact.supersededBy
        }
        fields["v"] = fact.v
        return fields
    }

    private fun sortedFacts(facts: List<Fact>): List<Fact> =
        facts.sortedWith(
            compareBy<Fact> { it.k }
                .thenBy { it.observedAt }
                .thenBy { it.id }
                .thenBy { it.source }
        )

    fun errorCode(error: PlannerError): String = when (error) {
        PlannerError.NoPlanFound -> "NO_PLAN_FOUND"
        PlannerError.ConstraintConflict -> "CONSTRAINT_CONFLICT"
        PlannerError.MissingCapability -> "MISSING_CAPABILITY"
        PlannerError.MissingPrecondition -> "MISSING_PRECONDITION"
        PlannerError.TrustBlocked -> "TRUST_BLOCKED"
        PlannerError.StaleState -> "STALE_STATE"
    }

    private fun obj(fields: Map<String, Any?>, profile: Profile): String {
        val sorted = TreeMap<String, Any?>()
        for ((k, v) in fields) {
            if (v != null) sorted[k] = v
        }
        return buildString {
            append('{')
            var first = true
            for ((k, v) in sorted) {
                if (!first) append(',')
                first = false
                append(string(k))
                append(':')
                append(encode(v, profile))
            }
            append('}')
        }
    }

    private fun arr(items: List<*>, profile: Profile): String = buildString {
        append('[')
        items.forEachIndexed { i, item ->
            if (i > 0) append(',')
            append(encode(item, profile))
        }
        append(']')
    }

    private fun string(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else append(ch)
            }
        }
        append('"')
    }

    private fun number(value: Number): String {
        val d = value.toDouble()
        if (d.isNaN() || d.isInfinite()) {
            throw IllegalArgumentException("canonical JSON cannot encode $value")
        }
        return if (value is Int || value is Long || value is Short || value is Byte ||
            d == d.toLong().toDouble()
        ) {
            d.toLong().toString()
        } else {
            d.toString()
        }
    }
}
