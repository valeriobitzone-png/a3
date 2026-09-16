// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.launcher

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.engine.PresentationHash
import a3.a3ui.engine.SurfaceComposer
import a3.core.time.SequentialIdGenerator
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.CatalogProfile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.ArrayList
import org.junit.Test

class A3UIAxisLauncherTest {
    @Test
    fun AX_axis_fixture_exposes_medium_support_on_price() {
        val compiler = DeterministicA3UICompiler(DemoFixtures.now, SequentialIdGenerator())
        val presentation = DemoFixtures.presentation()
        val asOf = DemoFixtures.now
        val facts = DemoFixtures.priceFacts(confidence = 0.6)
        val tree = SurfaceComposer.compose(presentation, asOf, facts)
        val surface = compiler.compile(DemoFixtures.projection(), presentation)
            .copy(nodes = tree.nodes, bindings = tree.bindings)
        val again = SurfaceComposer.compose(presentation, asOf, facts)
        assertEquals(PresentationHash.of(tree), PresentationHash.of(again))
        val out = A3UIInterpreter().interpret(
            surface,
            presentation,
            DemoFixtures.rendererContext()
        )
        val price = out.nodes.flatMap { walk(it) }.single { it.id.contains("train.price") }
        assertTrue(price.stateDescription.isNotEmpty())
        assertTrue(price.stateDescription.contains("support"))
        val v1 = A3UIInterpreter().interpret(
            surface,
            presentation,
            DemoFixtures.rendererContext(),
            CatalogProfile.V1
        )
        assertEquals("12.40", v1.nodes.flatMap { walk(it) }.single { it.id.contains("train.price") }.text)
        assertEquals("axis-ignored", v1.degradation)
        val reduced = A3UIInterpreter().interpret(
            surface,
            presentation,
            DemoFixtures.rendererContext().let {
                it.copy(reducedMotion = true)
            }
        )
        assertEquals(price.stateDescription, reduced.nodes.flatMap { walk(it) }.single { it.id.contains("train.price") }.stateDescription)
        assertFalse(price.stateDescription.isEmpty())
    }

    private fun walk(
        node: a3.renderers.android.core.model.RenderedNode
    ): List<a3.renderers.android.core.model.RenderedNode> {
        val out = ArrayList<a3.renderers.android.core.model.RenderedNode>()
        out += node
        for (child in node.children) out += walk(child)
        return out
    }
}
