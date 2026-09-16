// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.t12

import a3.core.envelope.contentId
import a3.core.json.CanonicalJson
import a3.core.truth.TruthClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class T12LiveTest {
    private val root = File("../..")
    private val resources = File("src/test/resources")

    private val live: ProbeReport get() = Shared.report

    private object Shared {
        val report: ProbeReport by lazy {
            requireLiveApiKey()
            val run = runProbe()
            dump(run)
            run
        }

        private fun dump(run: ProbeReport) {
            if (System.getenv("T12_DUMP") != "1") {
                require(File("src/test/resources/t12-live-raw.json").exists()) {
                    "missing t12-live-raw.json — generate with T12_DUMP=1"
                }
                return
            }
            val dir = File("src/test/resources")
            dir.mkdirs()
            dir.resolve("t12-live-raw.json").writeText(run.call.text)
            dir.resolve("t12-live-canonical.json").writeText(run.admitted.canonicalJson)
            val meta = mapOf(
                "attempts" to run.call.attempts,
                "candidates_tokens" to run.call.candidatesTokens,
                "checks" to run.checks.associate { it.id to mapOf("detail" to it.detail, "pass" to it.pass) },
                "event_id" to run.admitted.event.id,
                "latency_ms" to run.call.latencyMs,
                "model" to run.call.model,
                "model_version" to run.call.modelVersion,
                "prompt_tokens" to run.call.promptTokens,
                "total_tokens" to run.call.totalTokens,
                "truth_class" to run.admitted.event.data.truth.truthClass
            )
            dir.resolve("t12-live-meta.json").writeText(CanonicalJson.encode(meta))
        }
    }

    companion object {
        /**
         * Live Gemini probe needs [T12_API_ENV]. Missing key = environment assumption → SKIP,
         * not FAIL. Exercise live with:
         * `A3_T12_API_KEY=… ./gradlew :core:t12:test --tests a3.core.t12.T12LiveTest`
         */
        fun requireLiveApiKey() {
            val key = System.getenv(T12_API_ENV)?.trim().orEmpty()
            assumeTrue(
                key.isNotEmpty(),
                "$T12_API_ENV is not set — live Gemini probe skipped (environment assumption). " +
                    "Set the key and run :core:t12:test alone to exercise live."
            )
        }
    }

    @Test
    fun T12_001_receipt_is_observation_not_fact() {
        val check = live.check("T12-001")
        assertTrue(check.pass, check.detail)
        assertTrue(live.admitted.event.data.truth.truthClass != TruthClass.FACT, check.detail)
        assertTrue(allowedTruth(live.admitted.event.data.truth.truthClass), check.detail)
    }

    @Test
    fun T12_002_provenance_present_and_valid() {
        val check = live.check("T12-002")
        assertTrue(check.pass, check.detail)
        assertTrue(live.admitted.event.data.truth.provenance.name.isNotBlank())
    }

    @Test
    fun T12_003_postcondition_not_verified() {
        val check = live.check("T12-003")
        assertTrue(check.pass, check.detail)
        assertFalse(live.admitted.postconditionVerified)
    }

    @Test
    fun T12_004_cloudevents_json_valid() {
        val check = live.check("T12-004")
        assertTrue(check.pass, check.detail)
        assertEquals("1.0", live.admitted.event.specversion)
        assertEquals("application/json", live.admitted.event.datacontenttype)
        assertTrue(live.admitted.canonicalJson.startsWith("{"))
    }

    @Test
    fun T12_005_id_is_sha256_of_jcs_payload() {
        val check = live.check("T12-005")
        assertTrue(check.pass, check.detail)
        val event = live.admitted.event
        assertEquals(contentId(event.data), event.id)
        assertEquals(64, event.id.length)
        assertFalse(event.id.contains("-"))
    }

    @Test
    fun T12_006_four_times_coherent() {
        val check = live.check("T12-006")
        assertTrue(check.pass, check.detail)
        val stamp = live.admitted.event.data.temporal
        assertFalse(stamp.tObserve.isBefore(stamp.tEvent))
        assertFalse(stamp.tAdmit.isBefore(stamp.tObserve))
        assertEquals(stamp.tPresent.toString(), live.admitted.event.time)
    }

    @Test
    fun T12_007_confidence_low_if_present() {
        val check = live.check("T12-007")
        assertTrue(check.pass, check.detail)
    }

    @Test
    fun T12_008_unknown_is_pass() {
        val check = live.check("T12-008")
        assertTrue(check.pass, check.detail)
        assertTrue(allowedTruth(live.admitted.event.data.truth.truthClass))
        assertTrue(live.call.totalTokens >= 0)
        assertTrue(live.call.latencyMs >= 0)
        assertEquals(T12_MODEL, live.call.model)
    }

    @Test
    fun T12_009_freeze_only_t12() {
        // Freeze / ArchUnit do not need the live probe. Model check only when key is present.
        val key = System.getenv(T12_API_ENV)?.trim().orEmpty()
        if (key.isNotEmpty()) {
            assertEquals(T12_MODEL, live.call.model)
        }
        fun diff(vararg paths: String): String {
            val proc = ProcessBuilder("git", "diff", "--stat", "--", *paths)
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals(0, proc.waitFor())
            return out
        }
        val frozen = diff(
            "core/admission", "core/action", "core/json", "core/envelope",
            "core/temporal", "core/truth", "core/confidence",
            "a3ui/", "renderers/", "broker/", "agent/", "showcase/"
        )
        assertTrue(frozen.isBlank(), frozen)
        val status = ProcessBuilder("git", "status", "--porcelain")
            .directory(root).redirectErrorStream(true).start()
        val porcelain = status.inputStream.bufferedReader().readText()
        assertEquals(0, status.waitFor())
        val allowed = listOf(
            "core/t12/",
            "settings.gradle.kts",
            "REVIEW_T12_LIVE.md",
            "docs/",
            "CHANGELOG.md",
            "broker/",
            "launcher/",
            "review-assets/"
        )
        val ignore = listOf(".kotlin/", ".DS_Store")
        for (line in porcelain.lineSequence().filter { it.isNotBlank() }) {
            val path = line.drop(3).trim().removePrefix("?? ").let {
                if (it.contains(" -> ")) it.substringAfter(" -> ") else it
            }
            if (ignore.any { path.startsWith(it) }) continue
            assertTrue(allowed.any { path == it || path.startsWith(it) }, "unexpected path $line")
        }
        val settings = File(root, "settings.gradle.kts").readText()
        assertTrue(settings.contains(":core:t12"))
        val production = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("a3.core.t12")
        noClasses()
            .that().resideInAPackage("a3.core.t12..")
            .should().callMethod(Instant::class.java, "now")
            .check(production)
        noClasses()
            .that().resideInAPackage("a3.core.t12..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "a3.core.action..",
                "a3.core.runtime..",
                "a3.core.world..",
                "a3.a3ui..",
                "a3.renderers..",
                "a3.broker..",
                "a3.agent..",
                "a3.showcase.."
            )
            .check(production)
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("project(\":core:envelope\")"))
        assertTrue(gradle.contains("project(\":core:truth\")"))
        assertTrue(gradle.contains("project(\":core:temporal\")"))
        assertTrue(gradle.contains("project(\":core:confidence\")"))
        assertFalse(gradle.contains("project(\":core:action\")"))
        val main = File("src/main").walkTopDown().filter { it.extension == "kt" }
        for (file in main) {
            val text = file.readText()
            assertFalse(text.contains("Instant.now"), file.path)
            assertFalse(text.contains("UUID.randomUUID"), file.path)
        }
        val review = File(root, "REVIEW_T12_LIVE.md")
        assertTrue(review.exists(), "REVIEW_T12_LIVE.md missing")
        val body = review.readText()
        assertTrue(body.contains("T12-001"))
        assertTrue(body.contains("T12-010"))
        assertTrue(body.contains(T12_MODEL) || body.contains("gemini"))
        assertTrue(body.contains("Receipt"))
        assertTrue(body.contains("A3_T12_API_KEY"))
        assertTrue(resources.resolve("t12-live-raw.json").exists())
        assertTrue(resources.resolve("t12-live-meta.json").exists())
    }

    @Test
    fun T12_010_key_hygiene() {
        requireLiveApiKey()
        val key = System.getenv(T12_API_ENV)?.trim().orEmpty()
        val skipDir = setOf(".git", "build", ".gradle", ".kotlin", "node_modules")
        val skipName = setOf(".env", "local.properties")
        File(root.canonicalPath).walkTopDown()
            .onEnter { it.name !in skipDir }
            .filter { it.isFile && it.name !in skipName }
            .filter { it.extension in setOf("kt", "kts", "md", "json", "properties", "txt", "yml", "yaml", "xml", "gradle") || it.name == "gitignore" }
            .forEach { file ->
                val text = file.readText()
                assertFalse(text.contains(key), "key leaked in ${file.path}")
                val aiza = Regex("AIza[0-9A-Za-z_-]{20,}")
                assertFalse(aiza.containsMatchIn(text), "AIza key pattern in ${file.path}")
            }
        val client = File("src/main/kotlin/a3/core/t12/GeminiClient.kt").readText()
        assertTrue(client.contains("x-goog-api-key"))
        assertFalse(client.contains("?key="))
        assertTrue(client.contains("redact"))
        val getenvHits = File("src/main").walkTopDown().filter { it.extension == "kt" }
            .map { it.readText() }
            .count { it.contains("getenv(\"$T12_API_ENV\")") || it.contains("getenv(T12_API_ENV)") }
        assertTrue(getenvHits >= 1)
    }
}
