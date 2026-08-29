package a3.renderers.android.core.interp

import a3.a3ui.model.HapticMap
import a3.renderers.android.core.model.SemanticHapticEvent
import a3.renderers.android.core.model.SemanticHapticEvents
import java.util.ArrayList

class HapticInterpreter {
    fun interpret(map: HapticMap): SemanticHapticEvents {
        val events = ArrayList<SemanticHapticEvent>(map.events.size)
        for (item in map.events) {
            events += SemanticHapticEvent(
                event = item.event,
                pattern = item.pattern,
                intensityHint = item.intensityHint
            )
        }
        events.sortWith(compareBy({ it.event }, { it.pattern }))
        return SemanticHapticEvents(events)
    }
}
