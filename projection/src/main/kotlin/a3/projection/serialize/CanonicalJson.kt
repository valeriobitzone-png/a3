// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.projection.serialize

import a3.core.json.CanonicalJson as JsonCanonical
import a3.core.json.CanonicalObject
import a3.projection.model.CandidateStatus
import a3.projection.model.CausalLineage
import a3.projection.model.Density
import a3.projection.model.FormFactorHints
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.projection.model.Projection
import a3.projection.model.ProjectionCandidate
import a3.projection.model.ProjectionStatus
import a3.projection.model.RenderedOutput

/**
 * Projection typed facade. Engine rules live in :core:json.
 */
object CanonicalJson {
    fun of(value: Any?): String = JsonCanonical.encode(wire(value))

    fun bytes(value: Any?): ByteArray = of(value).toByteArray(Charsets.UTF_8)

    private fun wire(value: Any?): Any? = when (value) {
        null, is Boolean, is Number, is String, is java.time.Instant -> value
        is Density -> value.wire()
        is ProjectionStatus -> value.wire()
        is CandidateStatus -> value.wire()
        is CausalLineage -> buildMap {
            put("causal_event_id", value.causalEventId)
            value.forecastId?.let { put("forecast_id", it) }
            value.futureStateId?.let { put("future_state_id", it) }
            put("source_state_version", value.sourceStateVersion)
            put("state_identity", value.stateIdentity)
        }
        is FormFactorHints -> mapOf(
            "density" to value.density.wire(),
            "form_factor" to value.formFactor
        )
        is PresentationAtom -> CanonicalObject(
            mapOf(
                "k" to value.k,
                "meaning" to value.meaning,
                "priority" to value.priority,
                "v" to wire(value.v)
            ),
            omitNulls = false
        )
        is PresentationState -> mapOf(
            "atoms" to wire(value.atoms),
            "id" to value.id,
            "lineage" to wire(value.lineage),
            "produced_at" to value.producedAt,
            "source_state_version" to value.sourceStateVersion
        )
        is Projection -> buildMap {
            put("context_ref", value.contextRef)
            put("form_factor_hints", wire(value.formFactorHints))
            put("id", value.id)
            if (value.interactionRequirements.isNotEmpty()) {
                put("interaction_requirements", value.interactionRequirements.sorted())
            }
            put("lineage", wire(value.lineage))
            put("presentation_id", value.presentationId)
            put("status", value.status.wire())
        }
        is ProjectionCandidate -> buildMap {
            put("base_state_version", value.baseStateVersion)
            put("context_ref", value.contextRef)
            value.expiresAt?.let { put("expires_at", it) }
            put("forecast_id", value.forecastId)
            put("future_state_id", value.futureStateId)
            put("id", value.id)
            put("lineage", wire(value.lineage))
            put("presentation", wire(value.presentation))
            put("priority", value.priority)
            put("rank", value.rank)
            put("status", value.status.wire())
        }
        is RenderedOutput -> mapOf("body" to value.body, "kind" to value.kind)
        is Map<*, *> -> {
            val out = LinkedHashMap<String, Any?>()
            for ((k, v) in value) {
                if (k != null) out[k.toString()] = wire(v)
            }
            out
        }
        is Iterable<*> -> value.map { wire(it) }
        is Enum<*> -> value.name.lowercase(java.util.Locale.ROOT)
        else -> value.toString()
    }
}
