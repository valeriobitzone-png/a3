// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.projection.model

import java.time.Instant

enum class Density {
    COMPACT,
    COMFORTABLE,
    SPACIOUS;

    fun wire(): String = name.lowercase()
}

enum class ProjectionStatus {
    PROPOSED,
    READY,
    SUPERSEDED;

    fun wire(): String = name.lowercase()
}

enum class CandidateStatus {
    PREPARED,
    INVALIDATED,
    EXPIRED;

    fun wire(): String = name.lowercase()
}

data class CausalLineage(
    val stateIdentity: String,
    val sourceStateVersion: Long,
    val causalEventId: String,
    val futureStateId: String? = null,
    val forecastId: String? = null
)

data class FormFactorHints(
    val formFactor: String,
    val density: Density
) {
    init {
        require(formFactor in FORM_FACTORS) { "invalid form_factor: $formFactor" }
    }

    companion object {
        val FORM_FACTORS = listOf("phone", "tablet", "car", "glasses", "desktop")
    }
}

data class PresentationAtom(
    val meaning: String,
    val k: String,
    val v: Any?,
    val priority: Int
)

data class PresentationState(
    val id: String,
    val sourceStateVersion: Long,
    val producedAt: Instant,
    val atoms: List<PresentationAtom>,
    val lineage: CausalLineage
)

data class Projection(
    val id: String,
    val presentationId: String,
    val contextRef: String,
    val formFactorHints: FormFactorHints,
    val interactionRequirements: List<String>,
    val lineage: CausalLineage,
    val status: ProjectionStatus
)

data class ProjectionCandidate(
    val id: String,
    val futureStateId: String,
    val forecastId: String,
    val contextRef: String,
    val presentation: PresentationState,
    val baseStateVersion: Long,
    val status: CandidateStatus,
    val expiresAt: Instant? = null,
    val priority: Int,
    val rank: Int,
    val lineage: CausalLineage
)

data class RenderedOutput(
    val kind: String,
    val body: String
)
