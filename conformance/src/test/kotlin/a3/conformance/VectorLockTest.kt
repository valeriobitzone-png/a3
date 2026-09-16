// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.conformance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorLockTest {
    @Test
    fun CS_001_vector_sha256_matches_expected_and_core_lock() {
        verifyDir(ConformancePaths.vectorsV1(), ConformanceIo.expectedSha256V1, core = false)
        verifyDir(ConformancePaths.vectorsV2(), ConformanceIo.expectedSha256V2, core = true)
        println("PASS CS-001 v1+v2 sha256")
    }

    private fun verifyDir(dir: File, expected: Map<String, String>, core: Boolean) {
        val manifest = ConformanceIo.readTree(File(dir, "vector-sha256.json"))
        for (name in ConformanceIo.lockVectors) {
            val file = File(dir, name)
            assertTrue(file.isFile, "missing ${dir.name}/$name")
            val actual = ConformanceIo.sha256Hex(file)
            val want = expected.getValue(name)
            assertEquals(want, actual, "${dir.name}/$name")
            assertEquals(want, manifest.path(name).asText(), "manifest ${dir.name}/$name")
        }
        if (core) {
            val root = ConformancePaths.repoRoot()
            for ((name, rel) in ConformanceIo.coreLockPathsV2) {
                val imported = File(dir, name).readBytes()
                val coreFile = File(root, rel).readBytes()
                assertTrue(imported.contentEquals(coreFile), "import drift $name")
            }
        }
    }

    @Test
    fun CS_008_readme_has_run_add_impl_core_and_report() {
        val text = ConformancePaths.readme().readText()
        assertTrue(text.contains("./gradlew :conformance:test"), "missing Kotlin run command")
        assertTrue(text.contains("test_conformance.py"), "missing Python run command")
        assertTrue(text.contains("Kotlin"), "missing Kotlin impl")
        assertTrue(text.contains("TypeScript") || text.contains("TS"), "missing TS impl")
        assertTrue(text.contains("Go"), "missing Go impl")
        assertTrue(text.contains("Python"), "missing Python impl")
        assertTrue(text.contains("CORE"), "missing CORE")
        assertTrue(text.contains("EXTENSION"), "missing EXTENSION")
        assertTrue(text.contains("report"), "missing report")
        println("PASS CS-008 README")
    }

    @Test
    fun CS_009_and_PV_010_frozen_modules_have_empty_diff() {
        val proc = ProcessBuilder(
            "git", "diff", "--stat", "--",
            "core/admission", "core/action", "core/json", "core/temporal",
            "a3ui", "renderers", "broker", "agent", "launcher", "overlay", "showcase"
        ).directory(ConformancePaths.repoRoot()).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        val err = proc.errorStream.bufferedReader().readText().trim()
        val code = proc.waitFor()
        assertEquals(0, code, err)
        assertEquals("", out, "frozen tree diff not empty:\n$out")
        println("PASS CS-009/PV-010 freeze")
    }
}
