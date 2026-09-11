package a3.core.confidence

import a3.core.json.CanonicalJson
import a3.core.temporal.TemporalStamp
import a3.core.truth.Provenance
import a3.core.truth.TruthBearer
import a3.core.truth.TruthClass
import a3.core.truth.VerificationEnvironment
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ConfidenceTest {
    private val t0 = Instant.parse("2026-08-27T08:00:00Z")
    private val present = Instant.parse("2026-08-27T11:00:00Z")
    private val root = File("../..")
    private val vectors = File("src/test/resources/confidence-vectors.json")
    private val halfLife = RecencyLaw.DEFAULT.halfLifeSeconds

    private fun stamp(
        tEvent: Instant = t0,
        tObserve: Instant = t0.plusSeconds(1),
        tAdmit: Instant = t0.plusSeconds(2),
        tPresent: Instant = present
    ) = TemporalStamp(tEvent, tObserve, tAdmit, tPresent)

    private fun ones(
        sourceReliability: Double = 1.0,
        evidenceStrength: Double = 1.0,
        recency: Double = 1.0,
        corroboration: Double = 1.0,
        verification: Double = 1.0
    ) = ConfidenceVector(
        sourceReliability,
        evidenceStrength,
        recency,
        corroboration,
        verification
    )

    private fun lock(actual: Map<String, Any?>): String {
        val json = CanonicalJson.encode(actual)
        if (System.getenv("CF_DUMP") == "1") {
            vectors.parentFile.mkdirs()
            vectors.writeText(json)
        }
        require(vectors.exists()) { "missing confidence-vectors.json — generate with CF_DUMP=1" }
        return json
    }

    @Test
    fun CF_001_vector_is_complete_or_explicit_reject() {
        val vector = ones()
        val score = aggregate(vector, AggregationWeights.DEFAULT)
        assertEquals(0.6, score)
        assertEquals(5, vector.asList().size)
        assertEquals(CONFIDENCE_DIMENSIONS.toSet(), vector.toCanonical().keys)
        for (name in CONFIDENCE_DIMENSIONS) {
            val fields = vector.toCanonical().toMutableMap()
            fields.remove(name)
            val missing = assertFailsWith<ConfidenceReject> { parseVector(fields) }
            assertEquals("missing $name", missing.message)
        }
        assertFailsWith<ConfidenceReject> { ones(recency = Double.NaN) }
        assertFailsWith<ConfidenceReject> { ones(verification = 1.1) }
        assertFailsWith<ConfidenceReject> { ones(corroboration = -0.01) }
        val parsed = parseVector(vector.toCanonical())
        assertEquals(vector, parsed)
    }

    @Test
    fun CF_002_zero_dimension_is_not_compensated() {
        val weights = AggregationWeights.DEFAULT
        val zeroCorroboration = ones(corroboration = 0.0)
        assertEquals(0.0, aggregate(zeroCorroboration, weights))
        assertEquals(0.0, aggregate(ones(sourceReliability = 0.0), weights))
        assertEquals(0.0, aggregate(ones(evidenceStrength = 0.0), weights))
        assertEquals(0.0, aggregate(ones(recency = 0.0), weights))
        assertEquals(0.0, aggregate(ones(verification = 0.0), weights))
        val assessment = assessVector(zeroCorroboration, TruthClass.FACT, weights)
        assertEquals(0.0, assessment.score)
        assertEquals(ConfidenceCategory.UNKNOWN, assessment.category)
        assertNotEquals(ConfidenceCategory.HIGH, assessment.category)
        val perfect = assessVector(ones(), TruthClass.FACT, weights)
        assertEquals(0.6, perfect.score)
        assertTrue(perfect.score > assessment.score)
        val shifted = AggregationWeights.DEFAULT.copy(corroboration = 1.0)
        assertEquals(0.8, aggregate(ones(), shifted))
        assertNotEquals(aggregate(ones(), weights), aggregate(ones(), shifted))
    }

    @Test
    fun CF_003_recency_exponential_decay() {
        val law = RecencyLaw.DEFAULT
        assertEquals(1.0, recencyFromAge(0, law))
        assertEquals(0.5, recencyFromAge(halfLife, law))
        assertEquals(0.25, recencyFromAge(halfLife * 2, law))
        assertEquals(0.0625, recencyFromAge(halfLife * 4, law))
        assertTrue(recencyFromAge(halfLife + 1, law) < 0.5)
        val fresh = stamp(tEvent = present, tObserve = present, tAdmit = present, tPresent = present)
        assertEquals(1.0, recency(fresh, law))
        val oldObserve = present.minusSeconds(halfLife * 4)
        val stale = stamp(tEvent = oldObserve, tObserve = oldObserve, tAdmit = oldObserve, tPresent = present)
        assertEquals(0.0625, recency(stale, law))
        assertTrue(recency(stale, law) < recency(fresh, law))
        val fixture = stamp()
        val age = present.epochSecond - t0.plusSeconds(1).epochSecond
        assertEquals(10799, age)
        assertTrue(age > 0)
        assertTrue(recency(fixture, law) > 0.5)
        assertTrue(recency(fixture, law) < 1.0)
        val main = File("src/main").walkTopDown().filter { it.extension == "kt" }
        for (file in main) {
            assertFalse(file.readText().contains("Instant.now"), file.path)
        }
    }

    @Test
    fun CF_004_corroboration_needs_distinct_source_ids() {
        assertEquals(0.0, corroboration(listOf("train")))
        assertEquals(0.0, corroboration(listOf("train", "train", "TRAIN")))
        assertEquals(0.5, corroboration(listOf("train", "hotel")))
        assertEquals(1.0, corroboration(listOf("train", "hotel", "calendar")))
        assertEquals(1.0, corroboration(listOf("train", "hotel", "calendar", "weather")))
        assertEquals(0.0, corroboration(listOf("train", "hotel", "calendar"), concordant = false))
        assertTrue(corroboration(listOf("train", "hotel")) > corroboration(listOf("train")))
        assertEquals(
            corroboration(listOf("train", "hotel", "calendar")),
            corroboration(listOf("calendar", "hotel", "train"))
        )
    }

    @Test
    fun CF_005_verification_grades() {
        val config = ConfidenceConfig.DEFAULT
        assertEquals(0.3, verificationScore(VerificationGrade.SANDBOX, config))
        assertEquals(0.8, verificationScore(VerificationGrade.REAL, config))
        assertEquals(1.0, verificationScore(VerificationGrade.DETERMINISTIC, config))
        assertEquals(0.0, verificationScore(VerificationGrade.NONE, config))
        assertTrue(
            verificationScore(VerificationGrade.SANDBOX) < verificationScore(VerificationGrade.REAL)
        )
        assertTrue(
            verificationScore(VerificationGrade.REAL) < verificationScore(VerificationGrade.DETERMINISTIC)
        )
        assertEquals(VerificationGrade.SANDBOX, verificationGrade(VerificationEnvironment.SANDBOX))
        assertEquals(VerificationGrade.REAL, verificationGrade(VerificationEnvironment.REAL))
        assertEquals(1.0, sourceReliability(Provenance.OBSERVED_SIGNED))
        assertEquals(0.5, sourceReliability(Provenance.INFERRED))
        assertEquals(0.3, sourceReliability(Provenance.DERIVED_MODEL))
        assertTrue(sourceReliability(Provenance.OBSERVED_SIGNED) > sourceReliability(Provenance.INFERRED))
        assertTrue(sourceReliability(Provenance.INFERRED) > sourceReliability(Provenance.DERIVED_MODEL))
    }

    @Test
    fun CF_006_truth_class_mapping() {
        val weights = AggregationWeights.DEFAULT
        val unit = AggregationWeights.UNIT
        assertEquals(
            ConfidenceCategory.UNKNOWN,
            categoryOf(0.0, TruthClass.FACT)
        )
        assertEquals(
            ConfidenceCategory.HIGH,
            assessVector(ones(), TruthClass.FACT, unit).category
        )
        assertEquals(1.0, assessVector(ones(), TruthClass.FACT, unit).score)
        assertEquals(
            ConfidenceCategory.MEDIUM,
            assessVector(ones(), TruthClass.FACT, weights).category
        )
        assertEquals(
            ConfidenceCategory.MEDIUM,
            assessVector(ones(), TruthClass.OBSERVATION, unit).category
        )
        assertEquals(
            ConfidenceCategory.LOW,
            assessVector(ones(recency = 0.5), TruthClass.HYPOTHESIS, weights).category
        )
        assertEquals(0.4, aggregate(ones(recency = 0.5), weights))
        val zero = assess(
            TruthBearer(TruthClass.UNKNOWN, Provenance.INFERRED, "gap:price"),
            stamp(),
            sourceIds = listOf("train"),
            observationCount = 0,
            concordant = true,
            verification = VerificationGrade.NONE
        )
        assertEquals(0.0, zero.score)
        assertEquals(ConfidenceCategory.UNKNOWN, zero.category)
        assertEquals(0.0, zero.vector.evidenceStrength)
        assertEquals(0.0, zero.vector.corroboration)
    }

    @Test
    fun CF_007_same_input_same_score() {
        val bearer = TruthBearer(TruthClass.FACT, Provenance.OBSERVED_SIGNED, "ver-1")
        val a = assess(
            bearer, stamp(),
            sourceIds = listOf("train", "hotel", "calendar"),
            observationCount = 3,
            concordant = true,
            verification = VerificationGrade.DETERMINISTIC
        )
        val b = assess(
            bearer, stamp(),
            sourceIds = listOf("calendar", "train", "hotel"),
            observationCount = 3,
            concordant = true,
            verification = VerificationGrade.DETERMINISTIC
        )
        assertEquals(a.score, b.score)
        assertEquals(a.vector, b.vector)
        assertEquals(a.category, b.category)
        assertEquals(a, b)
        val again = assess(
            bearer, stamp(),
            sourceIds = listOf("train", "hotel", "calendar"),
            observationCount = 3,
            concordant = true,
            verification = VerificationGrade.DETERMINISTIC
        )
        assertEquals(a, again)
        dumpCorpus()
        assertEquals(vectors.readText(), CanonicalJson.encode(corpus(a)))
        val main = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        for (file in main) {
            val text = file.readText()
            assertFalse(text.contains("Instant.now"), file.path)
            assertFalse(text.contains("Random"), file.path)
            assertFalse(text.contains(".average()"), file.path)
            assertFalse(text.contains("kotlin.random"), file.path)
        }
    }

    @Test
    fun CF_008_freeze_only_confidence() {
        dumpCorpus()
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
            "core/temporal", "core/truth",
            "a3ui/", "renderers/", "broker/", "agent/", "showcase/"
        )
        assertTrue(frozen.isBlank(), frozen)
        val status = ProcessBuilder("git", "status", "--porcelain")
            .directory(root).redirectErrorStream(true).start()
        val porcelain = status.inputStream.bufferedReader().readText()
        assertEquals(0, status.waitFor())
        val allowed = listOf(
            "core/confidence/",
            "settings.gradle.kts",
            "REVIEW_CORE_CONFIDENCE.md"
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
        assertTrue(settings.contains(":core:confidence"))
        val production = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("a3.core.confidence")
        noClasses()
            .that().resideInAPackage("a3.core.confidence..")
            .should().callMethod(Instant::class.java, "now")
            .check(production)
        noClasses()
            .that().resideInAPackage("a3.core.confidence..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "a3.core.envelope..",
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
        assertTrue(gradle.contains("project(\":core:temporal\")"))
        assertTrue(gradle.contains("project(\":core:truth\")"))
        assertTrue(gradle.contains("project(\":core:json\")"))
        assertFalse(gradle.contains("project(\":core:envelope\")"))
        assertFalse(gradle.contains("project(\":core:action\")"))
        assertFalse(gradle.contains("project(\":a3ui\")"))
        val review = File(root, "REVIEW_CORE_CONFIDENCE.md")
        assertTrue(review.exists(), "REVIEW_CORE_CONFIDENCE.md missing")
        val body = review.readText()
        assertTrue(body.contains("CF-001"))
        assertTrue(body.contains("CF-008"))
        assertTrue(body.contains("confidence-vectors.json"))
        assertTrue(body.contains("weighted_min") || body.contains("weighted min") || body.contains("minimo pesato"))
        assertTrue(vectors.exists())
    }

    private fun dumpCorpus() {
        val fixture = assess(
            TruthBearer(TruthClass.FACT, Provenance.OBSERVED_SIGNED, "ver-1"),
            stamp(),
            sourceIds = listOf("train", "hotel", "calendar"),
            observationCount = 3,
            concordant = true,
            verification = VerificationGrade.DETERMINISTIC
        )
        lock(corpus(fixture))
    }

    private fun corpus(fixture: ConfidenceAssessment): Map<String, Any?> {
        val defaultW = AggregationWeights.DEFAULT
        val unit = AggregationWeights.UNIT
        val law = RecencyLaw.DEFAULT
        val fixtureRecency = recency(stamp(), law)
        return mapOf(
            "config" to ConfidenceConfig.DEFAULT.toCanonical(),
            "corroboration" to mapOf(
                "identical" to corroboration(listOf("train", "train", "TRAIN")),
                "one" to corroboration(listOf("train")),
                "three" to corroboration(listOf("train", "hotel", "calendar")),
                "two" to corroboration(listOf("train", "hotel"))
            ),
            "fixture" to fixture.toCanonical(),
            "mapping" to mapOf(
                "alta_unit_fact" to assessVector(ones(), TruthClass.FACT, unit).toCanonical(),
                "bassa_hypothesis" to assessVector(ones(recency = 0.5), TruthClass.HYPOTHESIS, defaultW).toCanonical(),
                "media_default_fact" to assessVector(ones(), TruthClass.FACT, defaultW).toCanonical(),
                "media_observation" to assessVector(ones(), TruthClass.OBSERVATION, unit).toCanonical(),
                "unknown_zero" to assessVector(ones(corroboration = 0.0), TruthClass.FACT, defaultW).toCanonical()
            ),
            "recency" to mapOf(
                "age_0" to recencyFromAge(0, law),
                "fixture_10799s" to fixtureRecency,
                "half_life" to recencyFromAge(halfLife, law),
                "stale_four_half_lives" to recencyFromAge(halfLife * 4, law),
                "two_half_lives" to recencyFromAge(halfLife * 2, law)
            ),
            "verification" to mapOf(
                "deterministic" to verificationScore(VerificationGrade.DETERMINISTIC),
                "none" to verificationScore(VerificationGrade.NONE),
                "real" to verificationScore(VerificationGrade.REAL),
                "sandbox" to verificationScore(VerificationGrade.SANDBOX)
            ),
            "weights_shift" to mapOf(
                "default_score" to aggregate(ones(), defaultW),
                "raised_corroboration_score" to aggregate(ones(), defaultW.copy(corroboration = 1.0))
            ),
            "zero_not_compensated" to assessVector(ones(verification = 0.0), TruthClass.FACT, defaultW).toCanonical()
        )
    }
}
