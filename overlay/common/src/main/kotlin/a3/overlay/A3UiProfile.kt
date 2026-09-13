package a3.overlay

/** SPEC_A3UI §9 chrome profiles. Law for blur, particles, and motion — not a silent downgrade. */
enum class A3UiProfile {
    HIGH,
    MID,
    BLUR_OFF,
    PARTICLES_OFF;

    fun wire(): String = name

    companion object {
        fun parse(raw: String?): A3UiProfile? {
            if (raw.isNullOrBlank()) return null
            val key = raw.trim().uppercase().replace('-', '_')
            if (key == "AUTO" || key == "DEFAULT") return null
            return entries.firstOrNull { it.name == key }
        }
    }
}

enum class BlurMode { FULL, REDUCED, OFF }

enum class MotionMode { FULL, SIMPLIFIED }

enum class ProfileSource {
    AUTO,
    MANUAL;

    fun wire(): String = name
}

data class FeatureMatrix(
    val blur: BlurMode,
    val blurRadiusPx: Int,
    val particles: Boolean,
    val motion: MotionMode,
    val dynamicColor: Boolean,
    val noise: Boolean
) {
    val blurEnabled: Boolean get() = blur != BlurMode.OFF

    companion object {
        fun of(profile: A3UiProfile): FeatureMatrix = when (profile) {
            A3UiProfile.HIGH -> FeatureMatrix(
                blur = BlurMode.FULL,
                blurRadiusPx = 18,
                particles = true,
                motion = MotionMode.FULL,
                dynamicColor = true,
                noise = true
            )
            A3UiProfile.MID -> FeatureMatrix(
                blur = BlurMode.REDUCED,
                blurRadiusPx = 6,
                particles = false,
                motion = MotionMode.SIMPLIFIED,
                dynamicColor = true,
                noise = false
            )
            A3UiProfile.BLUR_OFF -> FeatureMatrix(
                blur = BlurMode.OFF,
                blurRadiusPx = 0,
                particles = false,
                motion = MotionMode.FULL,
                dynamicColor = true,
                noise = false
            )
            A3UiProfile.PARTICLES_OFF -> FeatureMatrix(
                blur = BlurMode.FULL,
                blurRadiusPx = 18,
                particles = false,
                motion = MotionMode.FULL,
                dynamicColor = true,
                noise = true
            )
        }
    }
}

enum class GpuTier { HIGH, MID, SOFTWARE }

data class DeviceSignals(
    val name: String,
    val ramMb: Int,
    val screenW: Int,
    val screenH: Int,
    val gpuTier: GpuTier,
    val glesVersion: Int,
    val hardware: String,
    val platform: String
) {
    val pixels: Int get() = screenW * screenH
}

data class ProfileDecision(
    val active: A3UiProfile,
    val detected: A3UiProfile,
    val source: ProfileSource,
    val override: A3UiProfile?,
    val reason: String
) {
    val matrix: FeatureMatrix get() = FeatureMatrix.of(active)
    val pillText: String get() = "${active.wire()} · ${source.wire().lowercase()}"
    val blurMessage: String? get() {
        if (active == A3UiProfile.BLUR_OFF) {
            return if (source == ProfileSource.AUTO) {
                "${OverlayPolicy.BLUR_UNAVAILABLE} (auto ${detected.wire()})"
            } else {
                OverlayPolicy.BLUR_UNAVAILABLE
            }
        }
        return null
    }
}

object ProfileDetect {
    const val RAM_HIGH_MB = 6144
    const val RAM_MID_MB = 3072
    const val PIXEL_HIGH = 1080 * 1920
    const val PIXEL_MID = 720 * 1280

    fun gpuTier(hardware: String, platform: String, glesVersion: Int): GpuTier {
        val blob = "$hardware $platform".lowercase()
        if (
            blob.contains("goldfish") ||
            blob.contains("ranchu") ||
            blob.contains("swiftshader") ||
            blob.contains("emulator") ||
            blob.contains("cuttlefish")
        ) {
            return GpuTier.SOFTWARE
        }
        if (glesVersion > 0 && glesVersion < 0x30000) return GpuTier.MID
        return GpuTier.HIGH
    }

