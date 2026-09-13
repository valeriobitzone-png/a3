package a3.a3ui.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FreezeTest {
    @Test
    fun AC_023_frozen_modules_have_empty_diff() {
        val proc = ProcessBuilder(
            "git", "diff", "--stat", "--",
            "core/", "broker/", "agent/", "renderers/", "launcher/", "overlay/", "adapters/"
        ).directory(ConformancePaths.repoRoot()).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        val err = proc.errorStream.bufferedReader().readText().trim()
        val code = proc.waitFor()
        assertEquals(0, code, err)
        assertEquals("", out, "frozen tree diff not empty:\n$out")
        val readme = ConformancePaths.readme().readText()
        assertTrue(readme.contains(":a3ui:conformance"))
        assertTrue(readme.contains("AC-001"))
        println("PASS AC-023 freeze")
    }
}
