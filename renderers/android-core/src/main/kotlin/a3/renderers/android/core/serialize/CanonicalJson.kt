package a3.renderers.android.core.serialize

import a3.a3ui.model.PrefetchStatus
import a3.projection.model.CausalLineage
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RenderedOutput
import a3.renderers.android.core.model.RenderedPrefetch
import a3.renderers.android.core.model.ResolvedToken
import a3.renderers.android.core.model.SemanticGestureAction
import a3.renderers.android.core.model.SemanticGestureActions
import a3.renderers.android.core.model.SemanticHapticEvent
import a3.renderers.android.core.model.SemanticHapticEvents
import a3.renderers.android.core.model.SharedElementPlan
import a3.renderers.android.core.model.SpringParams
import java.time.Instant
import java.util.Locale
import java.util.TreeMap

object CanonicalJson {
    fun of(value: Any?): String = encode(value)

    fun bytes(value: Any?): ByteArray = of(value).toByteArray(Charsets.UTF_8)

    private fun encode(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> if (value) "true" else "false"
        is Number -> number(value)
        is String -> string(value)
        is Instant -> string(value.toString())
        is PrefetchStatus -> string(value.wire())
        is ColorValue -> obj(
            mapOf("blue" to value.blue, "green" to value.green, "red" to value.red)
        )
        is CausalLineage -> obj(
            buildMap {
                put("causal_event_id", value.causalEventId)
                value.forecastId?.let { put("forecast_id", it) }
                value.futureStateId?.let { put("future_state_id", it) }
                put("source_state_version", value.sourceStateVersion)
                put("state_identity", value.stateIdentity)
            }
        )
        is SpringParams -> obj(
            mapOf(
                "curve" to value.curve,
                "damping" to value.damping,
                "duration_hint" to value.durationHint,
                "stiffness" to value.stiffness
            )
        )
        is SharedElementPlan -> obj(
            mapOf(
                "mode" to value.mode,
                "shared" to value.shared,
                "spring" to value.spring,
                "to" to value.to
            )
        )
        is SemanticGestureAction -> obj(
            mapOf("action" to value.action, "gesture" to value.gesture)
        )
        is SemanticGestureActions -> obj(mapOf("actions" to value.actions))
        is SemanticHapticEvent -> obj(
            mapOf(
                "event" to value.event,
                "intensity_hint" to value.intensityHint,
                "pattern" to value.pattern
            )
        )
        is SemanticHapticEvents -> obj(mapOf("events" to value.events))
        is ResolvedToken -> obj(mapOf("color" to value.color, "token" to value.token))
        is RenderedPrefetch -> obj(
            mapOf(
                "base_state_version" to value.baseStateVersion,
                "candidate_ref" to value.candidateRef,
                "confidence" to value.confidence,
                "status" to value.status.wire(),
                "ttl_ms" to value.ttlMs
            )
        )
        is RenderedOutput -> obj(
            buildMap {
                put("density_hint", value.densityHint)
                put("density_scale", value.densityScale)
                put("form_factor", value.formFactor)
                put("gestures", value.gestures)
                put("haptics", value.haptics)
                put("lineage", value.lineage)
                value.prefetch?.let { put("prefetch", it) }
                put("presentation_ref", value.presentationRef)
                put("produced_at", value.producedAt)
                put("projection_ref", value.projectionRef)
                put("resolved_tokens", value.resolvedTokens)
                put("shared_elements", value.sharedElements)
                put("spring", value.spring)
                put("surface_id", value.surfaceId)
            }
        )
        is Map<*, *> -> {
            val sorted = TreeMap<String, Any?>()
            for ((k, v) in value) {
                if (k != null) sorted[k.toString()] = v
            }
            obj(sorted)
        }
        is Iterable<*> -> arr(value.toList())
        is Enum<*> -> string(value.name.lowercase(Locale.ROOT))
        else -> string(value.toString())
    }

    private fun obj(fields: Map<String, Any?>, omitNulls: Boolean = true): String {
        val sorted = TreeMap<String, Any?>()
        for ((k, v) in fields) {
            if (v != null || !omitNulls) sorted[k] = v
        }
        return buildString {
            append('{')
            var first = true
            for ((k, v) in sorted) {
                if (!first) append(',')
                first = false
                append(string(k))
                append(':')
                append(encode(v))
            }
            append('}')
        }
    }

    private fun arr(items: List<*>): String = buildString {
        append('[')
        items.forEachIndexed { i, item ->
            if (i > 0) append(',')
            append(encode(item))
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
