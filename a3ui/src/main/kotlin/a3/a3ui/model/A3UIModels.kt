// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui.model

import a3.projection.model.CausalLineage
import java.time.Instant

enum class PrefetchStatus {
    PREPARED,
    INVALIDATED,
    EXPIRED;

    fun wire(): String = name.lowercase()
}

data class MotionSpec(
    val stiffness: Double,
    val damping: Double,
    val curve: String,
    val durationHint: Long
)

data class MorphSpec(
    val to: String,
    val mode: String,
    val shared: List<String>,
    val motion: MotionSpec
)

data class GestureBinding(
    val gesture: String,
    val action: String,
    val targetNodeId: String
) {
    fun emit(): IntentCandidate = IntentCandidate(
        gesture = gesture,
        action = action,
        targetNodeId = targetNodeId
    )
}

/** Gesture path product. Not a Command. Not a belief write. */
data class IntentCandidate(
    val gesture: String,
    val action: String,
    val targetNodeId: String
)

data class GestureMap(
    val bindings: List<GestureBinding>
)

data class HapticEvent(
    val event: String,
    val pattern: String,
    val intensityHint: String
)

data class HapticMap(
    val events: List<HapticEvent>
)

enum class EpistemicSupport {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN;

    fun wire(): String = name.lowercase()
}

enum class EpistemicFreshness {
    FRESH,
    AGING,
    STALE;

    fun wire(): String = name.lowercase()
}

enum class EpistemicStatus {
    BELIEVED,
    HELD,
    CONTRADICTED;

    fun wire(): String = name.lowercase()
}

enum class EpistemicAction {
    NA,
    PENDING,
    UNKNOWN,
    DONE,
    COMPENSATED;

    fun wire(): String = name.lowercase()
}

/**
 * Additive catalog field (not a role). Omit-when-default:
 * HIGH / FRESH / BELIEVED / NA is the implicit catalog default.
 */
data class EpistemicAxis(
    val support: EpistemicSupport = EpistemicSupport.HIGH,
    val freshness: EpistemicFreshness = EpistemicFreshness.FRESH,
    val status: EpistemicStatus = EpistemicStatus.BELIEVED,
    val action: EpistemicAction = EpistemicAction.NA
) {
    fun isDefault(): Boolean =
        support == EpistemicSupport.HIGH &&
            freshness == EpistemicFreshness.FRESH &&
            status == EpistemicStatus.BELIEVED &&
            action == EpistemicAction.NA

    fun stateDescription(): String {
        if (isDefault()) return ""
        val parts = mutableListOf<String>()
        if (support != EpistemicSupport.HIGH) parts += "support ${support.wire()}"
        if (freshness != EpistemicFreshness.FRESH) parts += "freshness ${freshness.wire()}"
        if (status != EpistemicStatus.BELIEVED) parts += "status ${status.wire()}"
        if (action != EpistemicAction.NA) parts += "action ${action.wire()}"
        return parts.joinToString(" ")
    }

    fun accessibleName(text: String = ""): String {
        val axis = stateDescription()
        if (axis.isEmpty()) return text
        if (text.isEmpty()) return axis
        return "$text $axis"
    }
}

data class Node(
    val id: String,
    val role: String,
    val children: List<Node> = emptyList(),
    val axis: EpistemicAxis? = null
) {
    fun resolvedAxis(): EpistemicAxis = axis ?: EpistemicAxis()
}

data class Binding(
    val atomKey: String,
    val nodeId: String,
    val role: String
)

data class PrefetchSpec(
    val candidateRef: String,
    val baseStateVersion: Long,
    val confidence: Double,
    val ttlMs: Long,
    val status: PrefetchStatus,
    val atomKeys: List<String> = emptyList()
)

data class A3UISurface(
    val id: String,
    val projectionRef: String,
    val presentationRef: String,
    val lineage: CausalLineage,
    val densityHint: String,
    val colorTokens: List<String>,
    val motion: MotionSpec,
    val morph: MorphSpec,
    val gestures: GestureMap,
    val haptics: HapticMap,
    val nodes: List<Node> = emptyList(),
    val bindings: List<Binding> = emptyList(),
    val prefetch: PrefetchSpec? = null,
    val producedAt: Instant
)
