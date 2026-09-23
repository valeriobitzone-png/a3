// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.conformance

import a3.core.truth.TruthClass
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** CS-010: receipt != fact, for every receipt form in `fixtures/receipt-forms.json`. */
class ReceiptFormsTest {
    @Test
    fun CS_010_receipt_is_never_fact() {
        val doc = ConformanceIo.readTree(File(ConformancePaths.fixtures(), "receipt-forms.json"))
        val forms = doc.path("forms")
        assertTrue(forms.size() >= 10, "receipt forms")
        val factClaims = doc.path("rule").path("claimed_fact_rejects").map { it.asText() }
        val observationClaims = doc.path("rule").path("claimed_observation_admits").map { it.asText() }
        for (form in forms) {
            val name = form.path("name").asText()
            val receipt = form.path("receipt")
            for (claimed in factClaims) {
                try {
                    CategoryJudge.judgeReceiptClaim(claimed, receipt)
                    fail("$name: FACT '$claimed' admitted from a receipt")
                } catch (e: ConformanceReject) {
                    assertEquals("CF-001", e.code, name)
                    assertTrue(e.message!!.startsWith("CF-001:"), name)
                }
            }
            for (claimed in observationClaims) {
                val lawful = CategoryJudge.judgeReceiptClaim(claimed, receipt)
                assertEquals(TruthClass.OBSERVATION, lawful.truthClass, name)
            }
        }
        println("PASS CS-010 receipt != fact over ${forms.size()} forms")
    }
}
