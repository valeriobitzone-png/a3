package a3.core

import a3.core.model.Fact
import a3.core.model.Goal
import a3.core.model.Intent
import a3.core.serialize.CanonicalJson
import a3.core.world.BeliefState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class J3ByteIdentityTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    @Test
    fun J3_bytes_match_corpus_fixed_before_move() {
        val fact = Fact("ticket.owned", true, 0.9, "train", t, t.plusSeconds(60), id = "f1")
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
            "fact" to CanonicalJson.of(fact),
            "goal" to CanonicalJson.of(Goal("g", "i", listOf(fact), priority = 0.5)),
            "intent" to CanonicalJson.of(Intent("int", "buy ticket", "text", "explicit", 0.8, "g")),
            "belief" to CanonicalJson.ofState(BeliefState(1, listOf(fact)))
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
