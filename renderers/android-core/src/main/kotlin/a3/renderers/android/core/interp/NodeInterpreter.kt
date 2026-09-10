package a3.renderers.android.core.interp

import a3.a3ui.engine.BindingCopy
import a3.a3ui.model.Binding
import a3.a3ui.model.Node
import a3.projection.model.PresentationState
import a3.renderers.android.core.model.CatalogProfile
import a3.renderers.android.core.model.RenderedNode
import java.util.ArrayList

/**
 * Resolves A3UI Node/Binding into a renderer-owned data tree.
 * Unknown roles are refused. Copy comes from BindingCopy; missing atom → empty string.
 */
object NodeInterpreter {
    val ROLES = listOf("stack", "row", "list", "item", "action", "field", "text")

    fun interpret(
        nodes: List<Node>,
        bindings: List<Binding>,
        presentation: PresentationState,
        profile: CatalogProfile = CatalogProfile.V2
    ): List<RenderedNode> {
        val bound = ArrayList(bindings)
        bound.sortWith(compareBy({ it.nodeId }, { it.role }, { it.atomKey }))
        val out = ArrayList<RenderedNode>(nodes.size)
        for (node in nodes) {
            out += render(node, bound, presentation, profile)
        }
        return out
    }

    fun ids(nodes: List<RenderedNode>): List<String> {
        val out = ArrayList<String>()
        fun walk(node: RenderedNode) {
            out += node.id
            for (child in node.children) walk(child)
        }
        for (node in nodes) walk(node)
        return out
    }

    private fun render(
        node: Node,
        bindings: List<Binding>,
        presentation: PresentationState,
        profile: CatalogProfile
    ): RenderedNode {
        if (ROLES.none { it == node.role }) {
            throw IllegalArgumentException("unknown node role ${node.role}")
        }
        if (node.role == "list") {
            for (child in node.children) {
                if (child.role != "item") {
                    throw IllegalArgumentException("list child must be item")
                }
            }
        }
        val children = ArrayList<RenderedNode>(node.children.size)
        for (child in node.children) {
            children += render(child, bindings, presentation, profile)
        }
        var text = ""
        var hint = ""
        for (binding in bindings) {
            if (binding.nodeId != node.id) continue
            val copy = stringify(BindingCopy.of(binding, presentation))
            when (binding.role) {
                "content", "value" -> text = copy
                "placeholder" -> hint = copy
                else -> throw IllegalArgumentException("unknown binding role ${binding.role}")
            }
        }
        val expose = profile == CatalogProfile.V2
        val axis = if (expose) node.axis?.takeUnless { it.isDefault() } else null
        val description = axis?.stateDescription() ?: ""
        val accessible = if (expose && axis != null) axis.accessibleName(text) else ""
        return RenderedNode(
            id = node.id,
            role = node.role,
            children = children,
            text = text,
            hint = hint,
            axis = axis,
            stateDescription = description,
            accessibleName = accessible
        )
    }

    private fun stringify(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        else -> value.toString()
    }
}
