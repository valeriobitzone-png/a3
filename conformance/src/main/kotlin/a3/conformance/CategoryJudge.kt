// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.conformance

import a3.core.action.ExecutionReceipt
import a3.core.action.receiptCannotClaimDomain
import a3.core.confidence.AggregationWeights
import a3.core.confidence.aggregate
import a3.core.confidence.parseVector
import a3.core.envelope.parseEvent
import a3.core.envelope.EnvelopeReject
import a3.core.temporal.TemporalOrder
import a3.core.temporal.fold
import a3.core.truth.Provenance
import a3.core.truth.TruthBearer
import a3.core.truth.TruthClass
import a3.core.truth.TruthReject
import a3.core.truth.VerificationAdmitted
import a3.core.truth.VerificationEnvironment
import a3.core.truth.fromProcessOutcome
import a3.core.truth.fromRealAdmitted
import a3.core.truth.promoteHypothesis
import com.fasterxml.jackson.databind.JsonNode
import java.io.File
import java.time.Instant

object CategoryJudge {
    fun judgeFile(file: File): Nothing {
        val root = ConformanceIo.readTree(file)
        val code = root.path("reject_code").asText()
        when (code) {
            "CF-001" -> judgeReceipt(root)
            "CF-002" -> judgeSandbox(root)
            "CF-003" -> judgePromotion(root)
            "CF-004" -> judgeAttester(root)
            "CF-005" -> judgeUnknown(root)
            "CF-006" -> judgeHistoryFold(root)
            "CF-007" -> judgeMean(root)
            "CF-008" -> judgeEnvelopeId(root)
            "CF-009" -> judgeOrder(root)
            else -> throw ConformanceReject("CF-UNKNOWN", "unknown reject_code $code")
        }
    }

    private fun claimedClass(node: JsonNode): String =
        node.path("claimed").path("truth_class").asText().trim().uppercase()

    private fun judgeReceipt(root: JsonNode): Nothing {
        check(root.path("kind").asText() == "receipt") { "CF-001 fixture is not a receipt" }
        judgeReceiptClaim(claimedClass(root), root)
        error("CF-001 fixture did not claim FACT on receipt")
    }

    /**
     * CF-001 as a semantic property (SPEC_A3-EP sections 2, 3, 10): a receipt
     * is OBSERVATION whatever it contains, and a FACT claim on a receipt is
     * rejected whatever it contains. Exit code, printed text, status and
     * payload only shape the ref; they never decide the verdict.
     * Returns the lawful bearer when the claim is not FACT.
     */
    fun judgeReceiptClaim(claimed: String, receipt: JsonNode): TruthBearer {
        val exitNode = receipt.path("exit_code")
        val exit = if (exitNode.isInt) exitNode.asInt() else 0
        val printed = receipt.path("printed").takeIf { it.isTextual }?.asText()
            ?: receipt.toString()
        val lawful = fromProcessOutcome(exit, printed)
        if (lawful.truthClass == TruthClass.FACT) {
            error("classifier emitted FACT for a receipt")
        }
        if (claimed.trim().uppercase() != "FACT") {
            return lawful
        }
        try {
            receiptCannotClaimDomain(
                ExecutionReceipt(
                    receiptId = "receipt-cf-001",
                    commandId = "cmd-cf-001",
                    dispatchId = "dispatch-cf-001",
                    ok = !exitNode.isInt || exit == 0,
                    at = Instant.parse("2026-08-27T11:00:00Z")
                )
            )
        } catch (e: IllegalStateException) {
            throw ConformanceReject(
                "CF-001",
                "FACT from an execution receipt (a receipt is OBSERVATION whatever it contains)"
            )
        }
    }

    private fun judgeSandbox(root: JsonNode): Nothing {
        val verification = VerificationAdmitted(
            verificationId = root.path("verification_id").asText(),
            admittedId = root.path("admitted_id").asText(),
            environment = VerificationEnvironment.SANDBOX
        )
        try {
            fromRealAdmitted(verification)
        } catch (e: TruthReject) {
            if (claimedClass(root) == "FACT") {
                throw ConformanceReject("CF-002", "FACT on sandbox (not REAL): ${e.message}")
            }
            throw e
        }
        error("implementation emitted FACT from SANDBOX")
    }

