package a3.a3ui.serialize

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.GestureBinding
import a3.a3ui.model.GestureMap
import a3.a3ui.model.HapticEvent
import a3.a3ui.model.HapticMap
import a3.a3ui.model.MorphSpec
import a3.a3ui.model.MotionSpec
import a3.a3ui.model.Node
import a3.a3ui.model.PrefetchSpec
import a3.a3ui.model.PrefetchStatus
import a3.core.json.CanonicalJson as JsonCanonical
import a3.projection.model.CausalLineage

/**
 * A3UI typed facade. Engine rules live in :core:json.
 */
object CanonicalJson {
    fun of(value: Any?): String = JsonCanonical.encode(wire(value))

    fun bytes(value: Any?): ByteArray = of(value).toByteArray(Charsets.UTF_8)

    private fun wire(value: Any?): Any? = when (value) {
        null, is Boolean, is Number, is String, is java.time.Instant -> value
        is PrefetchStatus -> value.wire()
        is CausalLineage -> buildMap {
            put("causal_event_id", value.causalEventId)
            value.forecastId?.let { put("forecast_id", it) }
            value.futureStateId?.let { put("future_state_id", it) }
            put("source_state_version", value.sourceStateVersion)
            put("state_identity", value.stateIdentity)
        }
        is MotionSpec -> mapOf(
            "curve" to value.curve,
            "damping" to value.damping,
            "duration_hint" to value.durationHint,
            "stiffness" to value.stiffness
        )
        is MorphSpec -> mapOf(
            "mode" to value.mode,
            "motion" to wire(value.motion),
            "shared" to value.shared,
            "to" to value.to
        )
        is GestureBinding -> mapOf(
            "action" to value.action,
            "gesture" to value.gesture,
            "target_node_id" to value.targetNodeId
        )
        is GestureMap -> mapOf("bindings" to wire(value.bindings))
        is EpistemicAxis -> wireAxis(value)
        is Node -> buildMap {
            wireAxis(value.resolvedAxis())?.let { put("axis", it) }
            put("children", wire(value.children))
            put("id", value.id)
            put("role", value.role)
        }
        is Binding -> mapOf(
            "atom_key" to value.atomKey,
            "node_id" to value.nodeId,
            "role" to value.role
        )
        is HapticEvent -> mapOf(
            "event" to value.event,
            "intensity_hint" to value.intensityHint,
            "pattern" to value.pattern
        )
        is HapticMap -> mapOf("events" to wire(value.events))
        is PrefetchSpec -> mapOf(
            "atom_keys" to value.atomKeys,
            "base_state_version" to value.baseStateVersion,
            "candidate_ref" to value.candidateRef,
            "confidence" to value.confidence,
            "status" to value.status.wire(),
            "ttl_ms" to value.ttlMs
        )
        is A3UISurface -> buildMap {
            put("bindings", wire(value.bindings))
            put("color_tokens", value.colorTokens)
            put("density_hint", value.densityHint)
            put("gestures", wire(value.gestures))
            put("haptics", wire(value.haptics))
            put("id", value.id)
            put("lineage", wire(value.lineage))
            put("morph", wire(value.morph))
            put("motion", wire(value.motion))
            put("nodes", wire(value.nodes))
            value.prefetch?.let { put("prefetch", wire(it)) }
            put("presentation_ref", value.presentationRef)
            put("produced_at", value.producedAt)
            put("projection_ref", value.projectionRef)
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

    private fun wireAxis(value: EpistemicAxis): Map<String, Any?>? {
        if (value.isDefault()) return null
        return buildMap {
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
    }
}
