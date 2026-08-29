package a3.renderers.android.core.interp

import a3.a3ui.model.GestureMap
import a3.renderers.android.core.model.SemanticGestureAction
import a3.renderers.android.core.model.SemanticGestureActions
import java.util.ArrayList

class GestureInterpreter {
    fun interpret(map: GestureMap): SemanticGestureActions {
        val actions = ArrayList<SemanticGestureAction>(map.bindings.size)
        for (binding in map.bindings) {
            actions += SemanticGestureAction(gesture = binding.gesture, action = binding.action)
        }
        actions.sortWith(compareBy({ it.gesture }, { it.action }))
        return SemanticGestureActions(actions)
    }
}
