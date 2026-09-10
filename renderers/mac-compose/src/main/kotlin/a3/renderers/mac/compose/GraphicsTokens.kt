package a3.renderers.mac.compose

import java.io.File

/**
 * Snapshot of a3ui-graphics-v0.1 (colors, elevation, surfaces).
 * Graphics repo is the source of truth; this module consumes the copied JSON.
 */
internal object GraphicsTokens {
    data class Highlight(val widthPx: Int, val startAlpha: Float, val endAlpha: Float)
    data class Shadow(
        val opacity: Float,
        val offsetXPx: Float,
        val offsetYPx: Float,
        val blurPx: Float
    )
    data class KeyShadow(
        val opacity: Float,
        val offsetXPx: Float,
        val offsetYPx: Float,
        val blurPx: Float,
        val azimuthDeg: Float
    )
    data class Snapshot(
        val version: String,
        val blurRadiusPx: Float,
        val fillOpacity: Float,
        val fillColor: Int,
        val vibrancySaturation: Float,
        val inner: Highlight,
        val outer: Highlight,
        val ambient: Shadow,
        val key: KeyShadow,
        val superellipseN: Float,
        val radiusPx: Float,
        val nestedMinPx: Float
    )

    val snapshot: Snapshot by lazy { load() }

    fun load(read: (String) -> String = ::readResource): Snapshot {
        val surfaces = JsonMap.parse(read("surfaces.json"))
        val elevation = JsonMap.parse(read("elevation.json"))
        val glass = surfaces.obj("glass")
        val innerStops = glass.obj("innerHighlight").arr("stops")
        val outerStops = glass.obj("outerHighlight").arr("stops")
        val corner = surfaces.obj("container").obj("corner")
        val nested = surfaces.obj("container").obj("nestedRadius")
        val ambient = elevation.obj("ambientShadow")
        val key = elevation.obj("keyShadow")
        return Snapshot(
            version = surfaces.str("version"),
            blurRadiusPx = glass.num("blurRadiusPx"),
            fillOpacity = glass.num("fillOpacity"),
            fillColor = parseHex(glass.str("fillColor")),
            vibrancySaturation = glass.num("vibrancySaturation"),
            inner = Highlight(
                widthPx = glass.obj("innerHighlight").int("widthPx"),
                startAlpha = stopAlpha(innerStops[0]),
                endAlpha = stopAlpha(innerStops[1])
            ),
            outer = Highlight(
                widthPx = glass.obj("outerHighlight").int("widthPx"),
                startAlpha = stopAlpha(outerStops[0]),
                endAlpha = stopAlpha(outerStops[1])
            ),
            ambient = Shadow(
                opacity = ambient.num("opacity"),
                offsetXPx = ambient.num("offsetXPx"),
                offsetYPx = ambient.num("offsetYPx"),
                blurPx = ambient.num("blurPx")
            ),
            key = KeyShadow(
                opacity = key.num("opacity"),
                offsetXPx = key.num("offsetXPx"),
                offsetYPx = key.num("offsetYPx"),
                blurPx = key.num("blurPx"),
                azimuthDeg = key.num("azimuthDeg")
            ),
            superellipseN = corner.num("superellipseN"),
            radiusPx = corner.num("radiusPx"),
            nestedMinPx = nested.num("minPx")
        )
    }

    private fun stopAlpha(stop: Any?): Float {
        val rgba = (stop as Map<*, *>)["rgba"] as List<*>
        return (rgba[3] as Number).toFloat()
    }

    private fun parseHex(hex: String): Int {
        val h = hex.removePrefix("#")
        return h.toInt(16) or (0xFF shl 24)
    }

    private fun readResource(name: String): String {
        val stream = GraphicsTokens::class.java.classLoader?.getResourceAsStream("a3ui-graphics/$name")
        if (stream != null) return stream.reader().readText()
        val file = File("src/main/resources/a3ui-graphics/$name")
        if (file.exists()) return file.readText()
        error("missing a3ui-graphics token $name")
    }
}

internal class JsonMap(private val map: Map<String, Any?>) {
    fun obj(key: String) = JsonMap(castMap(map[key], key))
    fun str(key: String) = map[key] as String
    fun num(key: String) = (map[key] as Number).toFloat()
    fun int(key: String) = (map[key] as Number).toInt()
    fun arr(key: String) = map[key] as List<*>

    companion object {
        fun parse(text: String): JsonMap = JsonMap(castMap(read(text.trim()), "root"))

        private fun castMap(value: Any?, key: String): Map<String, Any?> {
            @Suppress("UNCHECKED_CAST")
            return value as? Map<String, Any?> ?: error("expected object at $key")
        }

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
