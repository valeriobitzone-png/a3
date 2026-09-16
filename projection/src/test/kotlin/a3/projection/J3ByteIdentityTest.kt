// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.projection

import a3.projection.model.CausalLineage
import a3.projection.model.Density
import a3.projection.model.FormFactorHints
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.projection.serialize.CanonicalJson
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class J3ByteIdentityTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    @Test
    fun J3_bytes_match_corpus_fixed_before_move() {
        val lineage = CausalLineage("ctx", 1, "ev1")
        val atomNull = PresentationAtom("confirm", "ticket.owned", null, 97)
        val atomVal = PresentationAtom("passenger", "train.passenger", "Ada", 40)
        val presentation = PresentationState("ps1", 1, t, listOf(atomVal, atomNull), lineage)
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
            "atom_null" to CanonicalJson.of(atomNull),
            "atom_val" to CanonicalJson.of(atomVal),
            "hints" to CanonicalJson.of(FormFactorHints("phone", Density.COMFORTABLE)),
            "lineage" to CanonicalJson.of(lineage),
            "presentation" to CanonicalJson.of(presentation)
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
