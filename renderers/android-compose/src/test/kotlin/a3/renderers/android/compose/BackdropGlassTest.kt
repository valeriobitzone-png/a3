package a3.renderers.android.compose

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.a3ui.engine.EpistemicFacts
import a3.a3ui.engine.SurfaceComposer
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
import a3.core.time.SequentialIdGenerator
import a3.core.world.api.Claim
import a3.projection.model.CausalLineage
import a3.projection.model.Density
import a3.projection.model.FormFactorHints
import a3.projection.model.PresentationAtom
import a3.projection.model.PresentationState
import a3.projection.model.Projection
import a3.projection.model.ProjectionStatus
import a3.renderers.android.core.interp.A3UIInterpreter
import a3.renderers.android.core.model.ColorValue
import a3.renderers.android.core.model.RenderedOutput
import a3.renderers.android.core.model.RendererContext
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BackdropGlassTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val root = File("../..")
    private val assets = File(root, "review-assets")
    private val reports = File("build/reports")
    private val t = Instant.parse("2026-08-27T08:00:00Z")

    private fun save(name: String, bitmap: android.graphics.Bitmap) {
        reports.mkdirs()
        assets.mkdirs()
        GlassRaster.write(bitmap, File(reports, name))
        GlassRaster.write(bitmap, File(assets, name))
        assertTrue(File(assets, name).length() > 0)
    }

    private fun chroma(bitmap: android.graphics.Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Float {
        val pixels = IntArray((x1 - x0) * (y1 - y0))
        var i = 0
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                pixels[i++] = bitmap.getPixel(x, y)
                x++
            }
            y++
        }
        return GlassBackdrop.chroma(pixels)
    }

    @Test
    fun GB_001_glass_blurs_scene_not_white_slab() {
        assertEquals("shared-background", GlassBackdrop.TECHNIQUE)
        val glassSrc = File("src/main/kotlin/a3/renderers/android/compose/GlassSurface.kt").readText()
        assertTrue(!glassSrc.contains(".background(Color.White)"), glassSrc.take(80))
        val wall = GlassRaster.wallpaper(200, 140)
        val replica = GlassRaster.paint(wall, blur = true, vibrancy = true, highlights = false, shadows = false, fill = false)
        val filled = GlassRaster.paint(wall, blur = true, vibrancy = true, highlights = false, shadows = false, fill = true)
        val white = GlassRaster.paint(
            GlassRaster.solid(200, 140, Color.WHITE),
            blur = true,
            vibrancy = false,
            highlights = false,
            shadows = false,
            fill = true
        )
        val replicaChroma = chroma(replica, 40, 30, 160, 110)
        val filledChroma = chroma(filled, 40, 30, 160, 110)
        val whiteChroma = chroma(white, 40, 30, 160, 110)
        assertTrue(replicaChroma > 0.15f, "replica not wallpaper chroma=$replicaChroma")
        assertTrue(filledChroma > whiteChroma + 0.05f, "filled=$filledChroma white=$whiteChroma")
        val calendar = calendarOutput()
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                ComposeRenderer(calendar)
            }
        }
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("glass-backdrop-replica", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        composeRule.onNodeWithTag("glass-backdrop-sharp", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("glass-surface", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        val overlay = ExposureRaster.paint(calendar)
        val catalog = GlassRaster.paint(
            GlassRaster.wallpaper(overlay.width, overlay.height),
            blur = true,
            overlayInk = overlay
        )
        val catalogChroma = chroma(catalog, 20, 20, catalog.width - 20, catalog.height - 20)
        assertTrue(catalogChroma > whiteChroma + 0.05f, "catalog=$catalogChroma white=$whiteChroma")
        save("catalogo-reale-dopo.png", catalog)
    }

    @Test
    fun GB_002_gutter_sharp_vs_blurred_ring() {
        val wall = GlassRaster.wallpaper(200, 140)
        val sharp = GlassRaster.paint(wall, blur = false, vibrancy = false, highlights = false, shadows = false)
        val glass = GlassRaster.paint(wall, blur = true, vibrancy = false, highlights = false, shadows = false)
        val ratio = GlassRaster.edgeEnergy(glass) / GlassRaster.edgeEnergy(sharp)
        assertTrue(ratio < GlassRaster.BLUR_ENERGY_RATIO_MAX, "blur ratio $ratio")
        val gutter = chroma(glass, 0, 0, 8, 8)
        val ring = chroma(glass, 40, 30, 160, 110)
        val whiteRing = chroma(
            GlassRaster.paint(
                GlassRaster.solid(200, 140, Color.WHITE),
                blur = true,
                vibrancy = false,
                highlights = false,
                shadows = false
            ),
            40,
            30,
            160,
            110
        )
        assertTrue(gutter > ring, "gutter=$gutter ring=$ring")
        assertTrue(ring > whiteRing + 0.04f, "ring=$ring white=$whiteRing")
        save("anello-sfocato.png", glass)
    }

    @Test
    fun GB_003_material_color_utilities_parity_and_aa() {
        val extractSrc = File("src/main/kotlin/a3/renderers/android/compose/DynamicPalette.kt").readText()
        assertTrue(extractSrc.contains("QuantizerCelebi"))
        assertTrue(extractSrc.contains("CorePalette"))
        assertTrue(extractSrc.contains("Score.score"))
        val wall = DynamicPalette.fixtureWallpaper(80, 80)
        val a = DynamicPalette.extract(wall, 80, 80)
        val b = DynamicPalette.extract(wall.copyOf(), 80, 80)
        assertEquals(a, b)
        assertEquals(DynamicPalette.SOURCE_EXTRACTED, a.source)
        assertEquals(DynamicPalette.tokenInk(), a.ink)
        assertEquals(DynamicPalette.tokenPaper(), a.paper)
        val min = GraphicsTokens.colors.minRatio
        assertTrue(DynamicPalette.contrast(a.ink, a.paper) >= min)
        assertTrue(DynamicPalette.contrast(a.primary, a.paper) >= min)
        assertTrue(DynamicPalette.contrast(a.secondary, a.paper) >= min)
        save("palette-extracted.png", DynamicRaster.paletteStrip(a))
    }

    @Test
    fun GB_004_badges_full_opacity_over_true_glass() {
        val overlay = ExposureRaster.paint(unknownOutput())
        val glass = GlassRaster.paint(GlassRaster.wallpaper(320, 80), blur = true, overlayInk = overlay)
        var stamped = 0
        var y = 0
        while (y < overlay.height) {
            var x = 0
            while (x < overlay.width) {
                val src = overlay.getPixel(x, y)
                if (src != Color.WHITE && Color.alpha(src) == 255) {
                    val dst = glass.getPixel(x, y)
                    assertEquals(255, Color.alpha(dst), "badge alpha at $x,$y")
                    assertEquals(src, dst)
                    stamped++
                }
                x++
            }
            y++
        }
        assertTrue(stamped > 20)
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                ComposeRenderer(unknownOutput())
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("n1-uncertain").assertIsDisplayed()
        save("gb-004-badge-on-glass.png", glass)
    }

    @Test
    fun GB_005_shared_background_cross_platform() {
        assertEquals("shared-background", GlassBackdrop.TECHNIQUE)
        val android = File("src/main/kotlin/a3/renderers/android/compose/GlassBackdrop.kt").readText()
        val mac = File(root, "renderers/mac-compose/src/main/kotlin/a3/renderers/mac/compose/GlassBackdrop.kt").readText()
        assertTrue(android.contains("shared-background"))
        assertTrue(mac.contains("shared-background"))
        assertTrue(android.contains("colorAt"))
        assertTrue(mac.contains("colorAt"))
        val review = File(root, "REVIEW_RENDERER_BACKDROP.md").readText()
        assertTrue(review.contains("shared-background"))
        assertTrue(review.contains("BlurView") && review.contains("scartato"))
    }

    @Test
    fun GB_006_regression_pointers() {
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/GlassSurface.kt").exists())
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/MotionPhysics.kt").exists())
        assertTrue(File("src/main/kotlin/a3/renderers/android/compose/DynamicPalette.kt").exists())
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                ComposeRenderer(calendarOutput())
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("glass-surface", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun GB_007_freeze_only_compose_renderers() {
        fun diff(vararg paths: String): String {
            val proc = ProcessBuilder("git", "diff", "--stat", "--", *paths)
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals(0, proc.waitFor())
            return out
        }
        val frozen = diff(
            "core/", "a3ui/", "broker/", "agent/", "launcher/", "adapters/",
            "showcase/", "prediction/", "projection/", "intent-model/",
            "renderers/android-core/"
        )
        assertTrue(frozen.isBlank(), frozen)
        val status = ProcessBuilder("git", "status", "--porcelain")
            .directory(root).redirectErrorStream(true).start()
        val porcelain = status.inputStream.bufferedReader().readText()
        assertEquals(0, status.waitFor())
        val allowed = listOf(
            "renderers/android-compose/",
            "renderers/mac-compose/",
            "REVIEW_RENDERER_BACKDROP.md",
            "REVIEW_RENDERER_MOTION.md",
            "REVIEW_RENDERER_DYNAMIC.md",
            "REVIEW_RENDERER_SHADERS.md",
            "REVIEW_RENDERER_SENSORY.md",
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
        assertTrue(File(assets, "catalogo-reale-prima.png").length() > 0)
        assertTrue(File(assets, "catalogo-reale-dopo.png").length() > 0)
        assertTrue(File(assets, "anello-sfocato.png").length() > 0)
    }

    private fun calendarOutput(): RenderedOutput {
        val presentation = PresentationState(
            id = "ps_cal",
            sourceStateVersion = 0,
            producedAt = t,
            atoms = listOf(
                PresentationAtom("price", "cal.meeting", "Riunione 09:00 — Ada", 50),
                PresentationAtom("price", "cal.flight", "Volo 14:30 — FCO→LIN", 50),
                PresentationAtom("price", "cal.hotel", "Hotel Milano", 50),
                PresentationAtom("price", "cal.dinner", "Prenotazione ristorante", 50)
            ),
            lineage = CausalLineage("ctx", 0, "ev_demo")
        )
        val agingObserved = t.minusSeconds(3600)
        val agingExpires = t.plusSeconds(600)
        val facts = mapOf(
            "cal.meeting" to EpistemicFacts(
                claim = Claim("cal.meeting", "Riunione 09:00 — Ada", 1.0, "calendar", t, t.plusSeconds(86400)),
                actionPhase = "completed"
            ),
            "cal.flight" to EpistemicFacts(
                claim = Claim("cal.flight", "Volo 14:30 — FCO→LIN", 0.7, "calendar", agingObserved, agingExpires),
                actionPhase = "created"
            ),
            "cal.hotel" to EpistemicFacts(
                claim = Claim("cal.hotel", "Hotel Milano", 0.4, "calendar", t, t.minusSeconds(1)),
                admissionHeld = true,
                actionPhase = "unknown"
            ),
            "cal.dinner" to EpistemicFacts(
                claim = Claim(
                    "cal.dinner",
                    "Prenotazione ristorante",
                    0.9,
                    "calendar",
                    t,
                    t.plusSeconds(86400)
                ),
                revisionContradicted = true,
                compensationPhase = "compensated"
            )
        )
        val tree = SurfaceComposer.compose(presentation, t, facts)
        val surface = DeterministicA3UICompiler(t, SequentialIdGenerator())
            .compile(
                Projection(
                    id = "proj_cal",
                    presentationId = "ps_cal",
                    contextRef = "ctx",
                    formFactorHints = FormFactorHints("phone", Density.COMFORTABLE),
                    interactionRequirements = listOf("attend"),
                    lineage = CausalLineage("ctx", 0, "ev_demo"),
                    status = ProjectionStatus.PROPOSED
                ),
                presentation
            )
            .copy(nodes = tree.nodes, bindings = tree.bindings)
        return A3UIInterpreter().interpret(
            surface,
            presentation,
            RendererContext(
                formFactor = "phone",
                density = "comfortable",
                tokens = TreeMap<String, ColorValue>().apply { put("accent", ColorValue(0, 90, 200)) },
                clock = FixedClock(t),
                reducedMotion = true
            )
        )
    }

    private fun unknownOutput() = A3UIInterpreter().interpret(
        A3UISurface(
            id = "s1",
            projectionRef = "p1",
            presentationRef = "ps1",
            lineage = CausalLineage("ctx", 1, "e1"),
            densityHint = "comfortable",
            colorTokens = listOf("accent"),
            motion = MotionSpec(280.0, 24.0, "standard", 240),
            morph = MorphSpec("ps1", "shared-element", emptyList(), MotionSpec(280.0, 24.0, "standard", 240)),
            gestures = GestureMap(emptyList()),
            haptics = HapticMap(emptyList()),
            nodes = listOf(Node("n1", "text", axis = EpistemicAxis(support = EpistemicSupport.UNKNOWN))),
            bindings = listOf(Binding("train.price", "n1", "content")),
            producedAt = t
        ),
        PresentationState(
            id = "ps1",
            sourceStateVersion = 1,
            producedAt = t,
            atoms = listOf(PresentationAtom("price", "train.price", "12.40", 60)),
            lineage = CausalLineage("ctx", 1, "e1")
        ),
        RendererContext(
            formFactor = "phone",
            density = "comfortable",
            tokens = TreeMap<String, ColorValue>().apply { put("accent", ColorValue(0, 90, 200)) },
            clock = FixedClock(t),
            reducedMotion = true
        )
    )
}
