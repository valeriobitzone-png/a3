package a3.launcher

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.engine.EpistemicFacts
import a3.a3ui.engine.SurfaceComposer
import a3.core.time.SequentialIdGenerator
import a3.projection.model.PresentationState
import a3.renderers.android.compose.ExposureRaster
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.RenderedNode
import a3.renderers.android.core.model.RenderedOutput
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.ArrayList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RendererExposureLauncherTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val interpreter = A3UIInterpreter()
    private val compiler = DeterministicA3UICompiler(DemoFixtures.now, SequentialIdGenerator())

    private fun interpret(
        presentation: PresentationState,
        facts: Map<String, EpistemicFacts>
    ): RenderedOutput {
        val tree = SurfaceComposer.compose(presentation, DemoFixtures.now, facts)
        val surface = compiler.compile(DemoFixtures.projection(), presentation)
            .copy(nodes = tree.nodes, bindings = tree.bindings)
        return interpreter.interpret(
            surface,
            presentation,
            DemoFixtures.rendererContext().copy(reducedMotion = true)
        )
    }

    private fun walk(node: RenderedNode): List<RenderedNode> {
        val out = ArrayList<RenderedNode>()
        out += node
        for (child in node.children) out += walk(child)
        return out
    }

    private fun writeShot(name: String, output: RenderedOutput) {
        val sample = File("build/reports/rx-008-$name.png")
        ExposureRaster.write(ExposureRaster.paint(output), sample)
        assertTrue(sample.exists() && sample.length() > 0, "$name bitmap missing")
    }

    @Test
    fun RX_008_tab_a9_and_uncertain_fixture_render_without_crash() {
        val tabA9 = interpret(DemoFixtures.presentation(), emptyMap())
        val uncertain = interpret(DemoFixtures.presentation(), DemoFixtures.uncertainFacts())
        writeShot("tab-a9", tabA9)
        writeShot("uncertain", uncertain)
        val tabHash = ExposureRaster.fingerprint(ExposureRaster.paint(tabA9))
        val uncertainHash = ExposureRaster.fingerprint(ExposureRaster.paint(uncertain))
        assertNotEquals(tabHash, uncertainHash)

        var shown by mutableStateOf(tabA9)
        composeRule.setContent {
            Box(
                Modifier
                    .size(400.dp, 800.dp)
                    .background(Color.White)
                    .testTag("shot")
            ) {
                A3Screen(shown, {}, false, false, {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
        composeRule.onNodeWithTag("text_train.price").assertIsDisplayed()
        composeRule.onNodeWithTag("item_train.slot.a").assertIsDisplayed()

        val price = uncertain.nodes.flatMap { walk(it) }.single { it.id.contains("train.price") }
        assertTrue(price.stateDescription.isNotEmpty())
        assertTrue(price.axis != null && !price.axis!!.isDefault())
        composeRule.runOnIdle { shown = uncertain }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_train.price").assertIsDisplayed()
        composeRule.onNodeWithTag("text_train.price-marks", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("text_train.price-low", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("text_train.price-stale", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("text_train.price-held", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("text_train.price-unknown", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("text_train.price-axis").assertIsDisplayed()
    }
}