    fun detect(signals: DeviceSignals): A3UiProfile {
        val pixels = signals.pixels
        if (signals.gpuTier == GpuTier.SOFTWARE || signals.ramMb < RAM_MID_MB || pixels < PIXEL_MID) {
            return A3UiProfile.BLUR_OFF
        }
        if (signals.ramMb < RAM_HIGH_MB || pixels < PIXEL_HIGH || signals.gpuTier == GpuTier.MID) {
            return A3UiProfile.MID
        }
        return A3UiProfile.HIGH
    }

    fun resolve(detected: A3UiProfile, override: A3UiProfile?): ProfileDecision {
        val active = override ?: detected
        val source = if (override != null) ProfileSource.MANUAL else ProfileSource.AUTO
        val reason = buildString {
            append("active=").append(active.wire())
            append(" source=").append(source.name.lowercase())
            append(" detected=").append(detected.wire())
            if (override != null) append(" override=").append(override.wire())
        }
        return ProfileDecision(active, detected, source, override, reason)
    }

    /**
     * Heuristic inputs for Nothing Phone (3). These are device-class signals for
     * auto-detect tests, not frame-time measurements.
     */
    fun nothingPhone3(): DeviceSignals = DeviceSignals(
        name = "Nothing Phone (3)",
        ramMb = 12288,
        screenW = 1264,
        screenH = 2736,
        gpuTier = GpuTier.HIGH,
        glesVersion = 0x30002,
        hardware = "qcom",
        platform = "sun"
    )
}

interface ProfileOverrideStore {
    fun read(): String?
    fun write(raw: String?)
}

class MemoryProfileStore(initial: String? = null) : ProfileOverrideStore {
    private var value: String? = initial
    override fun read(): String? = value
    override fun write(raw: String?) {
        value = raw
    }
}

class FileProfileStore(private val file: java.io.File) : ProfileOverrideStore {
    override fun read(): String? {
        if (!file.isFile) return null
        return file.readText().trim().ifBlank { null }
    }

    override fun write(raw: String?) {
        if (raw.isNullOrBlank()) {
            if (file.exists()) file.delete()
            return
        }
        file.parentFile?.mkdirs()
        file.writeText(raw)
    }
}

/** Host RAM/screen/GPU for Mac/JVM auto-detect. Not an Android gfxinfo dump. */
object JvmHostSignals {
    fun ramMb(): Int {
        val bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        val bytes = try {
            val method = bean.javaClass.methods.firstOrNull {
                it.name == "getTotalMemorySize" || it.name == "getTotalPhysicalMemorySize"
            }
            (method?.invoke(bean) as? Long) ?: -1L
        } catch (_: Exception) {
            -1L
        }
        return if (bytes > 0L) (bytes / (1024L * 1024L)).toInt() else 8192
    }

    fun screen(): Pair<Int, Int> {
        return try {
            val size = java.awt.Toolkit.getDefaultToolkit().screenSize
            size.width to size.height
        } catch (_: Exception) {
            1440 to 900
        }
    }

    fun detect(): DeviceSignals {
        val (w, h) = screen()
        val api = System.getProperty("skiko.renderApi").orEmpty()
        val gpu = if (api.equals("SOFTWARE", ignoreCase = true)) GpuTier.SOFTWARE else GpuTier.HIGH
        return DeviceSignals(
            name = System.getProperty("os.name") ?: "jvm",
            ramMb = ramMb(),
            screenW = w,
            screenH = h,
            gpuTier = gpu,
            glesVersion = 0x30000,
            hardware = System.getProperty("os.arch") ?: "",
            platform = "jvm"
        )
    }
}

object ProfileSession {
    fun decide(signals: DeviceSignals, store: ProfileOverrideStore): ProfileDecision {
        val detected = ProfileDetect.detect(signals)
        val override = A3UiProfile.parse(store.read())
        return ProfileDetect.resolve(detected, override)
    }

    fun setOverride(store: ProfileOverrideStore, profile: A3UiProfile?) {
        store.write(profile?.wire())
    }
}
