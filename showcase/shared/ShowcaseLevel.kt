package a3.showcase

enum class ShowcaseLevel {
    ALL,
    GLASS,
    AXIS,
    MOTION,
    DYNAMIC,
    SHADERS,
    SENSORY,
    OVERLAY;

    fun wire(): String = name.lowercase()

    fun flags(): ShowcaseFlags = when (this) {
        ALL -> ShowcaseFlags(
            gradient = true,
            ambient = true,
            modal = true,
            optics = true,
            particles = true,
            sensory = true,
            parallax = true
        )
        GLASS -> ShowcaseFlags()
        AXIS -> ShowcaseFlags()
        MOTION -> ShowcaseFlags()
        DYNAMIC -> ShowcaseFlags()
        SHADERS -> ShowcaseFlags(
            gradient = true,
            ambient = true,
            modal = true,
            parallax = true
        )
        SENSORY -> ShowcaseFlags(
            optics = true,
            particles = true,
            sensory = true
        )
        OVERLAY -> ShowcaseFlags()
    }

    companion object {
        val NAV = listOf(ALL, GLASS, AXIS, MOTION, DYNAMIC, SHADERS, SENSORY, OVERLAY)

        fun parse(raw: String?): ShowcaseLevel {
            if (raw.isNullOrBlank()) return ALL
            return entries.firstOrNull { it.wire() == raw.trim().lowercase() } ?: ALL
        }
    }
}

data class ShowcaseFlags(
    val gradient: Boolean = false,
    val ambient: Boolean = false,
    val modal: Boolean = false,
    val optics: Boolean = false,
    val particles: Boolean = false,
    val sensory: Boolean = false,
    val parallax: Boolean = false
)
