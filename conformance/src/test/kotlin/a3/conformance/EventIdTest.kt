package a3.conformance

import a3.core.envelope.Jcs
import a3.core.envelope.parseEvent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class EventIdTest {
    @Test
    fun CS_004_canonical_id_matches_event_id_expected() {
        val vectors = ConformancePaths.vectors()
        val expected = ConformanceIo.eventIdExpected()
        assertEquals(ConformanceIo.EVENT_ID, expected)
        val payload = File(vectors, "envelope-payload.json").readBytes()
        assertEquals(expected, ConformanceIo.sha256Hex(payload))
        assertEquals(expected, Jcs.sha256Hex(payload))
        val event = parseEvent(File(vectors, "envelope-event.json").readText())
        assertEquals(expected, event.id)
        val hashes = ConformanceIo.readTree(File(vectors, "envelope-hashes.json"))
        assertEquals(expected, hashes.path("event_id").asText())
        assertEquals(expected, hashes.path("envelope-payload.json").asText())
        println("PASS CS-004 event_id=$expected")
    }
}
