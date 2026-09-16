// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.engine.PrefetchLinker
import a3.a3ui.engine.TemporalBuilder
import a3.a3ui.model.A3UISurface
import a3.a3ui.model.PrefetchSpec
import a3.projection.model.PresentationState
import a3.projection.model.Projection
import kotlin.test.Test
import kotlin.test.assertTrue

class A3UIBarrierTest {
    @Test
    fun P40_no_a3ui_method_mutates_projection_presentation_or_believed_reality() {
        val forbiddenNames = setOf("apply", "commit", "write", "putFact", "mutate")
        val believed = "Belief" + "State"
        val committed = "World" + "State"
        val production = listOf(
            DeterministicA3UICompiler::class.java,
            PrefetchLinker::class.java,
            TemporalBuilder::class.java,
            A3UISurface::class.java,
            PrefetchSpec::class.java
        )
        for (type in production) {
            for (method in type.methods) {
                assertTrue(method.name !in forbiddenNames, "${type.simpleName}.${method.name}")
                for (param in method.parameterTypes) {
                    assertTrue(param.simpleName != believed, "${type.simpleName}.${method.name}")
                    assertTrue(param.simpleName != committed, "${type.simpleName}.${method.name}")
                }
            }
        }
        assertTrue(
            DeterministicA3UICompiler::class.java.declaredMethods
                .filter { it.name.startsWith("compile") }
                .all { method ->
                    method.parameterTypes.isNotEmpty() &&
                        method.parameterTypes.all { it.packageName.startsWith("a3.projection.model") }
                }
        )
        assertTrue(
            listOf(Projection::class.java, PresentationState::class.java).all { model ->
                production.none { type ->
                    type.methods.any { it.returnType == model && it.name in forbiddenNames }
                }
            }
        )
    }
}
