package a3.a3ui.engine

import a3.a3ui.model.GestureBinding
import a3.a3ui.model.GestureMap
import a3.a3ui.model.HapticEvent
import a3.a3ui.model.HapticMap
import a3.a3ui.model.MorphSpec
import a3.a3ui.model.MotionSpec
import a3.projection.model.Density
import a3.projection.model.PresentationAtom
import java.util.ArrayList
import java.util.TreeSet

/**
 * Builds motion/morph/gesture/haptic maps as declarative data.
 * No device animation or haptic APIs.
 */
class TemporalBuilder {
    fun motion(density: Density): MotionSpec = when (density) {
        Density.COMPACT -> MotionSpec(400.0, 28.0, "standard", 180)
        Density.COMFORTABLE -> MotionSpec(280.0, 24.0, "standard", 240)
        Density.SPACIOUS -> MotionSpec(180.0, 22.0, "standard", 320)
    }

    fun morph(to: String, shared: List<String>, motion: MotionSpec): MorphSpec {
        val ids = ArrayList(shared)
        ids.sort()
        return MorphSpec(to = to, mode = "shared-element", shared = ids, motion = motion)
    }

    fun gestures(requirements: List<String>, targetNodeId: String): GestureMap {
        val req = ArrayList(requirements)
        req.sort()
        val bindings = ArrayList<GestureBinding>()
        if (req.isNotEmpty()) bindings += GestureBinding("swipe-left", "dismiss", targetNodeId)
        if (req.contains("confirm")) bindings += GestureBinding("confirm", "confirm", targetNodeId)
        bindings.sortWith(compareBy({ it.gesture }, { it.action }, { it.targetNodeId }))
        return GestureMap(bindings)
    }

    fun haptics(requirements: List<String>): HapticMap {
        val req = ArrayList(requirements)
        req.sort()
        val events = ArrayList<HapticEvent>()
        if (req.contains("attend")) events += HapticEvent("attend", "tap", "light")
        if (req.contains("confirm")) events += HapticEvent("confirm", "double-tap", "medium")
        events.sortWith(compareBy({ it.event }, { it.pattern }))
        return HapticMap(events)
    }

    fun colorTokens(requirements: List<String>, prefetch: Boolean): List<String> {
        val tokens = TreeSet<String>()
        tokens.add("accent")
        if (requirements.contains("confirm")) tokens.add("success")
        if (prefetch) tokens.add("anticipation_highlight")
        return ArrayList(tokens)
    }

    fun requirementsFrom(atoms: List<PresentationAtom>): List<String> {
        val req = TreeSet<String>()
        if (atoms.isNotEmpty()) req.add("attend")
        for (atom in atoms) {
            if (atom.k.endsWith(".owned")) req.add("confirm")
        }
        return ArrayList(req)
    }
}
