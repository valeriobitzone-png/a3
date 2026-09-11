package a3.showcase

import java.io.File

/**
 * Consumes the a3ui-graphics-v0.1 snapshot packaged by the compose renderers.
 * Showcase does not own tokens; it only reads the same JSON the renderers copy.
 */
object ShowcaseTokens {
    data class Spring(val mass: Float, val stiffness: Float, val damping: Float, val durationHintMs: Int)
    data class Tone(
        val frequencyHz: Float,
        val semitones: Int,
        val durationMs: Int,
        val attackMs: Int,
        val decayMs: Int,
        val sustain: Float,
        val releaseMs: Int
    )
    data class Audio(
        val invariant: String,
        val synthesis: String,
        val files: Boolean,
        val voiceCeiling: Float,
        val morph: Tone
    )
    data class Intensity(val amplitude: Float, val durationMs: Int, val texture: String)
    data class Pattern(val intensity: String, val count: Int, val gapMs: Int)
    data class Haptic(
        val gapMs: Int,
        val light: Intensity,
        val medium: Intensity,
        val tap: Pattern,
        val doubleTap: Pattern
    )
    data class Highlight(val widthPx: Int, val startAlpha: Float, val endAlpha: Float)
    data class Snapshot(
        val fillOpacity: Float,
        val fillColor: Int,
        val blurRadiusPx: Float,
        val vibrancySaturation: Float,
        val radiusPx: Float,
        val superellipseN: Float,
        val nestedMinPx: Float,
        val inner: Highlight,
        val outer: Highlight,
        val acrylicBlurPx: Float,
        val ink: Int,
        val paper: Int,
        val amber: Int,
        val audio: Audio,
        val haptic: Haptic,
        val comfortable: Spring,
        val reducedZeroes: Boolean,
        val keepChroma: Boolean
    )

    const val GRADIENT_SPEED = 0.18f
    const val GRADIENT_AMPLITUDE = 0.35f
    const val TAU = 6.2831853f
    const val REFRACT_X = 1.25f
    const val REFRACT_Y = 0.85f
    const val FREQ_Y = 0.21f
    const val FREQ_X = 0.17f
    const val NOISE_STRENGTH = 0.04f
    const val PARTICLE_COUNT = 12
    const val MASK_FADE_PX = 16f
    const val SAMPLE_RATE = 22050

    val snapshot: Snapshot by lazy { load() }

    fun mixAmt(): Float = snapshot.fillOpacity * 0.45f

    fun load(read: (String) -> String = ::readResource): Snapshot {
        val surfaces = ShowcaseJson.parse(read("surfaces.json"))
        val colors = ShowcaseJson.parse(read("colors.json"))
        val audioJson = ShowcaseJson.parse(read("audio.json"))
        val hapticJson = ShowcaseJson.parse(read("haptic.json"))
        val motion = ShowcaseJson.parse(read("motion.json"))
        val glass = surfaces.obj("glass")
        val acrylic = surfaces.obj("acrylic")
        val pal = colors.obj("palette")
        val morph = audioJson.obj("tones").obj("morph")
        val env = morph.obj("envelope")
        val intensities = hapticJson.obj("intensities")
        val patterns = hapticJson.obj("patterns")
        val gap = hapticJson.int("gapMs")
        val comfortable = motion.obj("springs").obj("comfortable")
        val reduced = motion.obj("reducedMotion")
        val innerStops = glass.obj("innerHighlight").arr("stops")
        val outerStops = glass.obj("outerHighlight").arr("stops")
        val corner = surfaces.obj("container").obj("corner")
        val nested = surfaces.obj("container").obj("nestedRadius")
        fun intensity(name: String): Intensity {
            val s = intensities.obj(name)
            return Intensity(s.num("amplitude"), s.int("durationMs"), s.str("texture"))
        }
        fun pattern(name: String): Pattern {
            val p = patterns.obj(name)
            return Pattern(p.str("intensity"), p.int("count"), p.intOr("gapMs", gap))
        }
        fun stopAlpha(stop: Any?): Float {
            val map = stop as Map<*, *>
            val rgba = map["rgba"] as List<*>
            return (rgba[3] as Number).toFloat()
        }
        return Snapshot(
            fillOpacity = glass.num("fillOpacity"),
            fillColor = parseHex(glass.str("fillColor")),
            blurRadiusPx = glass.num("blurRadiusPx"),
            vibrancySaturation = glass.num("vibrancySaturation"),
            radiusPx = corner.num("radiusPx"),
            superellipseN = corner.num("superellipseN"),
            nestedMinPx = nested.num("minPx"),
            inner = Highlight(
                glass.obj("innerHighlight").int("widthPx"),
                stopAlpha(innerStops[0]),
                stopAlpha(innerStops[1])
            ),
            outer = Highlight(
                glass.obj("outerHighlight").int("widthPx"),
                stopAlpha(outerStops[0]),
                stopAlpha(outerStops[1])
            ),
            acrylicBlurPx = acrylic.num("blurRadiusPx"),
            ink = parseHex(pal.obj("ink").str("hex")),
            paper = parseHex(pal.obj("paper").str("hex")),
            amber = parseHex(pal.obj("amber").str("hex")),
            audio = Audio(
                invariant = audioJson.str("invariant"),
                synthesis = audioJson.str("synthesis"),
                files = audioJson.bool("files"),
                voiceCeiling = audioJson.num("voiceCeiling"),
                morph = Tone(
                    frequencyHz = morph.num("frequencyHz"),
                    semitones = morph.int("semitones"),
                    durationMs = morph.int("durationMs"),
                    attackMs = env.int("attackMs"),
                    decayMs = env.int("decayMs"),
                    sustain = env.num("sustain"),
                    releaseMs = env.int("releaseMs")
                )
            ),
            haptic = Haptic(
                gapMs = gap,
                light = intensity("light"),
                medium = intensity("medium"),
                tap = pattern("tap"),
                doubleTap = pattern("double-tap")
            ),
            comfortable = Spring(
                mass = comfortable.num("mass"),
                stiffness = comfortable.num("stiffness"),
                damping = comfortable.num("damping"),
                durationHintMs = comfortable.int("durationHintMs")
            ),
            reducedZeroes = reduced.bool("zeroAllDurations"),
            keepChroma = reduced.bool("keepChroma")
        )
    }

    private fun parseHex(hex: String): Int {
        val h = hex.removePrefix("#")
        return h.toInt(16) or (0xFF shl 24)
    }

    private fun readResource(name: String): String {
        val stream = ShowcaseTokens::class.java.classLoader?.getResourceAsStream("a3ui-graphics/$name")
        if (stream != null) return stream.reader().use { it.readText() }
        val roots = listOf(
            File("src/main/resources/a3ui-graphics/$name"),
            File("../../renderers/android-compose/src/main/resources/a3ui-graphics/$name"),
            File("../android-compose/src/main/resources/a3ui-graphics/$name")
        )
        val file = roots.firstOrNull { it.exists() }
        if (file != null) return file.readText()
        error("missing a3ui-graphics token $name")
    }
}
