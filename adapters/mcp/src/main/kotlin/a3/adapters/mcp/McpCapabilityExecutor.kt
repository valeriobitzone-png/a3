package a3.adapters.mcp

import a3.core.model.Capability
import a3.core.model.Observation
import a3.core.runtime.Executor
import java.time.Instant

/**
 * In-process tools/call. Implements the existing [Executor] port and stops at [Observation].
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
}
