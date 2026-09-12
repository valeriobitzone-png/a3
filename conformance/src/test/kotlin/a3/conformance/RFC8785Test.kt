package a3.conformance

import a3.core.envelope.Jcs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class RFC8785Test {
    @Test
    fun CS_003_appendix_a_is_byte_identical() {
        val vectors = ConformancePaths.vectors()
        val input = File(vectors, "rfc8785-input.json").readText()
        val actual = Jcs.ofJson(input)
        val lock = File(vectors, "envelope-rfc8785.json").readText()
        assertEquals(lock, actual)
        assertEquals(
            ConformanceIo.expectedSha256.getValue("envelope-rfc8785.json"),
            ConformanceIo.sha256Hex(actual.toByteArray(Charsets.UTF_8))
        )
        println("PASS CS-003 RFC8785 bytes=${actual.length}")
    }
}
