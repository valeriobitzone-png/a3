package a3.adapters.mcp

import a3.core.action.ActionEvent
import a3.core.action.ExecutionReceipt
import a3.core.admission.ObservationCandidate
import a3.core.model.Capability
import a3.core.model.Observation
import a3.core.runtime.Executor
import a3.core.world.toCandidate
import java.time.Instant

/**
 * In-process tools/call. Implements the existing [Executor] port and stops at [Observation].
 * [call] is the admission-facing boundary: it returns [ObservationCandidate] only.
 * [receipt] stamps an [ExecutionReceipt] / [ActionEvent] and never writes believed reality.
 */
class McpCapabilityExecutor(
    private val catalog: McpToolCatalog,
    private val serverId: String
) : Executor {
    override fun execute(capability: Capability, now: Instant): Observation {
        val mapped = catalog.tool(capability.id)
            ?: throw IllegalArgumentException("unknown tool: ${capability.id}")
        val ref = "mcp://$serverId/${mapped.id}"
        val facts = mapped.effects.map { fact ->
            fact.copy(id = "", supersededBy = null)
        }
        return Observation(
            id = ref,
            executionRef = ref,
            t = now,
            facts = facts
        )
    }

    fun call(capability: Capability, now: Instant): ObservationCandidate =
        execute(capability, now).toCandidate()

    fun receipt(capability: Capability, now: Instant): ExecutionReceipt {
        val observation = execute(capability, now)
        return ExecutionReceipt(
            receiptId = observation.id,
            commandId = capability.id,
            dispatchId = observation.executionRef,
            ok = true,
            at = now
        )
    }

    fun actionEvent(receipt: ExecutionReceipt): ActionEvent =
        if (receipt.ok) ActionEvent.ExecutorCompleted(receipt.at, receipt.receiptId)
        else ActionEvent.ExecutorFailed(receipt.at, "executor failed")
}
