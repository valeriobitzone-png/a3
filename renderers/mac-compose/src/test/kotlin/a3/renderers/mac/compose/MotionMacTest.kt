package a3.renderers.mac.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionMacTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val assets = File("../../review-assets")
    private val reports = File("build/reports")
    private val motion = GraphicsTokens.motion
    private val compact = motion.compact
    private val comfortable = motion.comfortable

    private fun save(name: String, image: java.awt.image.BufferedImage) {
        reports.mkdirs()
        assets.mkdirs()
        MotionRaster.write(image, File(reports, name))
        MotionRaster.write(image, File(assets, name))
        assertTrue(File(assets, name).length() > 0)
    }

    @Test
    fun MO_001_spring_physics_from_tokens() {
        assertEquals("0.1", motion.version)
        assertEquals(1f, compact.mass)
        assertEquals(400f, compact.stiffness)
        assertEquals(28f, compact.damping)
        val samples = MotionPhysics.integrateSettled(0f, 1f, compact.mass, compact.stiffness, compact.damping)
        assertTrue(MotionPhysics.notLinear(samples, 0f, 1f, compact.durationHintMs))
        assertTrue(MotionPhysics.overshoot(samples, 0f, 1f))
        val damped = MotionPhysics.integrateSettled(0f, 1f, compact.mass, compact.stiffness, compact.damping * 3f)
        assertFalse(MotionPhysics.overshoot(damped, 0f, 1f))
        val a = MotionPhysics.accel(0.2f, 0.1f, 1f, compact.mass, compact.stiffness, compact.damping)
        val expected = (-compact.stiffness * (0.2f - 1f) - compact.damping * 0.1f) / compact.mass
        assertTrue(abs(a - expected) < 1e-5f)
        val src = File("src/main/kotlin/a3/renderers/mac/compose/MacMotionApplier.kt").readText()
        assertTrue(src.contains("token.mass"))
        assertTrue(src.contains("inheritVelocity"))
        assertFalse(src.contains("LinearEasing"))
    }

    @Test
    fun MO_002_shared_element_hash_and_continuity() {
        val hash0 = MotionPhysics.nodeIdentity("card-train", "Milano")
        val from = MotionPhysics.Rect(24f, 24f, 32f, 20f)
        val to = MotionPhysics.Rect(0f, 0f, 160f, 80f)
        val mid = MotionPhysics.sharedRect(from, to, 0.45f, motion.bezierEmphasized)
        assertEquals(hash0, MotionPhysics.nodeIdentity("card-train", "Milano"))
        assertTrue(mid.l <= from.l + 0.5f && mid.r >= from.r - 0.5f)
        val dir = File(reports, "frames-shared-mac")
        dir.deleteRecursively()
        dir.mkdirs()
        val ts = listOf(0f, 0.15f, 0.3f, 0.45f, 0.6f, 0.75f, 0.9f, 1f)
        ts.forEachIndexed { i, t ->
            MotionRaster.write(
                MotionRaster.sharedFrame(MotionPhysics.sharedRect(from, to, t, motion.bezierEmphasized), "Milano"),
                File(dir, "f%03d.png".format(i))
            )
        }
        MotionRaster.encodeFfmpeg(dir, ts.size, File(assets, "shared-element.gif"), fps = 8, gif = true)
        val morph = File("src/main/kotlin/a3/renderers/mac/compose/MacMorphApplier.kt").readText()
        assertTrue(morph.contains("plan.shared"))
        assertTrue(morph.contains("LaunchedEffect(plan.to, plan.shared)"))
        assertTrue(morph.contains("scaleX"))
        assertFalse(morph.contains("alpha = morph"))
    }

    @Test
    fun MO_003_fluid_resize_parallel_reflow() {
        val bezier = motion.bezierStandard
        val midW = MotionPhysics.fluidWidth(200f, 400f, 0.5f, bezier)
        assertTrue(midW > 200f && midW < 400f)
        val start = MotionPhysics.fluidBoxes(200f, 400f, 0f, 4, bezier)
        val mid = MotionPhysics.fluidBoxes(200f, 400f, 0.5f, 4, bezier)
        val end = MotionPhysics.fluidBoxes(200f, 400f, 1f, 4, bezier)
        assertEquals(0f, start[1].x)
        assertTrue(mid[1].x > 4f)
        assertTrue(mid[1].x < end[1].x)
        val renderer = File("src/main/kotlin/a3/renderers/mac/compose/MacRenderer.kt").readText()
        assertTrue(renderer.contains("FluidResizeContainer"))
    }

    @Test
    fun MO_004_inertia_inherits_velocity() {
        val mid = MotionPhysics.State(0.4f, 1.2f)
        val gestureV = 6f
        val inherited = MotionPhysics.inheritVelocity(mid, gestureV)
        val later = MotionPhysics.afterMs(inherited, 1f, comfortable.mass, comfortable.stiffness, comfortable.damping, 50f)
        val reset = MotionPhysics.afterMs(
            MotionPhysics.State(mid.x, 0f), 1f, comfortable.mass, comfortable.stiffness, comfortable.damping, 50f
        )
        val expectedCoast = mid.x + gestureV * 0.05f
        assertTrue(abs(later.x - expectedCoast) < abs(reset.x - expectedCoast))
        assertTrue(later.x != reset.x)
    }

    @Test
    fun MO_005_ripple_from_contact_not_center() {
        val frame = MotionRaster.rippleFrame(24, 96, 18f)
        save("ripple.png", frame)
        val (px, py) = MotionRaster.peakLumaOffset(frame)
        val toOrigin = MotionRaster.dist(px, py, 24, 96)
        val toCenter = MotionRaster.dist(px, py, 60, 60)
        assertTrue(toOrigin < toCenter, "peak ($px,$py)")
        assertTrue(toOrigin < 14f)
        val catalog = File("src/main/kotlin/a3/renderers/mac/compose/MacCatalog.kt").readText()
        assertTrue(catalog.contains("contactRipple"))
        assertTrue(File("src/main/kotlin/a3/renderers/mac/compose/Motion.kt").readText().contains("indication = null"))
    }

    @Test
    fun MO_006_rubber_banding_stretch_then_spring() {
        val rest = MotionRaster.rubberFrame(0f)
        val stretch = MotionRaster.rubberFrame(80f)
        save("rubber-band-stretch.png", stretch)
        assertTrue(MotionRaster.contentBottom(stretch) > MotionRaster.contentBottom(rest))
        assertTrue(MotionPhysics.rubberStretch(80f) < MotionPhysics.rubberLinear(80f))
        val ret = MotionPhysics.integrateSettled(
            MotionPhysics.rubberStretch(80f), 0f, comfortable.mass, comfortable.stiffness, comfortable.damping
        )
        assertTrue(MotionPhysics.notLinear(ret, MotionPhysics.rubberStretch(80f), 0f, 240))
        assertTrue(File("src/main/kotlin/a3/renderers/mac/compose/MacCatalog.kt").readText().contains("rubberBand"))
        assertTrue(File("src/main/kotlin/a3/renderers/mac/compose/Motion.kt").readText().contains("step(s, 0f"))
    }

    @Test
    fun MO_007_reduced_motion_zeroes_l3() {
        assertTrue(motion.reducedZeroes)
        val spring = MotionPhysics.integrateSettled(0f, 1f, compact.mass, compact.stiffness, compact.damping, reduced = true)
        assertEquals(1f, spring.single().x)
        val rest = MotionRaster.contentBottom(MotionRaster.rubberFrame(80f, reduced = true))
        val zero = MotionRaster.contentBottom(MotionRaster.rubberFrame(0f, reduced = true))
        assertEquals(zero, rest)
        assertTrue(File("src/main/kotlin/a3/renderers/mac/compose/MacMorphApplier.kt").readText().contains("if (reduced)"))
        assertTrue(File("src/main/kotlin/a3/renderers/mac/compose/MacMotionApplier.kt").readText().contains("if (reduced)"))
        val dir = File(reports, "frames-spring-mac")
        dir.deleteRecursively()
        dir.mkdirs()
        val frames = MotionPhysics.integrate(0f, 1f, compact.mass, compact.stiffness, compact.damping, 400)
        val take = frames.filterIndexed { i, _ -> i % 8 == 0 }.take(24)
        take.forEachIndexed { i, s ->
            MotionRaster.write(MotionRaster.springFrame(s.x, 0f, 1f), File(dir, "f%03d.png".format(i)))
        }
        MotionRaster.encodeFfmpeg(dir, take.size, File(assets, "spring-overshoot.mp4"), fps = 12, gif = false)
        val cmp = File(reports, "frames-reduced-mac")
        cmp.deleteRecursively()
        cmp.mkdirs()
        val picked = frames.filterIndexed { i, _ -> i % 10 == 0 }.take(12)
        picked.forEachIndexed { i, s ->
            val left = MotionRaster.springFrame(s.x, 0f, 1f)
            val right = MotionRaster.springFrame(1f, 0f, 1f)
            val g = left.createGraphics()
            g.drawImage(right, 80, 0, 80, 80, 80, 0, 160, 80, null)
            g.dispose()
            MotionRaster.write(left, File(cmp, "f%03d.png".format(i)))
        }
        MotionRaster.encodeFfmpeg(cmp, picked.size, File(assets, "reduced-motion-comparison.gif"), fps = 8, gif = true)
    }

    @Test
    fun MO_008_epistemic_axis_still_pulses() {
        assertEquals(2800, MacTheme.heldPulseMs)
        assertEquals(800, MacTheme.unknownShimmerMs)
        val motionSrc = File("src/main/kotlin/a3/renderers/mac/compose/MacMotion.kt").readText()
        assertTrue(motionSrc.contains("MacTheme.heldPulseMs"))
        assertTrue(motionSrc.contains("tween(MacTheme.heldPulseMs, easing = LinearEasing)"))
        val (_, calendar) = MacFixtures.composeCalendar()
        System.setProperty("a3.reduce.motion", "false")
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) { MacRenderer(calendar) }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("text_cal.hotel-held").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.hotel-motion-pulse", useUnmergedTree = true).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = true
        assertTrue(File("src/main/resources/a3ui-graphics/motion.json").exists())
    }

    @Test
    fun MO_009_freeze_only_compose_renderers() {
        val root = File("../..")
        val proc = ProcessBuilder(
            "git", "diff", "--stat", "--",
            "core/", "core/json", "core/admission", "core/action",
            "prediction/", "projection/", "a3ui/", "renderers/android-core/",
            "adapters/", "intent-model/", "broker/", "launcher/"
        ).directory(root).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor())
        assertTrue(out.isBlank(), out)
        val status = ProcessBuilder("git", "status", "--porcelain")
            .directory(root).redirectErrorStream(true).start()
        val porcelain = status.inputStream.bufferedReader().readText()
        assertEquals(0, status.waitFor())
        val allowed = listOf(
            "renderers/android-compose/",
            "renderers/mac-compose/",
            "REVIEW_RENDERER_MOTION.md",
            "REVIEW_RENDERER_DYNAMIC.md",
            "REVIEW_RENDERER_SHADERS.md",
            "REVIEW_RENDERER_SENSORY.md",
            "REVIEW_RENDERER_BACKDROP.md",
            "review-assets/"
        )
        val ignore = listOf(".kotlin/", ".DS_Store")
        for (line in porcelain.lineSequence().filter { it.isNotBlank() }) {
            val path = line.drop(3).trim().let { if (it.contains(" -> ")) it.substringAfter(" -> ") else it }
            if (ignore.any { path.startsWith(it) || path.contains("/$it") }) continue
            assertTrue(allowed.any { path.startsWith(it) }, "unexpected path $line")
        }
    }
}
