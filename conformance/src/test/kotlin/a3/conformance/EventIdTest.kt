// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.conformance

import a3.core.envelope.Jcs
import a3.core.envelope.parseEvent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class EventIdTest {
    @Test
    fun CS_004_and_PV_005_canonical_ids_match_v1_and_v2() {
        val v1Dir = ConformancePaths.vectorsV1()
        val v2Dir = ConformancePaths.vectorsV2()
        val v1 = ConformanceIo.eventIdExpected(v1Dir)
        val v2 = ConformanceIo.eventIdExpected(v2Dir)
        assertEquals(ConformanceIo.EVENT_ID_V1, v1)
        assertEquals(ConformanceIo.EVENT_ID_V2, v2)
        assertEquals(v1, ConformanceIo.sha256Hex(File(v1Dir, "envelope-payload.json")))
        assertEquals(v2, ConformanceIo.sha256Hex(File(v2Dir, "envelope-payload.json")))
        assertEquals(v2, Jcs.sha256Hex(File(v2Dir, "envelope-payload.json").readBytes()))
        val parsedV2 = parseEvent(File(v2Dir, "envelope-event.json").readText())
        assertEquals(v2, parsedV2.id)
        val parsedV1 = parseEvent(File(v1Dir, "envelope-event.json").readText())
        assertEquals(v1, parsedV1.id)
        val hashesV2 = ConformanceIo.readTree(File(v2Dir, "envelope-hashes.json"))
        assertEquals(v2, hashesV2.path("event_id").asText())
        assertEquals(v2, hashesV2.path("envelope-payload.json").asText())
        println("PASS CS-004/PV-005 event_id_v1=$v1 event_id_v2=$v2")
    }
}
