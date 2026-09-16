// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.conformance

import a3.core.envelope.TYPE_ACTION_AUTHORIZED
import a3.core.envelope.TYPE_BELIEF_ADMITTED
import a3.core.envelope.parseEvent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolV2Test {
    @Test
    fun PV_001_v2_event_type_is_io_a3ep() {
        val json = File(ConformancePaths.vectorsV2(), "envelope-event.json").readText()
        assertTrue(json.contains("\"type\":\"$TYPE_BELIEF_ADMITTED\""))
        assertFalse(json.contains("\"type\":\"a3.belief.admitted\""))
        assertEquals(TYPE_BELIEF_ADMITTED, parseEvent(json).type)
        println("PASS PV-001 type=${parseEvent(json).type}")
    }

    @Test
    fun PV_002_v1_type_normalizes_on_parse() {
        val json = File(ConformancePaths.vectorsV1(), "envelope-event.json").readText()
        assertTrue(json.contains("\"type\":\"a3.belief.admitted\""))
        val parsed = parseEvent(json)
        assertEquals(TYPE_BELIEF_ADMITTED, parsed.type)
        println("PASS PV-002 normalized=${parsed.type}")
    }

    @Test
    fun PV_003_v2_attestation_present() {
        val parsed = parseEvent(File(ConformancePaths.vectorsV2(), "envelope-event.json").readText())
        val att = parsed.data.attestation ?: error("missing attestation")
        assertEquals("urn:a3:party:attester", att.attesterId)
        assertEquals("urn:a3:party:requester", att.requesterId)
        val v1 = parseEvent(File(ConformancePaths.vectorsV1(), "envelope-event.json").readText())
        assertNull(v1.data.attestation)
        println("PASS PV-003 attester=${att.attesterId}")
    }

    @Test
    fun PV_004_cf004_rejects_in_envelope() {
        val file = File(ConformancePaths.fixtures(), "attester-requester-violation.json")
        val thrown = kotlin.test.assertFailsWith<ConformanceReject> {
            CategoryJudge.judgeFile(file)
        }
        assertEquals("CF-004", thrown.code)
        val event = ConformanceIo.readTree(file).path("event")
        assertEquals(TYPE_ACTION_AUTHORIZED, event.path("type").asText())
        assertEquals(
            event.path("data").path("attestation").path("attester_id").asText(),
            event.path("data").path("attestation").path("requester_id").asText()
        )
        println("PASS PV-004 ${thrown.message}")
    }

    @Test
    fun PV_006_v1_bytes_intact() {
        val v1 = File(ConformancePaths.vectorsV1(), "envelope-event.json").readText()
        assertTrue(v1.contains("a3.belief.admitted"))
        assertFalse(v1.contains("attestation"))
        assertEquals(
            ConformanceIo.EVENT_ID_V1,
            ConformanceIo.sha256Hex(File(ConformancePaths.vectorsV1(), "envelope-payload.json"))
        )
        println("PASS PV-006 v1 intact")
    }

    @Test
    fun PV_007_v2_sha256_declared() {
        val manifest = ConformanceIo.readTree(File(ConformancePaths.vectorsV2(), "vector-sha256.json"))
        for (name in ConformanceIo.lockVectors) {
            assertEquals(
                ConformanceIo.expectedSha256V2.getValue(name),
                manifest.path(name).asText(),
                name
            )
        }
        println("PASS PV-007 v2 sha declared")
    }

    @Test
    fun PV_008_spec_is_v02_with_registry_and_attestation() {
        val spec = File(ConformancePaths.repoRoot(), "spec/SPEC_A3-EP.md").readText()
        assertTrue(spec.contains("Version: 0.2.0"))
        assertTrue(spec.contains("io.a3ep.belief.admitted"))
        assertTrue(spec.contains("io.a3ep.action.authorized"))
        assertTrue(spec.contains("io.a3ep.env.postcondition"))
        assertTrue(spec.contains("attestation"))
        println("PASS PV-008 spec 0.2.0")
    }
}
