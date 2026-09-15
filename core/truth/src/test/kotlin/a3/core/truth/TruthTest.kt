package a3.core.truth

import a3.core.admission.AdmissionDecision
import a3.core.admission.ConfidenceProfile
import a3.core.admission.Esito
import a3.core.admission.ObservationCandidate
import a3.core.admission.ReasonCode
import a3.core.admission.SourceId
import a3.core.admission.SourceType
import a3.core.admission.acceptedObservation
import a3.core.json.CanonicalJson
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

class TruthTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")
    private val root = File("../..")
    private val vectors = File("src/test/resources/truth-vectors.json")

    private fun accepted(id: String = "obs-1"): a3.core.admission.AcceptedObservation {
        val candidate = ObservationCandidate(
            source = SourceId("train"),
            id = id,
            occurredAt = t,
            observedAt = t,
            ingestedAt = t,
            data = mapOf("k" to "depart"),
            dataschema = "claim-array.schema.json",
            confidenceProfile = ConfidenceProfile(SourceType.DIRECT, "")
        )
        return acceptedObservation(
            candidate,
            AdmissionDecision(Esito.ADMIT, ReasonCode.ADMIT, "p", "1"),
            t
        )
    }

    private fun lock(actual: Map<String, Any?>): String {
        val json = CanonicalJson.encode(actual)
        if (System.getenv("TC_DUMP") == "1") {
            vectors.parentFile.mkdirs()
            vectors.writeText(json)
        }
        require(vectors.exists()) { "missing truth-vectors.json — generate with TC_DUMP=1" }
        return json
    }

    @Test
    fun TC_001_promotion_requires_verification_admitted() {
        val hypo = TruthBearer(TruthClass.HYPOTHESIS, Provenance.DERIVED_MODEL, "model:slot")
        val stayed = promoteHypothesis(hypo, null)
        assertEquals(TruthClass.HYPOTHESIS, stayed.truthClass)
        assertEquals("model:slot", stayed.ref)
        val minted = accepted("obs-verify")
        val verification = verificationFromAccepted(
            minted,
            verificationId = "ver-1",
            environment = VerificationEnvironment.REAL
        )
        val fact = promoteHypothesis(hypo, verification)
        assertEquals(TruthClass.FACT, fact.truthClass)
        assertEquals(Provenance.OBSERVED_SIGNED, fact.provenance)
        assertEquals("ver-1", fact.ref)
        assertNotEquals(hypo.ref, fact.ref)
        assertFailsWith<TruthReject> {
            promoteHypothesis(TruthBearer(TruthClass.OBSERVATION, Provenance.OBSERVED_SIGNED), verification)
        }
    }

    @Test
    fun TC_002_unknown_is_first_class() {
        val unknown = fromInsufficient(Provenance.INFERRED, "gap:price")
        assertEquals(TruthClass.UNKNOWN, unknown.truthClass)
        assertEquals(Provenance.INFERRED, unknown.provenance)
        assertEquals("gap:price", unknown.ref)
        assertNotEquals(TruthClass.FACT, unknown.truthClass)
        assertNotEquals(TruthClass.HYPOTHESIS, unknown.truthClass)
        val axis = projectAxis(unknown, "fresh")
        assertEquals("unknown", axis.support)
        assertEquals("unknown", axis.status)
    }

    @Test
    fun TC_003_missing_provenance_is_explicit_reject() {
        val missingProv = assertFailsWith<TruthReject> {
            parseBearer(mapOf("truth_class" to "fact"))
        }
        assertEquals("missing provenance", missingProv.message)
        val missingClass = assertFailsWith<TruthReject> {
            parseBearer(mapOf("provenance" to "observed_signed"))
        }
        assertEquals("missing truthClass", missingClass.message)
        val ok = parseBearer(
            mapOf("truth_class" to "observation", "provenance" to "human_admitted", "ref" to "r1")
        )
        assertEquals(TruthClass.OBSERVATION, ok.truthClass)
        assertEquals(Provenance.HUMAN_ADMITTED, ok.provenance)
        assertEquals("r1", ok.ref)
    }

    @Test
    fun TC_004_receipt_is_observation_not_fact() {
        val zero = fromProcessOutcome(0, "SUCCESS")
        assertEquals(TruthClass.OBSERVATION, zero.truthClass)
        assertNotEquals(TruthClass.FACT, zero.truthClass)
        assertTrue(zero.ref!!.contains("0"))
        assertTrue(zero.ref!!.contains("SUCCESS"))
        val fail = fromProcessOutcome(1, "FAILED")
        assertEquals(TruthClass.OBSERVATION, fail.truthClass)
        val empty = fromProcessOutcome(0, null)
        assertEquals(TruthClass.OBSERVATION, empty.truthClass)
        assertEquals("exit:0", empty.ref)
    }

    @Test
    fun TC_005_sandbox_is_observation_fact_only_after_real() {
        val hypo = TruthBearer(TruthClass.HYPOTHESIS, Provenance.DERIVED_MODEL, "model:x")
        val sandbox = VerificationAdmitted("ver-sand", "obs-sand", VerificationEnvironment.SANDBOX)
        val observed = promoteHypothesis(hypo, sandbox)
        assertEquals(TruthClass.OBSERVATION, observed.truthClass)
        assertEquals("ver-sand", observed.ref)
        val sandDirect = fromSandbox("ver-sand", Provenance.OBSERVED_SIGNED)
        assertEquals(TruthClass.OBSERVATION, sandDirect.truthClass)
        assertFailsWith<TruthReject> { fromRealAdmitted(sandbox) }
        val real = VerificationAdmitted("ver-real", "obs-real", VerificationEnvironment.REAL)
        val fact = fromRealAdmitted(real)
        assertEquals(TruthClass.FACT, fact.truthClass)
        assertEquals("ver-real", fact.ref)
        val promoted = promoteHypothesis(hypo, real)
        assertEquals(TruthClass.FACT, promoted.truthClass)
    }

    @Test
    fun TC_006_axis_coherence_on_fixture() {
        val hypo = TruthBearer(TruthClass.HYPOTHESIS, Provenance.DERIVED_MODEL)
        val unknown = TruthBearer(TruthClass.UNKNOWN, Provenance.INFERRED)
        val fact = TruthBearer(TruthClass.FACT, Provenance.OBSERVED_SIGNED, "ver-1")
        val hypoAxis = projectAxis(hypo, "fresh")
        assertNotEquals("believed", hypoAxis.status)
        assertEquals("held", hypoAxis.status)
        val unknownAxis = projectAxis(unknown, "fresh")
        assertEquals("unknown", unknownAxis.support)
        assertEquals("unknown", unknownAxis.status)
        val believed = projectAxis(fact, "fresh")
        assertEquals("believed", believed.status)
        val stale = projectAxis(fact, "stale")
        assertNotEquals("believed", stale.status)
        assertEquals("held", stale.status)
        assertFailsWith<TruthReject> { requireAxisCoherent(hypo, "believed", "fresh") }
        assertFailsWith<TruthReject> { requireAxisCoherent(fact, "believed", "stale") }
        requireAxisCoherent(fact, "believed", "fresh")
        requireAxisCoherent(hypo, "held", "fresh")
        assertEquals(Provenance.DERIVED_MODEL, provenanceFrom(SourceType.GROUNDED_BY_MODEL))
        assertEquals(Provenance.OBSERVED_SIGNED, provenanceFrom(SourceType.DIRECT))
        assertEquals(Provenance.INFERRED, provenanceFrom(SourceType.INFERRED))
    }

    @Test
    fun TC_007_freeze_and_vectors() {
        val corpus = mapOf(
            "axis_fact_fresh" to projectAxis(
                TruthBearer(TruthClass.FACT, Provenance.OBSERVED_SIGNED, "ver-1"),
                "fresh"
            ).toCanonical(),
            "axis_fact_stale" to projectAxis(
                TruthBearer(TruthClass.FACT, Provenance.OBSERVED_SIGNED, "ver-1"),
                "stale"
            ).toCanonical(),
            "axis_hypothesis" to projectAxis(
                TruthBearer(TruthClass.HYPOTHESIS, Provenance.DERIVED_MODEL, "model:slot"),
                "fresh"
            ).toCanonical(),
            "axis_unknown" to projectAxis(
                TruthBearer(TruthClass.UNKNOWN, Provenance.INFERRED, "gap:price"),
                "fresh"
            ).toCanonical(),
            "fact_promoted" to promoteHypothesis(
                TruthBearer(TruthClass.HYPOTHESIS, Provenance.DERIVED_MODEL, "model:slot"),
                VerificationAdmitted("ver-1", "obs-verify", VerificationEnvironment.REAL)
            ).toCanonical(),
            "hypothesis" to TruthBearer(
                TruthClass.HYPOTHESIS,
                Provenance.DERIVED_MODEL,
                "model:slot"
            ).toCanonical(),
            "receipt" to fromProcessOutcome(0, "SUCCESS").toCanonical(),
            "sandbox" to fromSandbox("ver-sand", Provenance.OBSERVED_SIGNED).toCanonical(),
            "unknown" to fromInsufficient(Provenance.INFERRED, "gap:price").toCanonical(),
            "verification" to VerificationAdmitted(
                "ver-1",
                "obs-verify",
                VerificationEnvironment.REAL
            ).toCanonical()
        )
        val json = lock(corpus)
        assertEquals(vectors.readText(), json)
        val receipt = CanonicalJson.encode(fromProcessOutcome(0, "SUCCESS").toCanonical())
        assertTrue(receipt.contains("\"truth_class\":\"observation\""))
        assertFalse(receipt.contains("\"truth_class\":\"fact\""))
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
            "core/admission", "core/action", "core/json", "core/temporal",
            "a3ui/", "renderers/", "broker/", "agent/", "showcase/"
        )
        assertTrue(frozen.isBlank(), frozen)
        val status = ProcessBuilder("git", "status", "--porcelain")
            .directory(root).redirectErrorStream(true).start()
        val porcelain = status.inputStream.bufferedReader().readText()
        assertEquals(0, status.waitFor())
        val allowed = listOf(
            "core/truth/",
            "settings.gradle.kts",
            "REVIEW_CORE_TRUTH.md",
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
        assertTrue(settings.contains(":core:truth"))
        val production = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("a3.core.truth")
        noClasses()
            .that().resideInAPackage("a3.core.truth..")
            .should().callMethod(Instant::class.java, "now")
            .check(production)
        noClasses()
            .that().resideInAPackage("a3.core.truth..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "a3.core.temporal..",
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
        val main = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(main.isNotEmpty())
        for (file in main) {
            val text = file.readText()
            assertFalse(text.contains("Instant.now"), file.path)
            assertFalse(text.contains("now()"), file.path)
        }
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("project(\":core:admission\")"))
        assertTrue(gradle.contains("project(\":core:json\")"))
        assertFalse(gradle.contains("project(\":core:temporal\")"))
        assertFalse(gradle.contains("project(\":a3ui\")"))
        val review = File(root, "REVIEW_CORE_TRUTH.md")
        assertTrue(review.exists(), "REVIEW_CORE_TRUTH.md missing")
        val body = review.readText()
        assertTrue(body.contains("TC-001"))
        assertTrue(body.contains("truth-vectors.json"))
    }
}