    private fun judgePromotion(root: JsonNode): Nothing {
        val bearerNode = root.path("bearer")
        val bearer = TruthBearer(
            truthClass = TruthClass.valueOf(bearerNode.path("truth_class").asText().trim().uppercase()),
            provenance = Provenance.valueOf(bearerNode.path("provenance").asText().trim().uppercase()),
            ref = bearerNode.path("ref").takeIf { !it.isMissingNode && !it.isNull }?.asText()
        )
        val verification = if (root.get("verification") == null || root.get("verification").isNull) {
            null
        } else {
            error("CF-003 fixture must omit VerificationAdmitted")
        }
        val stayed = promoteHypothesis(bearer, verification)
        if (stayed.truthClass == TruthClass.FACT) {
            error("implementation promoted HYPOTHESIS to FACT without VerificationAdmitted")
        }
        if (claimedClass(root) == "FACT") {
            throw ConformanceReject(
                "CF-003",
                "HYPOTHESIS promoted to FACT without VerificationAdmitted"
            )
        }
        error("CF-003 fixture did not claim FACT")
    }

    private fun judgeAttester(root: JsonNode): Nothing {
        val eventNode = root.path("event")
        if (eventNode.isMissingNode || eventNode.isNull) {
            error("CF-004 fixture must carry an envelope event")
        }
        val json = ConformanceIo.mapper.writeValueAsString(eventNode)
        try {
            parseEvent(json)
        } catch (e: EnvelopeReject) {
            val reason = e.message.orEmpty()
            if (reason.contains("attester_id must not equal requester_id")) {
                throw ConformanceReject("CF-004", reason)
            }
            throw e
        }
        error("implementation accepted attester_id = requester_id on irreversible action")
    }

    private fun judgeUnknown(root: JsonNode): Nothing {
        val truth = root.path("truth_class").asText().trim().uppercase()
        val proposition = root.get("proposition")
        val invented = proposition != null && !proposition.isNull && proposition.asText().isNotBlank()
        if (truth == "UNKNOWN" && invented) {
            throw ConformanceReject(
                "CF-005",
                "UNKNOWN with invented conclusion (proposition not null)"
            )
        }
        error("CF-005 fixture is not UNKNOWN with a proposition")
    }

    private fun judgeHistoryFold(root: JsonNode): Nothing {
        val vectorName = root.path("history_vector").asText()
        val history = ConformanceIo.observationsFrom(
            ConformanceIo.readTree(File(ConformancePaths.vectorsV2(), vectorName))
        )
        val folded = fold(history)
        val kept = folded.sumOf { it.history.size }
        if (kept != history.size) {
            error("implementation folded causal history ($kept != ${history.size})")
        }
        val claimedEmpty = root.path("claimed").path("history")
        val replaces = root.path("claimed").path("replaces_history").asBoolean(false)
        if (replaces || (claimedEmpty.isArray && claimedEmpty.size() == 0)) {
            throw ConformanceReject(
                "CF-006",
                "fold applied to causal history (not confidence/projection)"
            )
        }
        error("CF-006 fixture did not replace history")
    }

    private fun judgeMean(root: JsonNode): Nothing {
        val vector = parseVector(ConformanceIo.jsonMap(root.path("vector")))
        val w = root.path("weights")
        val weights = AggregationWeights(
            sourceReliability = w.path("source_reliability").asDouble(),
            evidenceStrength = w.path("evidence_strength").asDouble(),
            recency = w.path("recency").asDouble(),
            corroboration = w.path("corroboration").asDouble(),
            verification = w.path("verification").asDouble()
        )
        val lawful = aggregate(vector, weights)
        val claimed = root.path("claimed_score").asDouble()
        val method = root.path("aggregation").asText()
        if (method.equals("mean", ignoreCase = true) || claimed != lawful) {
            throw ConformanceReject(
                "CF-007",
                "confidence aggregated with compensating average (claimed=$claimed lawful=$lawful)"
            )
        }
        error("CF-007 fixture matched weighted min")
    }

    private fun judgeEnvelopeId(root: JsonNode): Nothing {
        val eventJson = ConformanceIo.mapper.writeValueAsString(root.path("event"))
        try {
            parseEvent(eventJson)
        } catch (e: EnvelopeReject) {
            throw ConformanceReject("CF-008", "envelope id ≠ SHA-256(JCS(payload)): ${e.message}")
        }
        error("implementation accepted envelope id ≠ SHA-256(JCS(payload))")
    }

    private fun judgeOrder(root: JsonNode): Nothing {
        val input = ConformanceIo.observationsFrom(root.path("input"))
        val lawful = input.sortedWith(TemporalOrder).map { it.sourceId.value }
        val claimed = root.path("claimed_order").map { it.asText() }
        val tie = root.path("tie_break").asText()
        if (tie == "none" || claimed != lawful) {
            throw ConformanceReject(
                "CF-009",
                "order without tie-break (source_id, seq); claimed=$claimed lawful=$lawful"
            )
        }
        error("CF-009 fixture already used the protocol order")
    }
}
