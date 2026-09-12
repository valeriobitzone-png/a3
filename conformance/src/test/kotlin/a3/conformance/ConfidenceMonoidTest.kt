package a3.conformance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfidenceMonoidTest {
    @Test
    fun CS_006_min_top_is_commutative_and_associative() {
        val fixture = ConformanceIo.readTree(File(ConformancePaths.vectorsV2(), "confidence-vectors.json"))
        val scores = listOf(
            fixture.path("mapping").path("media_default_fact").path("score").asDouble(),
            fixture.path("fixture").path("score").asDouble(),
            fixture.path("mapping").path("unknown_zero").path("score").asDouble(),
            fixture.path("mapping").path("alta_unit_fact").path("score").asDouble(),
            fixture.path("mapping").path("bassa_hypothesis").path("score").asDouble(),
            fixture.path("zero_not_compensated").path("score").asDouble()
        )
        val top = ConfidenceMonoid.TOP
        assertEquals(1.0, top)
        for (a in scores) {
            assertEquals(a, ConfidenceMonoid.combine(a, top), "identity $a")
            assertEquals(a, ConfidenceMonoid.combine(top, a), "identity-r $a")
        }
        for (a in scores) {
            for (b in scores) {
                assertEquals(
                    ConfidenceMonoid.combine(a, b),
                    ConfidenceMonoid.combine(b, a),
                    "commute $a $b"
                )
                for (c in scores) {
                    val left = ConfidenceMonoid.combine(ConfidenceMonoid.combine(a, b), c)
                    val right = ConfidenceMonoid.combine(a, ConfidenceMonoid.combine(b, c))
                    assertEquals(left, right, "assoc $a $b $c")
                }
            }
        }
        println("PASS CS-006 scores=$scores")
    }
}
