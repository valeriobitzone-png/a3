package a3.renderers.android.core.serialize

import a3.a3ui.model.PrefetchStatus
import a3.a3ui.model.EpistemicAxis
import a3.core.json.CanonicalJson as JsonCanonical
import a3.projection.model.CausalLineage
import a3.renderers.android.core.model.CatalogProfile
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RenderedNode
import a3.renderers.android.core.model.RenderedOutput
import a3.renderers.android.core.model.RenderedPrefetch
import a3.renderers.android.core.model.ResolvedToken
import a3.renderers.android.core.model.SemanticGestureAction
import a3.renderers.android.core.model.SemanticGestureActions
import a3.renderers.android.core.model.SemanticHapticEvent
import a3.renderers.android.core.model.SemanticHapticEvents
import a3.renderers.android.core.model.SharedElementPlan
import a3.renderers.android.core.model.SpringParams

/**
 * Renderer typed facade. Engine rules live in :core:json.
 */
object CanonicalJson {
    fun of(value: Any?): String = JsonCanonical.encode(wire(value))

    fun bytes(value: Any?): ByteArray = of(value).toByteArray(Charsets.UTF_8)

    private fun wire(value: Any?): Any? = when (value) {
        null, is Boolean, is Number, is String, is java.time.Instant -> value
        is PrefetchStatus -> value.wire()
        is ColorValue -> mapOf("blue" to value.blue, "green" to value.green, "red" to value.red)
        is CausalLineage -> buildMap {
            put("causal_event_id", value.causalEventId)
            value.forecastId?.let { put("forecast_id", it) }
            value.futureStateId?.let { put("future_state_id", it) }
            put("source_state_version", value.sourceStateVersion)
            put("state_identity", value.stateIdentity)
        }
        is SpringParams -> mapOf(
            "curve" to value.curve,
            "damping" to value.damping,
            "duration_hint" to value.durationHint,
            "stiffness" to value.stiffness
        )
        is SharedElementPlan -> mapOf(
            "mode" to value.mode,
            "shared" to value.shared,
            "spring" to wire(value.spring),
            "to" to value.to
        )
        is SemanticGestureAction -> mapOf(
            "action" to value.action,
            "gesture" to value.gesture,
            "target_node_id" to value.targetNodeId
        )
        is SemanticGestureActions -> mapOf("actions" to wire(value.actions))
        is SemanticHapticEvent -> mapOf(
            "event" to value.event,
            "intensity_hint" to value.intensityHint,
            "pattern" to value.pattern
        )
        is SemanticHapticEvents -> mapOf("events" to wire(value.events))
        is ResolvedToken -> mapOf("color" to wire(value.color), "token" to value.token)
        is RenderedPrefetch -> mapOf(
            "atom_keys" to value.atomKeys,
            "base_state_version" to value.baseStateVersion,
            "candidate_ref" to value.candidateRef,
            "confidence" to value.confidence,
            "status" to value.status.wire(),
            "ttl_ms" to value.ttlMs
        )
        is RenderedNode -> buildMap {
            if (value.accessibleName.isNotEmpty()) put("accessible_name", value.accessibleName)
            value.axis?.takeUnless { it.isDefault() }?.let { put("axis", wire(it)) }
            put("children", wire(value.children))
            put("hint", value.hint)
            put("id", value.id)
            put("role", value.role)
            if (value.stateDescription.isNotEmpty()) put("state_description", value.stateDescription)
            put("text", value.text)
        }
        is EpistemicAxis -> buildMap {
            if (value.action != a3.a3ui.model.EpistemicAction.NA) {
                put("action", value.action.wire())
            }
            if (value.freshness != a3.a3ui.model.EpistemicFreshness.FRESH) {
                put("freshness", value.freshness.wire())
            }
            if (value.status != a3.a3ui.model.EpistemicStatus.BELIEVED) {
                put("status", value.status.wire())
            }
            if (value.support != a3.a3ui.model.EpistemicSupport.HIGH) {
                put("support", value.support.wire())
            }
        }
        is RenderedOutput -> buildMap {
            if (value.catalogProfile != CatalogProfile.V2) {
                put("catalog_profile", value.catalogProfile.wire())
            }
            value.degradation?.let { put("degradation", it) }
            put("density_hint", value.densityHint)
            put("density_scale", value.densityScale)
            put("form_factor", value.formFactor)
            put("gestures", wire(value.gestures))
            put("haptics", wire(value.haptics))
            put("lineage", wire(value.lineage))
            put("nodes", wire(value.nodes))
            value.prefetch?.let { put("prefetch", wire(it)) }
            put("presentation_ref", value.presentationRef)
            put("produced_at", value.producedAt)
            put("projection_ref", value.projectionRef)
            if (value.reducedMotion) put("reduced_motion", true)
            put("resolved_tokens", wire(value.resolvedTokens))
            put("shared_elements", wire(value.sharedElements))
            put("spring", wire(value.spring))
            put("surface_id", value.surfaceId)
        }
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
