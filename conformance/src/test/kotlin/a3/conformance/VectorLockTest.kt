package a3.conformance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorLockTest {
    @Test
    fun CS_001_vector_sha256_matches_expected_and_core_lock() {
        val vectors = ConformancePaths.vectors()
        val manifest = ConformanceIo.readTree(File(vectors, "vector-sha256.json"))
        for (name in ConformanceIo.lockVectors) {
            val file = File(vectors, name)
            assertTrue(file.isFile, "missing vector $name")
            val actual = ConformanceIo.sha256Hex(file)
            val expected = ConformanceIo.expectedSha256.getValue(name)
            assertEquals(expected, actual, name)
            assertEquals(expected, manifest.path(name).asText(), "manifest $name")
        }
        val root = ConformancePaths.repoRoot()
        for ((name, rel) in ConformanceIo.coreLockPaths) {
            val imported = File(vectors, name).readBytes()
            val core = File(root, rel).readBytes()
            assertTrue(imported.contentEquals(core), "import drift $name")
        }
        println("PASS CS-001 vectors=${ConformanceIo.lockVectors.size} sha256")
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
    fun CS_009_frozen_modules_have_empty_diff() {
        val proc = ProcessBuilder(
            "git", "diff", "--stat", "--",
            "core", "broker", "agent", "a3ui", "renderers", "launcher", "adapters"
        ).directory(ConformancePaths.repoRoot()).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        val err = proc.errorStream.bufferedReader().readText().trim()
        val code = proc.waitFor()
        assertEquals(0, code, err)
        assertEquals("", out, "frozen tree diff not empty:\n$out")
        println("PASS CS-009 freeze")
    }
}
