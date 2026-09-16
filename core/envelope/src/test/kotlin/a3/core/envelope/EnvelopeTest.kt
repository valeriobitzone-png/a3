// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.envelope

import a3.core.temporal.TemporalStamp
import a3.core.truth.Provenance
import a3.core.truth.TruthBearer
import a3.core.truth.TruthClass
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EnvelopeTest {
    private val mapper = ObjectMapper()
    private val t0 = Instant.parse("2026-08-27T08:00:00Z")
    private val present = Instant.parse("2026-08-27T11:00:00Z")
    private val root = File("../..")
    private val vectors = File("src/test/resources")
    private val tmOrder = File(root, "core/temporal/src/test/resources/tm-order.json")
    private val tmDedup = File(root, "core/temporal/src/test/resources/tm-dedup.json")
    private val tmFold = File(root, "core/temporal/src/test/resources/tm-fold.json")
    private val truthVectors = File(root, "core/truth/src/test/resources/truth-vectors.json")

    private fun stamp(): TemporalStamp =
        TemporalStamp(t0, t0.plusSeconds(1), t0.plusSeconds(2), present)

    private fun truth(): TruthBearer =
        TruthBearer(TruthClass.FACT, Provenance.OBSERVED_SIGNED, "ver-1")

    private fun foldRef(): String = Jcs.sha256Hex(Jcs.bytesOfJson(tmFold.readText()))

    private fun fixture(): CloudEventEnvelope = pack(
        type = "a3.belief.admitted",
        sourceId = "train",
        subject = "trip.milano",
        stamp = stamp(),
        truth = truth(),
        content = mapOf("depart" to "09:30"),
        foldRef = foldRef(),
        attestation = lockV2Attestation()
    )

    private fun golden(name: String, actual: String): String {
        val file = File(vectors, name)
        if (System.getenv("EN_DUMP") == "1") {
            vectors.mkdirs()
            file.writeText(actual)
        }
        require(file.exists()) { "missing $name — generate with EN_DUMP=1" }
        return file.readText()
    }

    private val rfc8785Input: String =
        EnvelopeTest::class.java.getResource("/rfc8785-input.json")!!.readText()

    private fun lockJson(name: String, value: Any?): String {
        val actual = Jcs.of(value)
        assertEquals(actual, golden(name, actual))
        return actual
    }

    private fun dumpLocks(event: CloudEventEnvelope = fixture()) {
        golden("envelope-rfc8785.json", Jcs.ofJson(rfc8785Input))
        golden("envelope-payload.json", Jcs.of(event.data.toCanonical()))
        golden("envelope-event.json", encodeEvent(event))
        lockJson(
            "envelope-hashes.json",
            mapOf(
                "envelope-event.json" to Jcs.sha256Hex(encodeEvent(event).toByteArray(Charsets.UTF_8)),
                "envelope-payload.json" to Jcs.sha256Hex(Jcs.bytes(event.data.toCanonical())),
                "envelope-rfc8785.json" to Jcs.sha256Hex(Jcs.bytesOfJson(rfc8785Input)),
                "event_id" to event.id
            )
        )
    }

    private fun shuffled(canonical: String): String = encodeShuffled(mapper.readTree(canonical))

    private fun encodeShuffled(node: JsonNode): String = when {
        node.isObject -> {
            val names = node.fieldNames().asSequence().toList().sortedDescending()
            names.joinToString(",", "{", "}") { name ->
                "${mapper.writeValueAsString(name)}:${encodeShuffled(node.get(name))}"
            }
        }
        node.isArray -> node.joinToString(",", "[", "]") { encodeShuffled(it) }
        else -> node.toString()
    }

    @Test
    fun EN_001_jcs_matches_rfc8785_and_lock_vectors() {
        assertEquals("""{"a":1,"b":2}""", Jcs.ofJson("""{"b": 2, "a": 1}"""))
        assertEquals("""[56,{"1":[],"10":null,"d":true}]""", Jcs.ofJson("""[56,{"d":true,"10":null,"1":[]}]"""))
        dumpLocks()
        val rfcCanonical = Jcs.ofJson(rfc8785Input)
        assertEquals(rfcCanonical, File(vectors, "envelope-rfc8785.json").readText())
        assertTrue(rfcCanonical.startsWith("""{"literals":[null,true,false],"numbers":["""))
        assertTrue(rfcCanonical.contains(""""string":"""))
        val stampJson = Jcs.of(stamp().toCanonical())
        assertEquals(
            """{"t_admit":"2026-08-27T08:00:02Z","t_event":"2026-08-27T08:00:00Z","t_observe":"2026-08-27T08:00:01Z","t_present":"2026-08-27T11:00:00Z"}""",
            stampJson
        )
        val hypothesis = TruthBearer(TruthClass.HYPOTHESIS, Provenance.DERIVED_MODEL, "model:slot")
        val hypothesisJson = Jcs.of(hypothesis.toCanonical())
        val truthLock = mapper.readTree(truthVectors)
        assertEquals(Jcs.ofJson(truthLock.get("hypothesis").toString()), hypothesisJson)
        assertEquals(tmFold.readText(), Jcs.ofJson(tmFold.readText()))
        assertEquals(tmOrder.readText(), Jcs.ofJson(tmOrder.readText()))
        assertEquals(tmDedup.readText(), Jcs.ofJson(tmDedup.readText()))
        assertEquals(truthVectors.readText(), Jcs.ofJson(truthVectors.readText()))
    }

    @Test
    fun EN_002_id_is_sha256_of_jcs_payload_not_uuid() {
        val a = fixture()
        val b = fixture()
        assertEquals(a.id, b.id)
        assertEquals(contentId(a.data), a.id)
        assertEquals(64, a.id.length)
        assertTrue(a.id.matches(Regex("[0-9a-f]{64}")))
        assertFalse(a.id.contains("-"))
        assertNotEquals(UUID.randomUUID().toString(), a.id)
        val spaced = pack(
            type = a.type,
            sourceId = "train",
            subject = a.subject,
            stamp = a.data.temporal,
            truth = a.data.truth,
            content = mapOf("depart" to "09:30 "),
            foldRef = a.data.foldRef,
            attestation = a.data.attestation
        )
        assertNotEquals(a.id, spaced.id)
        val pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(encodeEvent(a)))
        assertNotEquals(encodeEvent(a), pretty)
        assertEquals(a.id, parseEvent(pretty).id)
        val rawPrettyHash = Jcs.sha256Hex(pretty.toByteArray(Charsets.UTF_8))
        assertNotEquals(a.id, rawPrettyHash)
        val payloadBytes = Jcs.bytes(a.data.toCanonical())
        assertEquals(a.id, Jcs.sha256Hex(payloadBytes))
    }

    @Test
    fun EN_003_cloudevents_1_0_schema() {
        val event = fixture()
        val canonical = encodeEvent(event)
        CloudEventsSchema.validateCanonical(canonical)
        val schema = mapper.readTree(
            EnvelopeTest::class.java.getResource("/a3/envelope/cloudevents-1.0.json")!!.readText()
        )
        val instance = mapper.readTree(canonical)
        for (name in schema.get("required").map { it.asText() }) {
            assertTrue(instance.has(name), "missing CloudEvents attribute $name")
            assertTrue(instance.get(name).asText().isNotBlank())
        }
        assertEquals("1.0", instance.get("specversion").asText())
        assertEquals("application/json", instance.get("datacontenttype").asText())
        assertEquals(event.data.temporal.tPresent.toString(), instance.get("time").asText())
        assertTrue(instance.get("source").asText().startsWith("urn:a3:source:"))
        assertFailsWith<EnvelopeReject> {
            validateCloudEvent(event.copy(specversion = "0.3"))
        }
        assertFailsWith<EnvelopeReject> {
            parseEvent(canonical.replaceFirst("\"1.0\"", "\"0.3\""))
        }
    }

    @Test
    fun EN_004_four_times_round_trip() {
        val event = fixture()
        val parsed = parseEvent(encodeEvent(event))
        assertEquals(event.data.temporal.tEvent, parsed.data.temporal.tEvent)
        assertEquals(event.data.temporal.tObserve, parsed.data.temporal.tObserve)
        assertEquals(event.data.temporal.tAdmit, parsed.data.temporal.tAdmit)
        assertEquals(event.data.temporal.tPresent, parsed.data.temporal.tPresent)
        assertEquals(event.time, parsed.time)
        assertEquals(event.data.temporal.tPresent.toString(), parsed.time)
        dumpLocks(event)
        val payloadJson = File(vectors, "envelope-payload.json").readText()
        assertEquals(payloadJson, Jcs.of(event.data.toCanonical()))
        val payload = parseJson(payloadJson) as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val again = parseStamp(payload["temporal"] as Map<String, Any?>)
        assertEquals(event.data.temporal, again)
    }

    @Test
    fun EN_005_truth_round_trip() {
        val event = fixture()
        val parsed = parseEvent(encodeEvent(event))
        assertEquals(TruthClass.FACT, parsed.data.truth.truthClass)
        assertEquals(Provenance.OBSERVED_SIGNED, parsed.data.truth.provenance)
        assertEquals("ver-1", parsed.data.truth.ref)
        val hypo = pack(
            type = TYPE_BELIEF_ADMITTED,
            sourceId = "calendar",
            subject = "trip.milano",
            stamp = stamp(),
            truth = TruthBearer(TruthClass.HYPOTHESIS, Provenance.DERIVED_MODEL, "model:slot"),
            content = mapOf("slot" to "09:00"),
            attestation = lockV2Attestation()
        )
        val hypoParsed = parseEvent(encodeEvent(hypo))
        assertEquals(TruthClass.HYPOTHESIS, hypoParsed.data.truth.truthClass)
        assertEquals(Provenance.DERIVED_MODEL, hypoParsed.data.truth.provenance)
        assertEquals("model:slot", hypoParsed.data.truth.ref)
        assertEquals(null, hypo.data.foldRef)
        assertFalse(encodeEvent(hypo).contains("fold_ref"))
    }

    @Test
    fun EN_006_key_order_commutes() {
        val event = fixture()
        val canonical = encodeEvent(event)
        val shuffledJson = shuffled(canonical)
        assertNotEquals(canonical, shuffledJson)
        val parsed = parseEvent(shuffledJson)
        assertEquals(event.id, parsed.id)
        assertEquals(canonical, encodeEvent(parsed))
        assertEquals(canonical, Jcs.ofJson(shuffledJson))
        val payloadCanonical = Jcs.of(event.data.toCanonical())
        assertEquals(payloadCanonical, Jcs.ofJson(shuffled(payloadCanonical)))
        dumpLocks(event)
        val eventJson = File(vectors, "envelope-event.json").readText()
        assertEquals(canonical, eventJson)
    }

    @Test
    fun EN_007_lock_vector_interoperability() {
        dumpLocks()
        for (file in listOf(tmOrder, tmDedup, tmFold, truthVectors)) {
            val raw = file.readText()
            assertEquals(raw, Jcs.ofJson(raw), file.name)
            assertEquals(raw, Jcs.of(parseJson(raw)), file.name)
        }
        val event = fixture()
        val eventLock = File(vectors, "envelope-event.json").readText()
        assertEquals(eventLock, encodeEvent(event))
        assertEquals(eventLock, Jcs.ofJson(eventLock))
        val parsed = parseEvent(eventLock)
        assertEquals(event.id, parsed.id)
        assertEquals(event.data.foldRef, parsed.data.foldRef)
        assertEquals(mapOf("depart" to "09:30"), parsed.data.content)
    }

    @Test
    fun PV_001_v2_output_type_is_io_a3ep() {
        val event = fixture()
        assertEquals(TYPE_BELIEF_ADMITTED, event.type)
        assertFalse(event.type.startsWith("a3."))
        assertTrue(encodeEvent(event).contains("\"type\":\"$TYPE_BELIEF_ADMITTED\""))
        assertFalse(encodeEvent(event).contains("\"type\":\"a3.belief.admitted\""))
        dumpLocks(event)
    }

    @Test
    fun PV_002_v1_type_input_normalizes_to_io_a3ep() {
        val v1 = File(root, "conformance/vectors/v1/envelope-event.json").readText()
        assertTrue(v1.contains("\"type\":\"a3.belief.admitted\""))
        val parsed = parseEvent(v1)
        assertEquals(TYPE_BELIEF_ADMITTED, parsed.type)
        assertEquals(null, parsed.data.attestation)
        assertEquals(
            "477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a",
            parsed.id
        )
        val packed = pack(
            type = "a3.belief.admitted",
            sourceId = "train",
            subject = "trip.milano",
            stamp = stamp(),
            truth = truth(),
            content = mapOf("depart" to "09:30"),
            foldRef = foldRef(),
            attestation = lockV2Attestation()
        )
        assertEquals(TYPE_BELIEF_ADMITTED, packed.type)
    }

    @Test
    fun PV_003_v2_payload_carries_attestation() {
        val event = fixture()
        val att = event.data.attestation ?: error("missing attestation")
        assertEquals(LOCK_V2_ATTESTER, att.attesterId)
        assertEquals(LOCK_V2_REQUESTER, att.requesterId)
        val payload = Jcs.of(event.data.toCanonical())
        assertTrue(payload.contains("\"attester_id\""))
        assertTrue(payload.contains("\"requester_id\""))
        val parsed = parseEvent(encodeEvent(event))
        assertEquals(att, parsed.data.attestation)
    }

    @Test
    fun PV_004_equal_attester_requester_on_irreversible_rejects() {
        val reject = assertFailsWith<EnvelopeReject> {
            pack(
                type = TYPE_ACTION_AUTHORIZED,
                sourceId = "train",
                subject = "trip.milano",
                stamp = stamp(),
                truth = truth(),
                content = mapOf("irreversible" to true),
                foldRef = foldRef(),
                attestation = Attestation("urn:a3:party:alice", "urn:a3:party:alice")
            )
        }
        assertTrue(reject.message!!.contains("attester_id must not equal requester_id"))
        val reversible = pack(
            type = TYPE_BELIEF_ADMITTED,
            sourceId = "train",
            subject = "trip.milano",
            stamp = stamp(),
            truth = truth(),
            content = mapOf("depart" to "09:30"),
            foldRef = foldRef(),
            attestation = Attestation("urn:a3:party:alice", "urn:a3:party:alice")
        )
        assertEquals(TYPE_BELIEF_ADMITTED, reversible.type)
    }

    @Test
    fun EN_008_freeze_only_envelope() {
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
            "a3ui/", "renderers/", "broker/", "agent/", "launcher/", "overlay/", "showcase/"
        )
        assertTrue(frozen.isBlank(), frozen)
        val status = ProcessBuilder("git", "status", "--porcelain")
            .directory(root).redirectErrorStream(true).start()
        val porcelain = status.inputStream.bufferedReader().readText()
        assertEquals(0, status.waitFor())
        val allowed = listOf(
            "core/envelope/",
            "core/confidence/",
            "core/truth/",
            "conformance/",
            "spec/",
            "REVIEW_PROTOCOL_V2.md",
            "REVIEW_CORE_ENVELOPE.md",
            "settings.gradle.kts",
            "CHANGELOG.md",
            "docs/",
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
        assertTrue(settings.contains(":core:envelope"))
        val production = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("a3.core.envelope")
        noClasses()
            .that().resideInAPackage("a3.core.envelope..")
            .should().callMethod(Instant::class.java, "now")
            .check(production)
        noClasses()
            .that().resideInAPackage("a3.core.envelope..")
            .should().callMethod(UUID::class.java, "randomUUID")
            .check(production)
        noClasses()
            .that().resideInAPackage("a3.core.envelope..")
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
        val main = File("src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(main.isNotEmpty())
        for (file in main) {
            val text = file.readText()
            assertFalse(text.contains("Instant.now"), file.path)
            assertFalse(text.contains("UUID.randomUUID"), file.path)
            assertFalse(text.contains("java.util.UUID"), file.path)
        }
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("project(\":core:temporal\")"))
        assertTrue(gradle.contains("project(\":core:truth\")"))
        assertTrue(gradle.contains("project(\":core:json\")"))
        assertTrue(gradle.contains("io.github.erdtman:java-json-canonicalization"))
        assertFalse(gradle.contains("project(\":core:action\")"))
        assertFalse(gradle.contains("project(\":a3ui\")"))
        val jcs = File("src/main/kotlin/a3/core/envelope/Jcs.kt").readText()
        assertTrue(jcs.contains("org.erdtman.jcs.JsonCanonicalizer"))
        val review = File(root, "REVIEW_CORE_ENVELOPE.md")
        assertTrue(review.exists(), "REVIEW_CORE_ENVELOPE.md missing")
        val body = review.readText()
        assertTrue(body.contains("EN-001"))
        assertTrue(body.contains("EN-008"))
        assertTrue(body.contains("envelope-event.json"))
        assertTrue(body.contains("envelope-payload.json"))
        assertTrue(body.contains("envelope-rfc8785.json"))
        assertTrue(body.contains("JsonCanonicalizer") || body.contains("RFC 8785"))
    }
}
