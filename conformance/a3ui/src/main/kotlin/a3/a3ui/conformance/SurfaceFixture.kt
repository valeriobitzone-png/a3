package a3.a3ui.conformance

data class SurfaceFixture(
    val id: String,
    val oggetto: String,
    val mark: String,
    val truthClass: String,
    val provenance: String,
    val age: String,
    val confidence: Double,
    val status: String,
    val price: String? = null,
    val slots: List<String> = emptyList(),
    val pendingLabel: String? = null,
    val ctaEnabled: Boolean,
    val ctaLabel: String = "conferma",
    val confirmationRequired: Boolean = false,
    val reason: String? = null,
    val warning: String? = null,
    val stateDescription: String,
    val spinner: Boolean = false,
    val actionsPermitted: List<String> = emptyList(),
    val actionsForbidden: List<String> = emptyList()
) {
    val reasonVisible: Boolean get() = !reason.isNullOrBlank()
    val warningVisible: Boolean get() = !warning.isNullOrBlank()
}

data class ObservedSurface(
    val mark: String,
    val ctaEnabled: Boolean,
    val confirmationRequired: Boolean = false,
    val reason: String? = null,
    val warning: String? = null,
    val stateDescription: String? = null,
    val spinner: Boolean = false,
    val marksVisible: Boolean = true,
    val marksReadable: Boolean = true,
    val reducedMotion: Boolean = false,
    val blurOff: Boolean = false,
    val motionMs: Int = 0
)

data class RenderObservation(
    val surfaces: List<ObservedSurface>,
    val reducedMotion: Boolean = false,
    val blurOff: Boolean = false,
    val marksVisible: Boolean = true,
    val marksReadable: Boolean = true,
    val motionMs: Int = 0
)
