package a3.showcase

internal class ShowcaseJson(private val map: Map<String, Any?>) {
    fun obj(key: String) = ShowcaseJson(cast(map[key], key))
    fun str(key: String) = map[key] as String
    fun num(key: String) = (map[key] as Number).toFloat()
    fun int(key: String) = (map[key] as Number).toInt()
    fun intOr(key: String, fallback: Int) = (map[key] as? Number)?.toInt() ?: fallback
    fun bool(key: String) = map[key] as Boolean
    fun arr(key: String): List<Any?> = map[key] as? List<Any?> ?: error("expected array at $key")

    companion object {
        fun parse(text: String): ShowcaseJson = ShowcaseJson(cast(read(text.trim()), "root"))

        @Suppress("UNCHECKED_CAST")
        private fun cast(value: Any?, key: String): Map<String, Any?> =
            value as? Map<String, Any?> ?: error("expected object at $key")

        private fun read(text: String): Any? {
            val p = Parser(text)
            val value = p.value()
            p.skip()
            return value
        }
    }

    private class Parser(private val s: String) {
        var i = 0

        fun skip() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skip()
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't', 'f' -> bool()
                'n' -> {
                    i += 4
                    null
                }
                else -> num()
            }
        }

        private fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            skip()
            if (s[i] == '}') {
                i++
                return out
            }
            while (true) {
                skip()
                val key = str()
                skip()
                if (s[i] != ':') error("expected : at $i")
                i++
                out[key] = value()
                skip()
                if (s[i] == '}') {
                    i++
                    return out
                }
                if (s[i] != ',') error("expected , at $i")
                i++
            }
        }

        private fun arr(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            skip()
            if (s[i] == ']') {
                i++
                return out
            }
            while (true) {
                out += value()
                skip()
                if (s[i] == ']') {
                    i++
                    return out
                }
                if (s[i] != ',') error("expected , at $i")
                i++
            }
        }

        private fun str(): String {
            if (s[i] != '"') error("expected string at $i")
            i++
            val b = StringBuilder()
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    b.append(s[i])
                } else {
                    b.append(s[i])
                }
                i++
            }
            i++
            return b.toString()
        }

        private fun bool(): Boolean {
            if (s.startsWith("true", i)) {
                i += 4
                return true
            }
            i += 5
            return false
        }

        private fun num(): Number {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+')) i++
            val slice = s.substring(start, i)
            return if (slice.contains('.') || slice.contains('e') || slice.contains('E')) slice.toDouble() else slice.toLong()
        }
    }
}
