package a3.renderers.android.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.io.File
import java.security.MessageDigest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MotionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val assets = File("../../review-assets")
    private val reports = File("build/reports")
    private val motion = GraphicsTokens.motion
    private val compact = motion.compact
    private val comfortable = motion.comfortable

    private fun savePng(name: String, bitmap: android.graphics.Bitmap) {
        reports.mkdirs()
        assets.mkdirs()
        MotionRaster.write(bitmap, File(reports, name))
        MotionRaster.write(bitmap, File(assets, name))
        assertTrue(File(assets, name).length() > 0)
    }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(file.readBytes())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun MO_001_spring_physics_from_tokens() {
        assertEquals("0.1", motion.version)
        assertEquals(1f, compact.mass)
        assertEquals(400f, compact.stiffness)
        assertEquals(28f, compact.damping)
        val samples = MotionPhysics.integrateSettled(
            from = 0f,
            target = 1f,
            mass = compact.mass,
            stiffness = compact.stiffness,
            damping = compact.damping
        )
        assertTrue(samples.size > 8, "samples ${samples.size}")
        assertTrue(MotionPhysics.notLinear(samples, 0f, 1f, compact.durationHintMs))
        assertTrue(MotionPhysics.overshoot(samples, 0f, 1f), "compact should overshoot")
        val damped = MotionPhysics.integrateSettled(
            from = 0f,
            target = 1f,
            mass = compact.mass,
            stiffness = compact.stiffness,
            damping = compact.damping * 3f
        )
        assertFalse(MotionPhysics.overshoot(damped, 0f, 1f), "high damping must not overshoot")
        val a = MotionPhysics.accel(0.2f, 0.1f, 1f, compact.mass, compact.stiffness, compact.damping)
        val expected = (-compact.stiffness * (0.2f - 1f) - compact.damping * 0.1f) / compact.mass
        assertTrue(abs(a - expected) < 1e-5f, "ode $a vs $expected")
        val midBez = MotionPhysics.bezier(0.5f, motion.bezierEmphasized)
        assertTrue(abs(midBez - 0.5f) > 0.04f, "emphasized bezier mid $midBez")
        val dir = File(reports, "frames-spring")
        dir.deleteRecursively()
        dir.mkdirs()
        val frames = MotionPhysics.integrate(0f, 1f, compact.mass, compact.stiffness, compact.damping, 400)
        val take = frames.filterIndexed { i, _ -> i % 8 == 0 }.take(24)
        take.forEachIndexed { i, s ->
            MotionRaster.write(MotionRaster.springFrame(s.x, 0f, 1f), File(dir, "f%03d.png".format(i)))
        }
        MotionRaster.encodeFfmpeg(dir, take.size, File(assets, "spring-overshoot.mp4"), fps = 12, gif = false)
        File(reports, "spring-overshoot.mp4").writeBytes(File(assets, "spring-overshoot.mp4").readBytes())
        savePng("spring-curve.png", MotionRaster.springStrip(samples, 0f, 1f))
        assertTrue(File(assets, "spring-overshoot.mp4").length() > 0)
        val motionSrc = File("src/main/kotlin/a3/renderers/android/compose/ComposeMotionApplier.kt").readText()
        assertTrue(motionSrc.contains("token.mass"))
        assertTrue(motionSrc.contains("params.durationHint"))
        assertTrue(motionSrc.contains("Theme.holdScale"))
        assertTrue(motionSrc.contains("STAGE_APPROVA"))
        assertTrue(motionSrc.contains("Theme.crackMs"))
        assertFalse(motionSrc.contains("LinearEasing"))
    }

    @Test
    fun MO_002_shared_element_hash_and_continuity() {
        val id = "card-train"
        val text = "Milano"
        val hash0 = MotionPhysics.nodeIdentity(id, text)
        val from = MotionPhysics.Rect(24f, 24f, 32f, 20f)
        val to = MotionPhysics.Rect(0f, 0f, 160f, 80f)
        val mid = MotionPhysics.sharedRect(from, to, 0.45f, motion.bezierEmphasized)
        assertEquals(hash0, MotionPhysics.nodeIdentity(id, text))
        assertTrue(mid.l <= from.l + 0.5f)
        assertTrue(mid.t <= from.t + 0.5f)
        assertTrue(mid.r >= from.r - 0.5f)
        assertTrue(mid.b >= from.b - 0.5f)
        val dir = File(reports, "frames-shared")
        dir.deleteRecursively()
        dir.mkdirs()
        val ts = listOf(0f, 0.15f, 0.3f, 0.45f, 0.6f, 0.75f, 0.9f, 1f)
        ts.forEachIndexed { i, t ->
            val rect = MotionPhysics.sharedRect(from, to, t, motion.bezierEmphasized)
            MotionRaster.write(MotionRaster.sharedFrame(rect, text), File(dir, "f%03d.png".format(i)))
        }
        MotionRaster.encodeFfmpeg(dir, ts.size, File(assets, "shared-element.gif"), fps = 8, gif = true)
        File(reports, "shared-element.gif").writeBytes(File(assets, "shared-element.gif").readBytes())
        val morph = File("src/main/kotlin/a3/renderers/android/compose/ComposeMorphApplier.kt").readText()
        assertTrue(morph.contains("plan.shared"))
        assertTrue(morph.contains("LaunchedEffect(plan.to, plan.shared)"))
        assertTrue(morph.contains("LocalReducedMotion"))
        assertTrue(morph.contains("scaleX"))
        assertFalse(morph.contains("alpha = morph"))
    }

    @Test
    fun MO_003_fluid_resize_parallel_reflow() {
        val bezier = motion.bezierStandard
        val midW = MotionPhysics.fluidWidth(200f, 400f, 0.5f, bezier)
        assertTrue(midW > 200f && midW < 400f, "width $midW")
        val start = MotionPhysics.fluidBoxes(200f, 400f, 0f, 4, bezier)
        val mid = MotionPhysics.fluidBoxes(200f, 400f, 0.5f, 4, bezier)
        val end = MotionPhysics.fluidBoxes(200f, 400f, 1f, 4, bezier)
        assertEquals(0f, start[1].x)
        assertTrue(mid[1].x > 4f, "item 1 still column if sequential")
        assertTrue(mid[1].x < end[1].x)
        assertTrue(abs(end[1].x - 200f) < 1f)
        savePng("fluid-mid.png", MotionRaster.fluidFrame(midW, mid))
        val renderer = File("src/main/kotlin/a3/renderers/android/compose/ComposeRenderer.kt").readText()
        assertTrue(renderer.contains("FluidResizeContainer"))
    }

    @Test
    fun MO_004_inertia_inherits_velocity() {
        val mid = MotionPhysics.State(0.4f, 1.2f)
        val gestureV = 6f
        val inherited = MotionPhysics.inheritVelocity(mid, gestureV)
        assertEquals(mid.x, inherited.x)
        assertEquals(gestureV, inherited.v)
        val later = MotionPhysics.afterMs(
            inherited, 1f, comfortable.mass, comfortable.stiffness, comfortable.damping, 50f
        )
        val reset = MotionPhysics.afterMs(
            MotionPhysics.State(mid.x, 0f), 1f, comfortable.mass, comfortable.stiffness, comfortable.damping, 50f
        )
        val expectedCoast = mid.x + gestureV * 0.05f
        assertTrue(
            abs(later.x - expectedCoast) < abs(reset.x - expectedCoast),
            "inherited ${later.x} reset ${reset.x} mid ${mid.x} coast $expectedCoast"
        )
        assertTrue(later.x != reset.x)
        val src = File("src/main/kotlin/a3/renderers/android/compose/ComposeMotionApplier.kt").readText()
        assertTrue(src.contains("inheritVelocity"))
    }

    @Test
    fun MO_005_ripple_from_contact_not_center() {
        val ox = 24
        val oy = 96
        val frame = MotionRaster.rippleFrame(ox, oy, 18f)
        savePng("ripple.png", frame)
        val (px, py) = MotionRaster.peakLumaOffset(frame)
        val toOrigin = MotionRaster.dist(px, py, ox, oy)
        val toCenter = MotionRaster.dist(px, py, 60, 60)
        assertTrue(toOrigin < toCenter, "peak ($px,$py) origin=($ox,$oy) center dist $toCenter origin dist $toOrigin")
        assertTrue(toOrigin < 14f, "ring too far $toOrigin")
        val catalog = File("src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt").readText()
        assertTrue(catalog.contains("contactRipple"))
        assertTrue(catalog.contains("indication = null") || File("src/main/kotlin/a3/renderers/android/compose/Motion.kt").readText().contains("indication = null"))
    }

    @Test
    fun MO_006_rubber_banding_stretch_then_spring() {
        val rest = MotionRaster.rubberFrame(0f)
        val stretch = MotionRaster.rubberFrame(80f)
        savePng("rubber-band-stretch.png", stretch)
        val bottomRest = MotionRaster.contentBottom(rest)
        val bottomStretch = MotionRaster.contentBottom(stretch)
        assertTrue(bottomStretch > bottomRest, "stretch $bottomStretch rest $bottomRest")
        val elastic = MotionPhysics.rubberStretch(80f)
        val linear = MotionPhysics.rubberLinear(80f)
        assertTrue(elastic < linear)
        val ret = MotionPhysics.integrateSettled(
            from = elastic,
            target = 0f,
            mass = comfortable.mass,
            stiffness = comfortable.stiffness,
            damping = comfortable.damping
        )
        assertTrue(MotionPhysics.notLinear(ret, elastic, 0f, 240))
        val catalog = File("src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt").readText()
        assertTrue(catalog.contains("rubberBand"))
        val motionKt = File("src/main/kotlin/a3/renderers/android/compose/Motion.kt").readText()
        assertTrue(motionKt.contains("runSpring") || motionKt.contains("MotionPhysics.step"))
        assertTrue(motionKt.contains("target = 0f") || motionKt.contains("step(s, 0f"))
    }

    @Test
    fun MO_007_reduced_motion_zeroes_l3() {
        assertTrue(motion.reducedZeroes)
        val spring = MotionPhysics.integrateSettled(0f, 1f, compact.mass, compact.stiffness, compact.damping, reduced = true)
        assertEquals(1, spring.size)
        assertEquals(1f, spring.first().x)
        assertEquals(0f, spring.first().v)
        val ripple = MotionRaster.rippleFrame(24, 96, 40f, reduced = true)
        val (px, py) = MotionRaster.peakLumaOffset(ripple)
        assertTrue(MotionRaster.dist(px, py, 24, 96) > 20f || android.graphics.Color.red(ripple.getPixel(24, 96)) < 40)
        val rest = MotionRaster.contentBottom(MotionRaster.rubberFrame(80f, reduced = true))
        val zero = MotionRaster.contentBottom(MotionRaster.rubberFrame(0f, reduced = true))
        assertEquals(zero, rest)
        val morph = File("src/main/kotlin/a3/renderers/android/compose/ComposeMorphApplier.kt").readText()
        assertTrue(morph.contains("if (reduced)"))
        val applier = File("src/main/kotlin/a3/renderers/android/compose/ComposeMotionApplier.kt").readText()
        assertTrue(applier.contains("if (reduced)"))
        val dir = File(reports, "frames-reduced")
        dir.deleteRecursively()
        dir.mkdirs()
        val moving = MotionPhysics.integrate(0f, 1f, compact.mass, compact.stiffness, compact.damping, 360)
        val picked = moving.filterIndexed { i, _ -> i % 10 == 0 }.take(12)
        picked.forEachIndexed { i, s ->
            MotionRaster.write(
                MotionRaster.comparisonFrame(s.x, 1f, 0f, 1f),
                File(dir, "f%03d.png".format(i))
            )
        }
        MotionRaster.encodeFfmpeg(dir, picked.size, File(assets, "reduced-motion-comparison.gif"), fps = 8, gif = true)
        File(reports, "reduced-motion-comparison.gif").writeBytes(File(assets, "reduced-motion-comparison.gif").readBytes())
    }

    @Test
    fun MO_008_epistemic_axis_and_regression_hooks() {
        assertEquals(2800, Theme.heldPulseMs)
        assertEquals(800, Theme.unknownShimmerMs)
        val motionSource = File("src/main/kotlin/a3/renderers/android/compose/ExposureMotion.kt").readText()
        assertTrue(motionSource.contains("Theme.heldPulseMs"))
        assertTrue(motionSource.contains("Theme.unknownShimmerMs"))
        assertTrue(motionSource.contains("tween(Theme.heldPulseMs, easing = LinearEasing)"))
        assertTrue(motionSource.contains("tween(Theme.unknownShimmerMs, easing = LinearEasing)"))
        assertEquals(1, Regex("rememberInfiniteTransition\\(").findAll(motionSource).count())
        val interpreter = a3.renderers.android.core.interp.A3UIInterpreter()
        val t = java.time.Instant.parse("2026-08-27T08:00:00Z")
        val spec = a3.a3ui.model.MotionSpec(280.0, 24.0, "standard", 240)
        fun axisOut(axis: a3.a3ui.model.EpistemicAxis) = interpreter.interpret(
            a3.a3ui.model.A3UISurface(
                id = "s1",
                projectionRef = "p1",
                presentationRef = "ps1",
                lineage = a3.projection.model.CausalLineage("ctx", 1, "e1"),
                densityHint = "comfortable",
                colorTokens = listOf("accent"),
                motion = spec,
                morph = a3.a3ui.model.MorphSpec("ps1", "shared-element", emptyList(), spec),
                gestures = a3.a3ui.model.GestureMap(emptyList()),
                haptics = a3.a3ui.model.HapticMap(emptyList()),
                nodes = listOf(a3.a3ui.model.Node("n1", "text", axis = axis)),
                bindings = listOf(a3.a3ui.model.Binding("train.price", "n1", "content")),
                producedAt = t
            ),
            a3.projection.model.PresentationState(
                id = "ps1",
                sourceStateVersion = 1,
                producedAt = t,
                atoms = listOf(a3.projection.model.PresentationAtom("price", "train.price", "12.40", 60)),
                lineage = a3.projection.model.CausalLineage("ctx", 1, "e1")
            ),
            a3.renderers.android.core.model.RendererContext(
                formFactor = "phone",
                density = "comfortable",
                tokens = java.util.TreeMap<String, a3.renderers.android.core.model.ColorValue>().apply {
                    put("accent", a3.renderers.android.core.model.ColorValue(0, 90, 200))
                },
                clock = a3.core.time.FixedClock(t),
                reducedMotion = false
            )
        )
        composeRule.mainClock.autoAdvance = false
        var shown by androidx.compose.runtime.mutableStateOf(
            axisOut(a3.a3ui.model.EpistemicAxis(status = a3.a3ui.model.EpistemicStatus.HELD))
        )
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) { ComposeRenderer(shown) }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("n1-held").assertIsDisplayed()
        composeRule.onNodeWithTag("n1-motion-pulse", useUnmergedTree = true).assertIsDisplayed()
        shown = axisOut(a3.a3ui.model.EpistemicAxis(support = a3.a3ui.model.EpistemicSupport.UNKNOWN))
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("n1-uncertain").assertIsDisplayed()
        composeRule.onNodeWithTag("n1-motion-shimmer", useUnmergedTree = true).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = true
        assertEquals(0, a3.renderers.android.compose.Exposure.motionMs(
            a3.a3ui.model.EpistemicAxis(status = a3.a3ui.model.EpistemicStatus.HELD),
            reduced = true
        ))
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/GlassSurface.kt").exists())
        assertTrue(File("src/main/resources/a3ui-graphics/motion.json").exists())
        assertEquals("a3ui-graphics-v0.1", File("src/main/resources/a3ui-graphics/VERSION").readText().trim())
    }

    @Test
    fun MO_009_freeze_only_compose_renderers() {
        val root = File("../..")
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
            "broker/",
            "launcher/"
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
            "review-assets/"
        )
        val ignore = listOf(".kotlin/", ".DS_Store")
        for (line in porcelain.lineSequence().filter { it.isNotBlank() }) {
            val path = line.drop(3).trim().removePrefix("?? ").let {
                if (it.contains(" -> ")) it.substringAfter(" -> ") else it
            }
            if (ignore.any { path.startsWith(it) }) continue
            assertTrue(allowed.any { path.startsWith(it) }, "unexpected path $line")
        }
        val mp4 = File(assets, "spring-overshoot.mp4")
        if (mp4.exists()) println("MO assets sha1 spring-overshoot=" + sha1(mp4))
    }
}
