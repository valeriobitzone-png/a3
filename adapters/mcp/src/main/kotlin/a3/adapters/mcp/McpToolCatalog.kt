package a3.adapters.mcp

import a3.core.model.Capability
import java.util.TreeMap

/**
 * Declared tools/list: MCP tool id → A3 [Capability].
 * Mapping is injected; this class does not invent effects or provenance.
 */
class McpToolCatalog(tools: Map<String, Capability>) {
    private val tools: Map<String, Capability> = TreeMap(tools)

    fun list(): List<Capability> = ArrayList(tools.values)

    fun tool(id: String): Capability? = tools[id]
}
