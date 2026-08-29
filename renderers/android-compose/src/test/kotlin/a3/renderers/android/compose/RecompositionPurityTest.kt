package a3.renderers.android.compose

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.serialize.CanonicalJson as SurfaceCanonical
import a3.core.time.FixedClock
import a3.core.time.SequentialIdGenerator
import a3.core.world.api.Fact
import a3.core.world.api.ReadBelief
import a3.projection.engine.ProjectionEngine
import a3.projection.engine.ProjectionEventLog
import a3.projection.model.Density
import a3.projection.model.FormFactorHints
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RendererContext
import a3.renderers.android.core.serialize.CanonicalJson
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RecompositionPurityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val t = Instant.parse("2026-08-27T08:00:00Z")

    @Test
    fun P55_recomposition_does_not_write_a3_state() {
        val surface = DeterministicA3UICompiler(FixedClock(t), SequentialIdGenerator()).compile(
            ProjectionEngine(FixedClock(t), SequentialIdGenerator(), ProjectionEventLog())
                .readBelief(
                    "ctx",
                    ReadBelief(1, listOf(Fact("ticket.owned", true, 0.97, "train", t))),
                    FormFactorHints("phone", Density.COMFORTABLE)
                ).projection
        )
        val ctx = RendererContext(
            formFactor = "phone",
            density = "comfortable",
            tokens = TreeMap<String, ColorValue>().apply {
                put("accent", ColorValue(0, 90, 200))
                put("success", ColorValue(0, 140, 70))
            },
            clock = FixedClock(t)
        )
        val output = A3UIInterpreter().interpret(surface, ctx)
        val beforeSurface = SurfaceCanonical.bytes(surface)
        val beforeOutput = CanonicalJson.bytes(output)
        val recompositions = AtomicInteger(0)
        lateinit var ticks: MutableIntState
        composeRule.setContent {
            ticks = remember { mutableIntStateOf(0) }
            ticks.intValue
            SideEffect { recompositions.incrementAndGet() }
            ComposeRenderer(output)
        }
        composeRule.waitForIdle()
        val afterFirst = recompositions.get()
        assertTrue(afterFirst >= 1, "first composition missing, was $afterFirst")
        repeat(8) {
            composeRule.runOnIdle { ticks.intValue++ }
            composeRule.waitForIdle()
        }
        val total = recompositions.get()
        assertTrue(total >= afterFirst + 8, "expected >= ${afterFirst + 8} recompositions, was $total")
        assertContentEquals(beforeSurface, SurfaceCanonical.bytes(surface))
        assertContentEquals(beforeOutput, CanonicalJson.bytes(output))
    }
}
