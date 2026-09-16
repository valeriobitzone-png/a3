// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.admission

import a3.core.world.BeliefState
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AdmissionTest {
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private fun validData(
        k: String = "calendar.next",
        v: Any? = "work@08:30",
        source: String = "calendar",
        observedAt: Instant = t
    ): List<Map<String, Any?>> = listOf(
        mapOf(
            "k" to k,
            "v" to v,
            "confidence" to 1.0,
            "source" to source,
            "observed_at" to observedAt
        )
    )

    private fun candidate(
        source: String = "calendar",
        id: String = "obs-1",
        observedAt: Instant = t,
        ingestedAt: Instant = t,
        data: Any = validData(observedAt = observedAt),
        dataschema: String = CLAIM_ARRAY_SCHEMA,
        sourceType: SourceType = SourceType.DIRECT
    ) = ObservationCandidate(
        source = SourceId(source),
        id = id,
        occurredAt = observedAt,
        observedAt = observedAt,
        ingestedAt = ingestedAt,
        data = data,
        dataschema = dataschema,
        confidenceProfile = ConfidenceProfile(sourceType, opaque = "")
    )

    private fun permissive(
        policyId: String = "p",
        policyVersion: String = "1",
        ttlSeconds: Long? = null,
        blacklist: Set<SourceId> = emptySet(),
        holdModelGrounded: Boolean = false,
        schemas: Map<String, ByteArray> = mapOf(
            CLAIM_ARRAY_SCHEMA to schemaResourceBytes(CLAIM_ARRAY_SCHEMA)
        ),
        validatorProfile: String = "a3.core.json.SchemaValidator",
        validatorVersion: String = "1"
    ) = admissionPolicy(
        policyId = policyId,
        policyVersion = policyVersion,
        ttlSeconds = ttlSeconds,
        blacklist = blacklist,
        holdModelGrounded = holdModelGrounded,
        schemas = schemas,
        validatorProfile = validatorProfile,
        validatorVersion = validatorVersion
    )

    private fun admit(
        c: ObservationCandidate = candidate(),
        policy: AdmissionPolicy = permissive(),
        asOf: Instant = c.ingestedAt,
        admitted: Set<Pair<SourceId, String>> = emptySet(),
        admittedAt: Instant = t
    ): AcceptedObservation {
        val decision = evaluate(c, policy, asOf, admitted)
        return acceptedObservation(c, decision, admittedAt)
    }

    private fun stateHash(state: BeliefState): ByteArray {
        val facts = state.facts.sortedWith(
            compareBy<a3.core.world.api.Claim> { it.k }
                .thenBy { it.observedAt }
                .thenBy { it.id }
                .thenBy { it.source }
        ).map { fact ->
            buildMap<String, Any?> {
                put("confidence", fact.confidence)
                fact.expiresAt?.let { put("expires_at", it) }
                if (fact.id.isNotBlank()) put("id", fact.id)
                put("k", fact.k)
                put("observed_at", fact.observedAt)
                put("source", fact.source)
                fact.supersededBy?.let { put("superseded_by", it) }
                put("v", fact.v)
            }
        }
        return a3.core.json.CanonicalJson.encode(
            mapOf("facts" to facts, "version" to state.version)
        ).toByteArray(Charsets.UTF_8)
    }

    @Test
    fun ADM_001_accepted_produced_candidate_without_admission_throws() {
        val accepted = admit()
        val folded = BeliefState().apply(accepted)
        assertEquals(1, folded.version)
        assertFailsWith<IllegalArgumentException> {
            BeliefState().apply(candidate())
        }
    }

    @Test
    fun ADM_002_pure_decision_byte_identity_and_duplicate_sensitivity() {
        val c = candidate()
        val policy = permissive()
        val asOf = c.ingestedAt
        val empty = emptySet<Pair<SourceId, String>>()
        val d1 = evaluate(c, policy, asOf, empty)
        val d2 = evaluate(c, policy, asOf, empty)
        assertEquals(Esito.ADMIT, d1.esito)
        assertContentEquals(decisionBytes(d1), decisionBytes(d2))
        val dup = evaluate(c, policy, asOf, setOf(c.source to c.id))
        assertEquals(Esito.REJECT, dup.esito)
        assertEquals(ReasonCode.REJECT_DUPLICATE, dup.reasonCode)
        assertNotEquals(
            decisionBytes(d1).toString(Charsets.UTF_8),
            decisionBytes(dup).toString(Charsets.UTF_8)
        )
    }

    @Test
    fun ADM_003_reason_code_closed_enum_missing_throws() {
        val names = ReasonCode.entries.map { it.name }.toSet()
        assertEquals(
            setOf(
                "ADMIT",
                "REJECT_STALE",
                "REJECT_POLICY_DENY",
                "REJECT_SCHEMA_INVALID",
                "REJECT_DUPLICATE",
                "HELD_FOR_REVIEW"
            ),
            names
        )
        assertFails { ReasonCode.valueOf("NOT_A_REASON") }
        assertFails {
            AdmissionDecision(Esito.ADMIT, ReasonCode.REJECT_STALE, "p", "1")
        }
        val ctor = AdmissionDecision::class.java.constructors.first()
        assertFails {
            ctor.newInstance(Esito.ADMIT, null, "p", "1")
        }
    }

    @Test
    fun ADM_004_ttl_stale_fresh_null_zero_negative() {
        val observed = t
        val asOf = t.plusSeconds(10)
        val c = candidate(observedAt = observed, ingestedAt = asOf)
        val stale = evaluate(c, permissive(ttlSeconds = 5), asOf, emptySet())
        assertEquals(ReasonCode.REJECT_STALE, stale.reasonCode)
        val fresh = evaluate(c, permissive(ttlSeconds = 60), asOf, emptySet())
        assertEquals(ReasonCode.ADMIT, fresh.reasonCode)
        val skip = evaluate(c, permissive(ttlSeconds = null), asOf.plusSeconds(10_000), emptySet())
        assertEquals(ReasonCode.ADMIT, skip.reasonCode)
        val rejectAll = evaluate(c, permissive(ttlSeconds = 0), c.ingestedAt, emptySet())
        assertEquals(ReasonCode.REJECT_STALE, rejectAll.reasonCode)
        assertFails { permissive(ttlSeconds = -1) }
    }

    @Test
    fun ADM_005_blacklist_deny_and_allowed_admit() {
        val c = candidate(source = "calendar")
        val denied = evaluate(
            c,
            permissive(blacklist = setOf(SourceId("calendar"))),
            c.ingestedAt,
            emptySet()
        )
        assertEquals(ReasonCode.REJECT_POLICY_DENY, denied.reasonCode)
        val allowed = evaluate(c, permissive(blacklist = setOf(SourceId("train"))), c.ingestedAt, emptySet())
        assertEquals(ReasonCode.ADMIT, allowed.reasonCode)
    }

    @Test
    fun ADM_006_duplicate_key_and_same_id_different_sources() {
        val a = candidate(source = "calendar", id = "same")
        val b = candidate(source = "train", id = "same", data = validData(k = "train.selected", v = true, source = "train"))
        val policy = permissive()
        assertEquals(ReasonCode.ADMIT, evaluate(a, policy, a.ingestedAt, emptySet()).reasonCode)
        assertEquals(ReasonCode.ADMIT, evaluate(b, policy, b.ingestedAt, emptySet()).reasonCode)
        val second = evaluate(a, policy, a.ingestedAt, setOf(a.source to a.id))
        assertEquals(ReasonCode.REJECT_DUPLICATE, second.reasonCode)
        val otherSource = evaluate(b, policy, b.ingestedAt, setOf(a.source to a.id))
        assertEquals(ReasonCode.ADMIT, otherSource.reasonCode)
    }

    @Test
    fun ADM_007_schema_invalid_valid_absent_unknown() {
        val policy = permissive()
        val bad = candidate(data = listOf(mapOf("k" to "x")))
        assertEquals(
            ReasonCode.REJECT_SCHEMA_INVALID,
            evaluate(bad, policy, bad.ingestedAt, emptySet()).reasonCode
        )
        val ok = candidate()
        assertEquals(ReasonCode.ADMIT, evaluate(ok, policy, ok.ingestedAt, emptySet()).reasonCode)
        val absent = candidate(dataschema = "")
        assertEquals(
            ReasonCode.REJECT_SCHEMA_INVALID,
            evaluate(absent, policy, absent.ingestedAt, emptySet()).reasonCode
        )
        val unknown = candidate(dataschema = "not-in-policy.schema.json")
        assertEquals(
            ReasonCode.REJECT_SCHEMA_INVALID,
            evaluate(unknown, policy, unknown.ingestedAt, emptySet()).reasonCode
        )
    }

    @Test
    fun ADM_007_neg_a_same_id_version_different_digest_fails() {
        val registry = PolicyRegistry()
        val a = permissive(schemas = mapOf(CLAIM_ARRAY_SCHEMA to schemaResourceBytes(CLAIM_ARRAY_SCHEMA)))
        val b = permissive(schemas = emptyMap())
        assertNotEquals(a.policyDigest, b.policyDigest)
        registry.register(a)
        assertFails { registry.register(b) }
    }

    @Test
    fun ADM_007_neg_b_mutating_registered_schema_set_fails() {
        val policy = permissive()
        val registry = PolicyRegistry()
        registry.register(policy)
        assertFails {
            @Suppress("UNCHECKED_CAST")
            (policy.schemas as MutableMap<String, ByteArray>)["x"] = byteArrayOf(1)
        }
        assertFails {
            registry.register(permissive(schemas = emptyMap()))
        }
    }

    @Test
    fun ADM_007_neg_c_same_digest_same_decision_bytes() {
        val c = candidate()
        val policy = permissive()
        val asOf = c.ingestedAt
        val admitted = emptySet<Pair<SourceId, String>>()
        val d1 = evaluate(c, policy, asOf, admitted)
        val d2 = evaluate(c, policy, asOf, admitted)
        assertEquals(policy.policyDigest, permissive().policyDigest)
        assertContentEquals(decisionBytes(d1), decisionBytes(d2))
    }

    @Test
    fun validator_profile_and_version_are_in_policy_digest() {
        val base = permissive(validatorProfile = "a3.core.json.SchemaValidator", validatorVersion = "1")
        val otherProfile = permissive(validatorProfile = "other.Validator", validatorVersion = "1")
        val otherVersion = permissive(validatorProfile = "a3.core.json.SchemaValidator", validatorVersion = "2")
        assertNotEquals(base.policyDigest, otherProfile.policyDigest)
        assertNotEquals(base.policyDigest, otherVersion.policyDigest)
    }

    @Test
    fun ADM_008_held_logs_not_accepted_not_fold() {
        val c = candidate(sourceType = SourceType.GROUNDED_BY_MODEL)
        val hold = permissive(holdModelGrounded = true)
        val decision = evaluate(c, hold, c.ingestedAt, emptySet())
        assertEquals(Esito.HELD, decision.esito)
        assertEquals(ReasonCode.HELD_FOR_REVIEW, decision.reasonCode)
        assertFails { acceptedObservation(c, decision, t) }
        val entry = AdmissionLogEntry(c, decision, t, decision.reasonCode)
        assertFails { BeliefState().apply(entry) }
        val otherwise = evaluate(
            candidate(sourceType = SourceType.DIRECT),
            hold,
            t,
            emptySet()
        )
        assertEquals(ReasonCode.ADMIT, otherwise.reasonCode)
    }

    @Test
    fun ADM_009_fold_hash_stable_readmission_throws() {
        val accepted = admit()
        val first = BeliefState().apply(accepted)
        val h = stateHash(first)
        val again = BeliefState().apply(accepted)
        assertContentEquals(h, stateHash(again))
        assertFails { first.apply(candidate()) }
        val foldSrc = File("../world/src/main/kotlin/a3/core/world/BeliefState.kt").readText()
        assertFalse(foldSrc.contains("evaluate("))
    }

    @Test
    fun ADM_010_apply_accepted_bumps_candidate_and_log_throw() {
        val accepted = admit()
        val bumped = BeliefState().apply(accepted)
        assertEquals(1, bumped.version)
        assertTrue(bumped.facts.isNotEmpty())
        assertFails { BeliefState().apply(candidate()) }
        val held = evaluate(
            candidate(sourceType = SourceType.GROUNDED_BY_MODEL),
            permissive(holdModelGrounded = true),
            t,
            emptySet()
        )
        val entry = AdmissionLogEntry(
            candidate(sourceType = SourceType.GROUNDED_BY_MODEL),
            held,
            t,
            held.reasonCode
        )
        assertFails { BeliefState().apply(entry) }
    }

    @Test
    fun ADM_011_evaluate_does_not_depend_on_belief_or_clock() {
        val production = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("a3.core.admission")
        noClasses()
            .that().resideInAPackage("a3.core.admission..")
            .should().dependOnClassesThat()
            .haveSimpleName("BeliefState")
            .check(production)
        noClasses()
            .that().resideInAPackage("a3.core.admission..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "a3.core.runtime..",
                "a3.prediction..",
                "a3.projection..",
                "a3.a3ui..",
                "a3.renderers..",
                "a3.intent..",
                "a3.adapters.."
            )
            .check(production)
        val eval = File("src/main/kotlin/a3/core/admission/Evaluate.kt").readText()
        assertFalse(eval.contains("Instant.now"))
        assertFalse(eval.contains("admittedAt"))
        assertFalse(AdmissionDecision::class.java.declaredFields.any { it.name == "admittedAt" })
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("project(\":core:json\")"))
        assertFalse(gradle.contains("project(\":core:runtime\")"))
        assertFalse(gradle.contains("project(\":prediction\")"))
        assertFalse(gradle.contains(":adapters"))
    }

    @Test
    fun ADM_012_llm_extraction_is_document_grounded_llm_source_throws() {
        val extracted = candidate(source = "document", sourceType = SourceType.GROUNDED_BY_MODEL)
        assertEquals("document", extracted.source.value)
        assertEquals(SourceType.GROUNDED_BY_MODEL, extracted.confidenceProfile.sourceType)
        assertFails { SourceId("llm") }
        assertFails { SourceId(" LLM ") }
    }

    @Test
    fun ADM_013_same_id_two_sources_then_duplicate_from_a() {
        val fromA = candidate(source = "a", id = "x", data = validData(source = "a"))
        val fromB = candidate(source = "b", id = "x", data = validData(k = "other", source = "b"))
        val policy = permissive()
        val firstA = evaluate(fromA, policy, fromA.ingestedAt, emptySet())
        val firstB = evaluate(fromB, policy, fromB.ingestedAt, setOf(fromA.source to fromA.id))
        assertEquals(ReasonCode.ADMIT, firstA.reasonCode)
        assertEquals(ReasonCode.ADMIT, firstB.reasonCode)
        val secondA = evaluate(
            fromA,
            policy,
            fromA.ingestedAt,
            setOf(fromA.source to fromA.id, fromB.source to fromB.id)
        )
        assertEquals(ReasonCode.REJECT_DUPLICATE, secondA.reasonCode)
    }

    @Test
    fun ADM_014_held_log_apply_throws_held_to_accepted_throws() {
        val c = candidate(sourceType = SourceType.GROUNDED_BY_MODEL)
        val decision = evaluate(c, permissive(holdModelGrounded = true), c.ingestedAt, emptySet())
        assertEquals(Esito.HELD, decision.esito)
        val entry = AdmissionLogEntry(c, decision, t, ReasonCode.HELD_FOR_REVIEW)
        assertFails { BeliefState().apply(entry) }
        assertFails { acceptedObservation(c, decision, t) }
    }

    @Test
    fun ADM_015_reapply_same_accepted_is_noop_mutating_apply_throws() {
        val accepted = admit()
        val one = BeliefState().apply(accepted)
        val two = one.apply(accepted)
        val three = two.apply(accepted)
        assertContentEquals(stateHash(one), stateHash(two))
        assertContentEquals(stateHash(one), stateHash(three))
        assertEquals(one.version, two.version)
        assertFails { one.apply(candidate(id = "obs-other")) }
    }
}
