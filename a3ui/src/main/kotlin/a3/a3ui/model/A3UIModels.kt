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
    val action: String
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

data class PrefetchSpec(
    val candidateRef: String,
    val baseStateVersion: Long,
    val confidence: Double,
    val ttlMs: Long,
    val status: PrefetchStatus
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
    val prefetch: PrefetchSpec? = null,
    val producedAt: Instant
)
