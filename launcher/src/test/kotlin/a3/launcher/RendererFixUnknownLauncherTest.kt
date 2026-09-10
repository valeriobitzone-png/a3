package a3.launcher

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.engine.Epistemic
import a3.a3ui.engine.EpistemicFacts
import a3.a3ui.engine.SurfaceComposer
import a3.a3ui.model.EpistemicSupport
import a3.core.model.Claim
import a3.core.time.SequentialIdGenerator
import a3.projection.model.PresentationState
import a3.renderers.android.compose.Exposure
import a3.renderers.android.compose.ExposureRaster
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.RenderedNode
import a3.renderers.android.core.model.RenderedOutput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import java.io.File
import java.util.ArrayList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RendererFixUnknownLauncherTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val interpreter = A3UIInterpreter()
    private val compiler = DeterministicA3UICompiler(DemoFixtures.now, SequentialIdGenerator())

    private fun unknownFacts(): Map<String, EpistemicFacts> = mapOf(
        "train.price" to EpistemicFacts(
            claim = Claim(
                "train.price",
                "12.40",
                0.0,
                "train",
                DemoFixtures.now,
                DemoFixtures.now.plusSeconds(3600)
            )
        )
    )

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

    private fun price(output: RenderedOutput): RenderedNode =
        output.nodes.flatMap { walk(it) }.single { it.id.contains("train.price") }

    @Test
    fun FX_001_conf_0_4_is_low_bars_without_dash() {
        val derived = Epistemic.derive(DemoFixtures.priceFacts(0.4).getValue("train.price"), DemoFixtures.now)
        assertEquals(EpistemicSupport.LOW, derived.support)
        assertEquals(0.50f, Exposure.textAlpha(derived))
        val output = interpret(DemoFixtures.presentation(), DemoFixtures.priceFacts(0.4))
        val node = price(output)
        assertEquals(EpistemicSupport.LOW, node.axis!!.support)
        composeRule.setContent { A3Screen(output, {}, false, false, {}) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_train.price-low", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("text_train.price-uncertain", useUnmergedTree = true).assertDoesNotExist()
        val bitmap = ExposureRaster.paint(node)
        val height = bitmap.height
        assertTrue(ExposureRaster.inkCount(bitmap, ExposureRaster.LOW_BAR_X1) >= height - 4)
        assertTrue(ExposureRaster.inkCount(bitmap, ExposureRaster.LOW_BAR_X2) >= height - 4)
        assertTrue(ExposureRaster.rowInk(bitmap, ExposureRaster.UNKNOWN_INSET) < bitmap.width / 8)
        val reports = File("build/reports")
        reports.mkdirs()
        ExposureRaster.write(bitmap, File(reports, "fx-001-low.png"))
    }

    @Test
    fun FX_002_support_unknown_is_dashed_full_opacity_without_bars() {
        val facts = unknownFacts()
        val derived = Epistemic.derive(facts.getValue("train.price"), DemoFixtures.now)
        assertEquals(EpistemicSupport.UNKNOWN, derived.support)
        assertEquals(1f, Exposure.textAlpha(derived))
        val output = interpret(DemoFixtures.presentation(), facts)
        val node = price(output)
        assertEquals(EpistemicSupport.UNKNOWN, node.axis!!.support)
        assertTrue(node.stateDescription.contains("support unknown"))
        assertTrue(Exposure.spokenPhrases(node.axis!!).contains("uncertain"))
        composeRule.setContent { A3Screen(output, {}, false, false, {}) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_train.price-uncertain", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("text_train.price-low", useUnmergedTree = true).assertDoesNotExist()
        val bitmap = ExposureRaster.paint(node)
        val height = bitmap.height
        assertTrue(ExposureRaster.inkCount(bitmap, ExposureRaster.LOW_BAR_X1) < height / 4)
        assertTrue(ExposureRaster.inkCount(bitmap, ExposureRaster.LOW_BAR_X2) < height / 4)
        val frameInk = ExposureRaster.inkCount(bitmap, ExposureRaster.UNKNOWN_INSET)
        assertTrue(frameInk in 1 until height, "dashed inset ink=$frameInk")
        assertTrue(ExposureRaster.rowInk(bitmap, ExposureRaster.UNKNOWN_INSET) > bitmap.width / 8)
        val reports = File("build/reports")
        reports.mkdirs()
        ExposureRaster.write(bitmap, File(reports, "fx-002-unknown.png"))
    }

    @Test
    fun FX_003_same_price_node_low_vs_unknown_bitmaps_differ() {
        val low = price(interpret(DemoFixtures.presentation(), DemoFixtures.priceFacts(0.4)))
        val unknown = price(interpret(DemoFixtures.presentation(), unknownFacts()))
        assertEquals(low.text, unknown.text)
        assertEquals(low.id, unknown.id)
        val lowBmp = ExposureRaster.paint(low)
        val unknownBmp = ExposureRaster.paint(unknown)
        assertNotEquals(ExposureRaster.fingerprint(lowBmp), ExposureRaster.fingerprint(unknownBmp))
        val reports = File("build/reports")
        reports.mkdirs()
        ExposureRaster.write(lowBmp, File(reports, "fx-003-low.png"))
        ExposureRaster.write(unknownBmp, File(reports, "fx-003-unknown.png"))
    }

    @Test
    fun FX_004_frozen_trees_have_empty_diff() {
        val root = File("..")
        val proc = ProcessBuilder(
            "git",
            "diff",
            "--stat",
            "--",
            "core/",
            "core/json",
            "core/admission",
            "core/action",
            "prediction/",
            "projection/",
            "a3ui/",
            "renderers/android-core/",
            "adapters/",
            "intent-model/",
            "broker/"
        ).directory(root).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor())
        assertTrue(out.isBlank(), out)
        val rx = File("src/test/kotlin/a3/launcher/RendererExposureLauncherTest.kt")
        assertTrue(rx.exists() && rx.readText().contains("RX_008"))
        val composeRx = File(
            "../renderers/android-compose/src/test/kotlin/a3/renderers/android/compose/RendererExposureTest.kt"
        )
        assertTrue(composeRx.exists())
        assertTrue(composeRx.readText().contains("RX_001"))
        assertTrue(composeRx.readText().contains("RX_008") || rx.readText().contains("RX_008"))
    }
}
