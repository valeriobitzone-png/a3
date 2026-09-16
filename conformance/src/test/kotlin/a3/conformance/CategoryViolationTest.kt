// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.conformance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CategoryViolationTest {
    @Test
    fun CS_002_each_category_fixture_is_explicit_reject() {
        for ((name, code) in ConformanceIo.categoryFixtures) {
            val file = File(ConformancePaths.fixtures(), name)
            assertTrue(file.isFile, "missing fixture $name")
            val thrown = assertFailsWith<ConformanceReject>(name) {
                CategoryJudge.judgeFile(file)
            }
            assertEquals(code, thrown.code, name)
            assertTrue(thrown.message!!.startsWith("$code:"), name)
            assertTrue(thrown.message!!.length > code.length + 2, "silent reject $name")
            println("PASS CS-002 $code $name → ${thrown.message}")
        }
    }
}
