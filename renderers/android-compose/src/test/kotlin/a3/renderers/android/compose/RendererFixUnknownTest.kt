package a3.renderers.android.compose

import a3.a3ui.model.A3UISurface
import a3.a3ui.model.Binding
import a3.a3ui.model.EpistemicAxis
import a3.a3ui.model.EpistemicSupport
import a3.a3ui.model.GestureMap
import a3.a3ui.model.HapticMap
import a3.a3ui.model.MorphSpec
import a3.a3ui.model.MotionSpec
import a3.a3ui.model.Node
import a3.core.time.FixedClock
import a3.projection.model.CausalLineage
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RenderedOutput
import a3.renderers.android.core.model.RendererContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import java.io.File
import java.time.Instant
import java.util.TreeMap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RendererFixUnknownTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val t = Instant.parse("2026-08-27T08:00:00Z")
    private val interpreter = A3UIInterpreter()
    private val motion = MotionSpec(280.0, 24.0, "standard", 240)

    private val lowAxis = EpistemicAxis(support = EpistemicSupport.LOW)
    private val unknownAxis = EpistemicAxis(support = EpistemicSupport.UNKNOWN)

    private fun ctx() = RendererContext(
        formFactor = "phone",
        density = "comfortable",
        tokens = TreeMap<String, ColorValue>().apply { put("accent", ColorValue(0, 90, 200)) },
        clock = FixedClock(t),
        reducedMotion = true
    )

    private fun lineage() = CausalLineage("ctx", 1, "e1")

    private fun presentation() = PresentationState(
        id = "ps1",
        sourceStateVersion = 1,
        producedAt = t,
        atoms = listOf(PresentationAtom("price", "train.price", "12.40", 60)),
        lineage = lineage()
    )

    private fun surface(node: Node) = A3UISurface(
        id = "s1",
        projectionRef = "p1",
        presentationRef = "ps1",
        lineage = lineage(),
        densityHint = "comfortable",
        colorTokens = listOf("accent"),
        motion = motion,
        morph = MorphSpec("ps1", "shared-element", emptyList(), motion),
        gestures = GestureMap(emptyList()),
        haptics = HapticMap(emptyList()),
        nodes = listOf(node),
        bindings = listOf(Binding("train.price", "n1", "content")),
        producedAt = t
    )

    private fun output(axis: EpistemicAxis): RenderedOutput {
        val node = Node("n1", "text", axis = axis)
        return interpreter.interpret(surface(node), presentation(), ctx())
    }

    @Test
    fun FX_001_low_is_half_opacity_bars_without_dash() {
        assertEquals(0.50f, Exposure.textAlpha(lowAxis))
        val out = output(lowAxis)
        val node = out.nodes.single()
        assertEquals(EpistemicSupport.LOW, node.axis!!.support)
        assertFalse(Exposure.spokenPhrases(lowAxis).contains("uncertain"))
        composeRule.setContent { ComposeRenderer(out) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("n1-low").assertIsDisplayed()
        composeRule.onNodeWithTag("n1-uncertain").assertDoesNotExist()
        val bitmap = ExposureRaster.paint(out)
        val reports = File("build/reports")
        reports.mkdirs()
        ExposureRaster.write(bitmap, File(reports, "fx-001-low.png"))
        val height = bitmap.height
        assertTrue(
            ExposureRaster.inkCount(bitmap, ExposureRaster.LOW_BAR_X1) >= height - 4,
            "LOW missing first bar"
        )
        assertTrue(
            ExposureRaster.inkCount(bitmap, ExposureRaster.LOW_BAR_X2) >= height - 4,
            "LOW missing second bar"
        )
        assertTrue(
            ExposureRaster.rowInk(bitmap, ExposureRaster.UNKNOWN_INSET) < bitmap.width / 8,
            "LOW must not paint a dashed top frame"
        )
        val chrome = File("src/main/kotlin/a3/renderers/android/compose/Exposure.kt").readText()
        val lowArm = chrome.substringAfter("EpistemicSupport.LOW -> {")
            .substringBefore("EpistemicSupport.UNKNOWN")
        assertFalse(lowArm.contains("dashPathEffect"), "LOW arm must not dash")
        assertTrue(lowArm.contains("drawLine"))
    }

    @Test
    fun FX_002_unknown_is_dashed_uncertain_full_opacity_without_bars() {
        assertEquals(1f, Exposure.textAlpha(unknownAxis))
        val out = output(unknownAxis)
        val node = out.nodes.single()
        assertEquals(EpistemicSupport.UNKNOWN, node.axis!!.support)
        assertTrue(node.stateDescription.contains("support unknown"))
        assertTrue(Exposure.spokenPhrases(unknownAxis).contains("uncertain"))
        composeRule.setContent { ComposeRenderer(out) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("n1-uncertain").assertIsDisplayed()
        composeRule.onNodeWithTag("n1-low").assertDoesNotExist()
        composeRule.onNodeWithTag("n1").assert(
            SemanticsMatcher("protocol phrase uncertain") { semantics ->
                val spoken = semantics.config[SemanticsProperties.ContentDescription]
                    .joinToString(" ")
                spoken.contains("uncertain") && spoken.contains(node.stateDescription)
            }
        )
        val bitmap = ExposureRaster.paint(out)
        val reports = File("build/reports")
        reports.mkdirs()
        ExposureRaster.write(bitmap, File(reports, "fx-002-unknown.png"))
        val height = bitmap.height
        assertTrue(
            ExposureRaster.inkCount(bitmap, ExposureRaster.LOW_BAR_X1) < height / 4,
            "UNKNOWN must not paint LOW bar 1"
        )
        assertTrue(
            ExposureRaster.inkCount(bitmap, ExposureRaster.LOW_BAR_X2) < height / 4,
            "UNKNOWN must not paint LOW bar 2"
        )
        val frameInk = ExposureRaster.inkCount(bitmap, ExposureRaster.UNKNOWN_INSET)
        assertTrue(frameInk in 1 until height, "UNKNOWN dashed inset must have gaps, ink=$frameInk")
        assertTrue(
            ExposureRaster.rowInk(bitmap, ExposureRaster.UNKNOWN_INSET) > bitmap.width / 8,
            "UNKNOWN must paint a dashed top frame"
        )
        val chrome = File("src/main/kotlin/a3/renderers/android/compose/Exposure.kt").readText()
        val unknownArm = chrome.substringAfter("EpistemicSupport.UNKNOWN -> {")
            .substringBefore("EpistemicSupport.HIGH")
        assertTrue(unknownArm.contains("dashPathEffect"))
        assertFalse(unknownArm.contains("drawLine"))
        val motion = File("src/main/kotlin/a3/renderers/android/compose/ExposureMotion.kt").readText()
        val shimmer = motion.substringAfter("ExposureVerb.SHIMMER -> {")
            .substringBefore("ExposureVerb.CRACK_REVERSE")
        assertTrue(shimmer.contains("translationX"))
        assertFalse(shimmer.contains("this.alpha"))
    }

    @Test
    fun FX_003_low_and_unknown_bitmaps_differ_for_the_same_node() {
        val low = output(lowAxis)
        val unknown = output(unknownAxis)
        assertEquals(low.nodes.single().text, unknown.nodes.single().text)
        val lowBmp = ExposureRaster.paint(low)
        val unknownBmp = ExposureRaster.paint(unknown)
        assertNotEquals(
            ExposureRaster.fingerprint(lowBmp),
            ExposureRaster.fingerprint(unknownBmp)
        )
        val reports = File("build/reports")
        reports.mkdirs()
        ExposureRaster.write(lowBmp, File(reports, "fx-003-low.png"))
        ExposureRaster.write(unknownBmp, File(reports, "fx-003-unknown.png"))
    }
}
