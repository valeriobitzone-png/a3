// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.conformance

import a3.core.admission.SourceId
import a3.core.envelope.parseStamp
import a3.core.temporal.TemporalObservation
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.security.MessageDigest

object ConformanceIo {
    val mapper: ObjectMapper = ObjectMapper()

    val lockVectors: List<String> = listOf(
        "tm-order.json",
        "tm-dedup.json",
        "tm-fold.json",
        "truth-vectors.json",
        "envelope-rfc8785.json",
        "envelope-payload.json",
        "envelope-event.json",
        "envelope-hashes.json",
        "confidence-vectors.json",
        "event_id_expected.txt"
    )

    val expectedSha256V1: Map<String, String> = mapOf(
        "tm-order.json" to "e805711fc39d48a59b47bfdd147737016db56a3a68511f27229e769691378a6e",
        "tm-dedup.json" to "b6b05fefb45b1f9ff2fc882d16eb5a1f0b1cf96e6d8455070836d472e333e0ba",
        "tm-fold.json" to "f80b9b513fc928d11e8aceb66a29d7cfb7540a0bb8451c9017630b105602e9f5",
        "truth-vectors.json" to "1ddb48779a470fd65adc59a5e0245767f07bd4ea91ad70b7c3e10afbdebab5d6",
        "envelope-rfc8785.json" to "2d5e01a318d0f0879ab568c4be289c8b1f64ef8921a53c6277d5e069978baacb",
        "envelope-payload.json" to "477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a",
        "envelope-event.json" to "a0cbf887413c9825638bd410da5689a217d895b0a391b367aa3c7cb003f0c07e",
        "envelope-hashes.json" to "2a318e245354fc67d566869abe6580f9d4c524b8e986efc0ce0fa1f55d9cdc87",
        "confidence-vectors.json" to "25efbc9f1b3730658c34502f2564d18a8aad04c1ee202b672be4b020917fddee",
        "event_id_expected.txt" to "97a3bb3a4ad58c8bd47f22ebe8292b5c86903c6956d73009f2a827d40cf38894"
    )

    val expectedSha256V2: Map<String, String> = mapOf(
        "tm-order.json" to "e805711fc39d48a59b47bfdd147737016db56a3a68511f27229e769691378a6e",
        "tm-dedup.json" to "b6b05fefb45b1f9ff2fc882d16eb5a1f0b1cf96e6d8455070836d472e333e0ba",
        "tm-fold.json" to "f80b9b513fc928d11e8aceb66a29d7cfb7540a0bb8451c9017630b105602e9f5",
        "truth-vectors.json" to "1ddb48779a470fd65adc59a5e0245767f07bd4ea91ad70b7c3e10afbdebab5d6",
        "envelope-rfc8785.json" to "2d5e01a318d0f0879ab568c4be289c8b1f64ef8921a53c6277d5e069978baacb",
        "envelope-payload.json" to "1fec213fbaf6d420cf9ff95c51c022c4cdfb1f43fabcf82647e03c03f92f2b7b",
        "envelope-event.json" to "fa6e007a23751ad55c22291b64982f0d7c8287eb5723b446a3a5fd72c470e939",
        "envelope-hashes.json" to "d27e67f05719b77daeb14a4d219a87cb332998fe2c6d163571f35e7b90d76ff5",
        "confidence-vectors.json" to "25efbc9f1b3730658c34502f2564d18a8aad04c1ee202b672be4b020917fddee",
        "event_id_expected.txt" to "c7cb220cb548ecb3be575faecac6d0d7e57cc18a785727732e5570c21bb68550"
    )

    val coreLockPathsV2: Map<String, String> = mapOf(
        "tm-order.json" to "core/temporal/src/test/resources/tm-order.json",
        "tm-dedup.json" to "core/temporal/src/test/resources/tm-dedup.json",
        "tm-fold.json" to "core/temporal/src/test/resources/tm-fold.json",
        "truth-vectors.json" to "core/truth/src/test/resources/truth-vectors.json",
        "envelope-rfc8785.json" to "core/envelope/src/test/resources/envelope-rfc8785.json",
        "envelope-payload.json" to "core/envelope/src/test/resources/envelope-payload.json",
        "envelope-event.json" to "core/envelope/src/test/resources/envelope-event.json",
        "envelope-hashes.json" to "core/envelope/src/test/resources/envelope-hashes.json",
        "confidence-vectors.json" to "core/confidence/src/test/resources/confidence-vectors.json"
    )

    val categoryFixtures: List<Pair<String, String>> = listOf(
        "receipt-fact-violation.json" to "CF-001",
        "sandbox-fact-violation.json" to "CF-002",
        "hypothesis-promotion-violation.json" to "CF-003",
        "attester-requester-violation.json" to "CF-004",
        "unknown-invention-violation.json" to "CF-005",
        "history-fold-violation.json" to "CF-006",
        "compensating-mean-violation.json" to "CF-007",
        "envelope-id-violation.json" to "CF-008",
        "order-tiebreak-violation.json" to "CF-009"
    )

    const val EVENT_ID_V1 =
        "477e868489f5c48d138e4c084e9bf13a40ed66390b964365869f7578dfa2e75a"

    const val EVENT_ID_V2 =
        "1fec213fbaf6d420cf9ff95c51c022c4cdfb1f43fabcf82647e03c03f92f2b7b"

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    fun sha256Hex(file: File): String = sha256Hex(file.readBytes())

    fun readTree(file: File): JsonNode = mapper.readTree(file)

    fun eventIdExpected(dir: File = ConformancePaths.vectorsV2()): String =
        File(dir, "event_id_expected.txt").readText().trim()

    fun observationsFrom(node: JsonNode): List<TemporalObservation> {
        require(node.isArray) { "observations must be a JSON array" }
        return node.map { observationFrom(it) }
    }

    fun observationFrom(node: JsonNode): TemporalObservation {
        val raw = jsonMap(node)
        val stampRaw = raw["stamp"] as? Map<*, *>
            ?: error("missing stamp")
        @Suppress("UNCHECKED_CAST")
        val stamp = parseStamp(stampRaw as Map<String, Any?>)
        return TemporalObservation(
            sourceId = SourceId(raw["source_id"].toString()),
            subject = raw["subject"].toString(),
            key = raw["key"].toString(),
            seq = (raw["seq"] as Number).toLong(),
            stamp = stamp,
            value = raw["value"]
        )
    }

    fun jsonMap(node: JsonNode): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return mapper.convertValue(node, Map::class.java) as Map<String, Any?>
    }
}
