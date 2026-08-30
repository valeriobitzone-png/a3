package a3.core.json

import java.time.Instant
import java.util.Locale
import java.util.TreeMap

/**
 * Blind canonical JSON engine. Primitives, maps, lists, Instant only.
 * Layer types stay in owner-module facades.
 */
data class CanonicalObject(
    val fields: Map<String, Any?>,
    val omitNulls: Boolean = true
)

object CanonicalJson {
    fun encode(value: Any?): String = encodeAny(value)

    fun bytes(value: Any?): ByteArray = encode(value).toByteArray(Charsets.UTF_8)

    private fun encodeAny(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> if (value) "true" else "false"
        is Number -> number(value)
        is String -> string(value)
        is Instant -> string(value.toString())
        is CanonicalObject -> obj(value.fields, value.omitNulls)
        is Map<*, *> -> {
            val sorted = TreeMap<String, Any?>()
            for ((k, v) in value) {
                if (k != null) sorted[k.toString()] = v
            }
            obj(sorted, omitNulls = true)
        }
        is Iterable<*> -> arr(value.toList())
        is Enum<*> -> string(value.name.lowercase(Locale.ROOT))
        else -> string(value.toString())
    }

    private fun obj(fields: Map<String, Any?>, omitNulls: Boolean): String {
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
                append(encodeAny(v))
            }
            append('}')
        }
    }

    private fun arr(items: List<*>): String = buildString {
        append('[')
        items.forEachIndexed { i, item ->
            if (i > 0) append(',')
            append(encodeAny(item))
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
