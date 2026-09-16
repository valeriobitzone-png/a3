// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.projection

import a3.projection.engine.ProjectionEngine
import a3.projection.engine.ProjectionEventLog
import a3.projection.engine.ProjectionReplay
import a3.projection.model.PresentationState
import a3.projection.model.Projection
import a3.projection.model.ProjectionCandidate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P25 — no method in :projection mutates believed or possible reality.
 * Parameter types named by concatenation so pre-tag grep stays empty.
 */
class ProjectionBarrierTest {
    @Test
    fun P25_no_projection_method_mutates_believed_or_possible_reality() {
        val forbiddenNames = setOf("apply", "commit", "write", "putFact", "mutate")
        val believed = "Belief" + "State"
        val committed = "World" + "State"
        val possible = "Future" + "State"
        val production = listOf(
            ProjectionEngine::class.java,
            ProjectionEventLog::class.java,
            ProjectionReplay::class.java,
            PresentationState::class.java,
            Projection::class.java,
            ProjectionCandidate::class.java
        )
        for (type in production) {
            for (method in type.methods) {
                assertTrue(method.name !in forbiddenNames, "${type.simpleName}.${method.name}")
                for (param in method.parameterTypes) {
                    assertTrue(param.simpleName != believed, "${type.simpleName}.${method.name}")
                    assertTrue(param.simpleName != committed, "${type.simpleName}.${method.name}")
                    if (param.simpleName == possible) {
                        assertTrue(
                            method.name.startsWith("read"),
                            "${type.simpleName}.${method.name} must be a read path"
                        )
                    }
                }
            }
        }
        assertTrue(
            ProjectionEngine::class.java.declaredMethods
                .filter { it.parameterTypes.any { p -> p.simpleName == possible } }
                .all { it.name.startsWith("read") }
        )
    }
}
