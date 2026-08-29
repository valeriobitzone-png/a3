package a3.renderers.android.core.interp

import a3.a3ui.model.GestureMap
import a3.renderers.android.core.model.SemanticGestureAction
import a3.renderers.android.core.model.SemanticGestureActions
import java.util.ArrayList

class GestureInterpreter {
    fun interpret(map: GestureMap): SemanticGestureActions = interpret(map, knownIds = null)

    fun interpret(map: GestureMap, knownIds: List<String>?): SemanticGestureActions {
        val actions = ArrayList<SemanticGestureAction>(map.bindings.size)
        for (binding in map.bindings) {
            if (knownIds != null && knownIds.none { it == binding.targetNodeId }) {
                throw IllegalArgumentException("unknown gesture target ${binding.targetNodeId}")
            }
            actions += SemanticGestureAction(
                gesture = binding.gesture,
                action = binding.action,
                targetNodeId = binding.targetNodeId
            )
        }
        actions.sortWith(compareBy({ it.gesture }, { it.action }, { it.targetNodeId }))
        return SemanticGestureActions(actions)
    }
}
