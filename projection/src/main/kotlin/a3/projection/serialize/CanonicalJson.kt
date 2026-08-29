package a3.projection.serialize

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
        is Density -> string(value.wire())
        is ProjectionStatus -> string(value.wire())
        is CandidateStatus -> string(value.wire())
        is CausalLineage -> obj(
            buildMap {
                put("causal_event_id", value.causalEventId)
                value.forecastId?.let { put("forecast_id", it) }
                value.futureStateId?.let { put("future_state_id", it) }
                put("source_state_version", value.sourceStateVersion)
                put("state_identity", value.stateIdentity)
            }
        )
        is FormFactorHints -> obj(
            mapOf(
                "density" to value.density.wire(),
                "form_factor" to value.formFactor
            )
        )
        is PresentationAtom -> obj(
            mapOf(
                "k" to value.k,
                "meaning" to value.meaning,
                "priority" to value.priority,
                "v" to value.v
            ),
            omitNulls = false
        )
        is PresentationState -> obj(
            mapOf(
                "atoms" to value.atoms,
                "id" to value.id,
                "lineage" to value.lineage,
                "produced_at" to value.producedAt,
                "source_state_version" to value.sourceStateVersion
            )
        )
        is Projection -> obj(
            buildMap {
                put("context_ref", value.contextRef)
                put("form_factor_hints", value.formFactorHints)
                put("id", value.id)
                if (value.interactionRequirements.isNotEmpty()) {
                    put("interaction_requirements", value.interactionRequirements.sorted())
                }
                put("lineage", value.lineage)
                put("presentation_id", value.presentationId)
                put("status", value.status.wire())
            }
        )
        is ProjectionCandidate -> obj(
            buildMap {
                put("base_state_version", value.baseStateVersion)
                put("context_ref", value.contextRef)
                value.expiresAt?.let { put("expires_at", it) }
                put("forecast_id", value.forecastId)
                put("future_state_id", value.futureStateId)
                put("id", value.id)
                put("lineage", value.lineage)
                put("presentation", value.presentation)
                put("priority", value.priority)
                put("rank", value.rank)
                put("status", value.status.wire())
            }
        )
        is RenderedOutput -> obj(
            mapOf("body" to value.body, "kind" to value.kind)
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
