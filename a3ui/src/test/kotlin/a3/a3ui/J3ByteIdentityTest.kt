package a3.a3ui

import a3.a3ui.model.Binding
import a3.a3ui.model.MotionSpec
import a3.a3ui.model.Node
import a3.a3ui.serialize.CanonicalJson
import a3.projection.model.CausalLineage
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class J3ByteIdentityTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    @Test
    fun J3_bytes_match_corpus_fixed_before_move() {
        val node = Node("root", "stack", listOf(Node("t", "text")))
        val binding = Binding("train.price", "t", "placeholder")
        val motion = MotionSpec(400.0, 20.0, "standard", 180)
        val got = mapOf(
            "null" to CanonicalJson.of(null),
            "true" to CanonicalJson.of(true),
            "false" to CanonicalJson.of(false),
            "str" to CanonicalJson.of("a\"b\\c\n"),
            "int" to CanonicalJson.of(1),
            "double_int" to CanonicalJson.of(1.0),
            "double" to CanonicalJson.of(1.5),
            "instant" to CanonicalJson.of(t),
            "map" to CanonicalJson.of(mapOf("b" to 1, "a" to 2, "n" to null)),
            "list" to CanonicalJson.of(listOf(3, 1, 2)),
            "empty_obj" to CanonicalJson.of(emptyMap<String, Any?>()),
            "empty_arr" to CanonicalJson.of(emptyList<Any?>()),
            "node" to CanonicalJson.of(node),
            "binding" to CanonicalJson.of(binding),
            "motion" to CanonicalJson.of(motion),
            "lineage" to CanonicalJson.of(CausalLineage("ctx", 1, "ev1"))
        )
        assertEquals(golden(), got)
    }

    private fun golden(): Map<String, String> {
        val text = javaClass.classLoader.getResource("j3-pre-move.txt")!!.readText().trim()
        return text.lines().associate {
            val i = it.indexOf('=')
            it.substring(0, i) to it.substring(i + 1)
        }
    }
}
