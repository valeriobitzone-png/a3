package a3.renderers.android.core.model

import a3.a3ui.model.PrefetchStatus
import a3.core.time.InstantSource
import a3.projection.model.CausalLineage
import java.time.Instant
import java.util.TreeMap

data class ColorValue(
    val red: Int,
    val green: Int,
    val blue: Int
) {
    init {
        require(red in 0..255 && green in 0..255 && blue in 0..255) {
            "channel out of range"
        }
    }
}

data class RendererContext(
    val formFactor: String,
    val density: String,
    val tokens: TreeMap<String, ColorValue>,
    val clock: InstantSource,
    val currentStateVersion: Long = 0,
    val stage: String = STAGE_PRONTO
) {
    init {
        require(stage in STAGES) { "unknown renderer stage" }
    }

    companion object {
        const val STAGE_ASCOLTO = "ascolto"
        const val STAGE_LAVORO = "lavoro"
        const val STAGE_PRONTO = "pronto"
        const val STAGE_APPROVA = "approva"
        val STAGES = listOf(STAGE_ASCOLTO, STAGE_LAVORO, STAGE_PRONTO, STAGE_APPROVA)
    }
}

data class SpringParams(
    val stiffness: Double,
    val damping: Double,
    val curve: String,
    val durationHint: Long
)

data class SharedElementPlan(
    val to: String,
    val mode: String,
    val shared: List<String>,
    val spring: SpringParams
)

data class SemanticGestureAction(
    val gesture: String,
    val action: String,
    val targetNodeId: String
)

data class SemanticGestureActions(
    val actions: List<SemanticGestureAction>
)

data class SemanticHapticEvent(
    val event: String,
    val pattern: String,
    val intensityHint: String
)

data class SemanticHapticEvents(
    val events: List<SemanticHapticEvent>
)

data class ResolvedToken(
    val token: String,
    val color: ColorValue
)

data class RenderedPrefetch(
    val candidateRef: String,
    val baseStateVersion: Long,
    val confidence: Double,
    val ttlMs: Long,
    val status: PrefetchStatus,
    val atomKeys: List<String> = emptyList()
)

data class RenderedNode(
    val id: String,
    val role: String,
    val children: List<RenderedNode> = emptyList(),
    val text: String = "",
    val hint: String = ""
)

data class RenderedOutput(
    val surfaceId: String,
    val projectionRef: String,
    val presentationRef: String,
    val lineage: CausalLineage,
    val densityScale: Double,
    val densityHint: String,
    val formFactor: String,
    val resolvedTokens: List<ResolvedToken>,
    val spring: SpringParams,
    val sharedElements: SharedElementPlan,
    val gestures: SemanticGestureActions,
    val haptics: SemanticHapticEvents,
    val nodes: List<RenderedNode> = emptyList(),
    val prefetch: RenderedPrefetch?,
    val producedAt: Instant
)
