package a3.core.t12

import a3.core.confidence.ConfidenceCategory
import a3.core.confidence.categoryOf
import a3.core.truth.Provenance
import a3.core.truth.TruthClass
import kotlin.math.abs

data class InvariantCheck(
    val id: String,
    val pass: Boolean,
    val detail: String
)

data class ProbeReport(
    val call: GeminiCall,
    val admitted: AdmittedProbe,
    val checks: List<InvariantCheck>
) {
    fun check(id: String): InvariantCheck =
        checks.first { it.id == id }
}

fun runProbe(): ProbeReport {
    val call = GeminiClient.generate(SYSTEM_PROMPT, userPrompt(), responseSchemaJson())
    val admitted = admitFromModel(call.text)
    return ProbeReport(call, admitted, evaluate(admitted))
}

fun evaluate(admitted: AdmittedProbe): List<InvariantCheck> {
    val truth = admitted.event.data.truth
    val stamp = admitted.event.data.temporal
    val t12_001 = when (truth.truthClass) {
        TruthClass.FACT -> InvariantCheck("T12-001", false, "truthClass=FACT on a receipt")
        TruthClass.OBSERVATION -> InvariantCheck("T12-001", true, "truthClass=OBSERVATION")
        TruthClass.UNKNOWN -> InvariantCheck("T12-001", true, "UNKNOWN accepted for insufficient evidence")
        TruthClass.HYPOTHESIS -> InvariantCheck("T12-001", false, "truthClass=HYPOTHESIS, expected OBSERVATION or UNKNOWN")
    }
    val t12_002 = when {
        truth.truthClass == TruthClass.UNKNOWN &&
            (truth.provenance == Provenance.OBSERVED_SIGNED ||
                truth.provenance == Provenance.DERIVED_MODEL ||
                truth.provenance == Provenance.INFERRED) ->
            InvariantCheck("T12-002", true, "provenance=${truth.provenance} with UNKNOWN")
        truth.provenance == Provenance.OBSERVED_SIGNED ||
            truth.provenance == Provenance.DERIVED_MODEL ->
            InvariantCheck("T12-002", true, "provenance=${truth.provenance}")
        else -> InvariantCheck("T12-002", false, "provenance=${truth.provenance}")
    }
    val t12_003 = InvariantCheck(
        "T12-003",
        !admitted.postconditionVerified,
        "postcondition_verified=${admitted.postconditionVerified}"
    )
    val t12_004 = InvariantCheck(
        "T12-004",
        admitted.event.specversion == "1.0" &&
            admitted.event.datacontenttype == "application/json" &&
            admitted.canonicalJson.isNotBlank(),
        "specversion=${admitted.event.specversion}"
    )
    val t12_005 = InvariantCheck(
        "T12-005",
        admitted.event.id == admitted.expectedId &&
            admitted.expectedId.matches(Regex("[0-9a-f]{64}")),
        "id=${admitted.event.id}"
    )
    val timesOk = !stamp.tObserve.isBefore(stamp.tEvent) &&
        !stamp.tAdmit.isBefore(stamp.tObserve) &&
        admitted.event.time == stamp.tPresent.toString()
    val t12_006 = InvariantCheck(
        "T12-006",
        timesOk,
        "t_event=${stamp.tEvent} t_observe=${stamp.tObserve} t_admit=${stamp.tAdmit} t_present=${stamp.tPresent}"
    )
    val t12_007 = evaluateConfidence(admitted, truth.truthClass)
    val t12_008 = InvariantCheck(
        "T12-008",
        allowedTruth(truth.truthClass),
        "truthClass=${truth.truthClass}"
    )
    return listOf(t12_001, t12_002, t12_003, t12_004, t12_005, t12_006, t12_007, t12_008)
}

private fun evaluateConfidence(admitted: AdmittedProbe, truthClass: TruthClass): InvariantCheck {
    val vector = admitted.confidence ?: return InvariantCheck(
        "T12-007",
        true,
        "confidence omitted"
    )
    val computed = admitted.computedScore
        ?: return InvariantCheck("T12-007", false, "missing computed score")
    val reported = admitted.confidenceScore
    if (reported != null && abs(reported - computed) > 1e-6) {
        return InvariantCheck("T12-007", false, "score $reported != weighted_min $computed")
    }
    val category = categoryOf(computed, truthClass)
    val weakEvidence = vector.evidenceStrength <= 0.5 ||
        vector.verification <= 0.3 ||
        computed < 0.5 ||
        category == ConfidenceCategory.LOW ||
        category == ConfidenceCategory.UNKNOWN
    if (category == ConfidenceCategory.HIGH) {
        return InvariantCheck("T12-007", false, "HIGH confidence on unverified postcondition")
    }
    return InvariantCheck(
        "T12-007",
        weakEvidence,
        "score=$computed category=$category evidence=${vector.evidenceStrength}"
    )
}
