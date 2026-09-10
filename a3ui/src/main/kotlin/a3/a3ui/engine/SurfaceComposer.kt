package a3.a3ui.engine

import a3.a3ui.model.Binding
import a3.a3ui.model.GestureBinding
import a3.a3ui.model.GestureMap
import a3.a3ui.model.Node
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import java.time.Instant
import java.util.ArrayList
import java.util.TreeMap

data class ComposedTree(
    val nodes: List<Node>,
    val bindings: List<Binding>,
    val gestureTargets: TreeMap<String, String>
)

/**
 * Builds a closed-catalog node tree from presentation atoms. Hierarchy is children.
 */
object SurfaceComposer {
    val ROLES = listOf("stack", "row", "list", "item", "action", "field", "text")
    val ACTIONS = listOf("confirm", "dismiss", "select", "next-step")

    fun compose(
        presentation: PresentationState,
        asOf: Instant,
        factsByKey: Map<String, EpistemicFacts>
    ): ComposedTree = Epistemic.attach(compose(presentation), asOf, factsByKey)

    fun compose(presentation: PresentationState): ComposedTree {
        val atoms = presentation.atoms.sortedWith(compareBy({ it.k }, { it.meaning }))
        val bindings = ArrayList<Binding>()
        val targets = TreeMap<String, String>()
        val rootChildren = ArrayList<Node>()

        val steps = atoms.filter { it.meaning == "step" }
        val slots = atoms.filter { it.meaning == "timetable" }
        val stations = atoms.filter { it.meaning == "station" }
        val selects = atoms.filter { it.meaning == "select" }
        val passengers = atoms.filter { it.meaning == "passenger" }
        val prices = atoms.filter { it.meaning == "price" }
        val confirms = atoms.filter { it.meaning == "confirm" || it.k.endsWith(".owned") }
        val used = TreeMap<String, PresentationAtom>()
        for (atom in steps + slots + stations + selects + passengers + prices + confirms) {
            used[atom.k] = atom
        }

        if (steps.isNotEmpty()) {
            val items = ArrayList<Node>()
            steps.forEachIndexed { index, atom ->
                val last = index == steps.size - 1
                val actionId = "action_${atom.k}"
                val itemId = "item_${atom.k}"
                val action = if (last) "confirm" else "next-step"
                items += Node(itemId, "item", listOf(Node(actionId, "action")))
                bindings += Binding(atom.k, actionId, "content")
                targets[action] = actionId
            }
            rootChildren += Node("steps", "stack", items)
        }

        if (slots.isNotEmpty()) {
            val items = ArrayList<Node>()
            for (atom in slots) {
                val id = "item_${atom.k}"
                items += Node(id, "item")
                bindings += Binding(atom.k, id, "content")
            }
            rootChildren += Node("timetable", "list", items)
        }

        if (stations.isNotEmpty() || selects.isNotEmpty()) {
            val rowKids = ArrayList<Node>()
            for (atom in stations) {
                val id = "text_${atom.k}"
                rowKids += Node(id, "text")
                bindings += Binding(atom.k, id, "content")
            }
            for (atom in selects) {
                val id = "action_${atom.k}"
                rowKids += Node(id, "action")
                bindings += Binding(atom.k, id, "content")
                targets["select"] = id
            }
            rootChildren += Node("choice", "row", rowKids)
        }

        for (atom in passengers) {
            val id = "field_${atom.k}"
            rootChildren += Node(id, "field")
            bindings += Binding(atom.k, id, "value")
        }
        for (atom in prices) {
            val id = "text_${atom.k}"
            rootChildren += Node(id, "text")
            bindings += Binding(atom.k, id, "content")
        }
        for (atom in confirms) {
            if (atom.meaning == "step") continue
            val id = "action_${atom.k}"
            if (rootChildren.any { containsId(it, id) }) continue
            rootChildren += Node(id, "action")
            bindings += Binding(atom.k, id, "content")
            targets["confirm"] = id
        }

        for (atom in atoms) {
            if (used.containsKey(atom.k)) continue
            val id = "text_${atom.k}"
            rootChildren += Node(id, "text")
            bindings += Binding(atom.k, id, "content")
            if (atom.k.endsWith(".owned")) targets.putIfAbsent("confirm", id)
        }

        val nodes = listOf(Node("root", "stack", rootChildren))
        targets.putIfAbsent("dismiss", "root")
        bindings.sortWith(compareBy({ it.atomKey }, { it.nodeId }, { it.role }))
        return ComposedTree(nodes, bindings, targets)
    }

    fun collectIds(nodes: List<Node>): List<String> {
        val ids = ArrayList<String>()
        fun walk(node: Node) {
            ids += node.id
            for (child in node.children) walk(child)
        }
        for (node in nodes) walk(node)
        ids.sort()
        return ids
    }

    fun gestures(requirements: List<String>, targets: TreeMap<String, String>): GestureMap {
        val req = ArrayList(requirements)
        req.sort()
        val bindings = ArrayList<GestureBinding>()
        fun add(gesture: String, action: String) {
            val target = targets[action]
                ?: throw IllegalArgumentException("gesture target missing for action $action")
            bindings += GestureBinding(gesture, action, target)
        }
        if (req.isNotEmpty() || targets.containsKey("dismiss")) add("swipe-left", "dismiss")
        if (req.contains("confirm") || targets.containsKey("confirm")) add("confirm", "confirm")
        if (targets.containsKey("select")) add("select", "select")
        if (targets.containsKey("next-step")) add("next-step", "next-step")
        bindings.sortWith(compareBy({ it.gesture }, { it.action }, { it.targetNodeId }))
        return GestureMap(bindings)
    }

    private fun containsId(node: Node, id: String): Boolean {
        if (node.id == id) return true
        return node.children.any { containsId(it, id) }
    }
}
