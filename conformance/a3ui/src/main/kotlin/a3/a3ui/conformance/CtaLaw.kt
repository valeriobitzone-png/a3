package a3.a3ui.conformance

class ConformanceReject(val code: String, reason: String) : IllegalArgumentException("$code: $reason")

object CtaLaw {
    const val FORBID_REASON = "contradicted: resolve conflict first"
    const val PENDING_LABEL = "in verifica"
    const val STALE_WARNING = "stale 2h"

    fun judgeSurface(observed: ObservedSurface) {
        when (observed.mark.uppercase()) {
            "CONTRADICTED" -> {
                if (observed.ctaEnabled) {
                    throw ConformanceReject("AC-001", "CONTRADICTED → CTA enabled")
                }
                if (observed.reason.isNullOrBlank()) {
                    throw ConformanceReject("AC-001", "CONTRADICTED CTA disabled without visible reason")
                }
            }
            "PENDING" -> {
                if (observed.ctaEnabled && !observed.confirmationRequired) {
                    throw ConformanceReject("AC-002", "PENDING → CTA enabled without confirmation")
                }
                if (observed.spinner) {
                    throw ConformanceReject("AC-002", "PENDING used a spinner instead of reserved slot")
                }
            }
            "UNKNOWN" -> {
                if (observed.ctaEnabled && !observed.confirmationRequired) {
                    throw ConformanceReject("AC-003", "UNKNOWN → CTA enabled without confirmation")
                }
            }
            "STALE" -> {
                if (observed.ctaEnabled && observed.warning.isNullOrBlank()) {
                    throw ConformanceReject("AC-004", "STALE → CTA without warning")
                }
            }
            "FACT" -> {
                if (!observed.ctaEnabled && observed.reason.isNullOrBlank()) {
                    throw ConformanceReject("AC-005", "FACT → CTA disabled without reason")
                }
            }
        }
        if (observed.stateDescription.isNullOrBlank()) {
            throw ConformanceReject("AC-006", "Mark without stateDescription")
        }
    }

    fun judgeRender(observation: RenderObservation) {
        for (surface in observation.surfaces) {
            judgeSurface(surface)
        }
        if (observation.reducedMotion && !observation.marksVisible) {
            throw ConformanceReject("AC-007", "reduced motion ON → marks disappeared")
        }
        if (observation.reducedMotion && observation.motionMs != 0) {
            throw ConformanceReject("AC-007", "reduced motion ON → motion not zero")
        }
        if (observation.blurOff && !observation.marksReadable) {
            throw ConformanceReject("AC-008", "blur OFF → marks unreadable")
        }
    }

    fun fromLawful(fixture: SurfaceFixture): ObservedSurface = ObservedSurface(
        mark = fixture.mark,
        ctaEnabled = fixture.ctaEnabled,
        confirmationRequired = fixture.confirmationRequired,
        reason = fixture.reason,
        warning = fixture.warning,
        stateDescription = fixture.stateDescription,
        spinner = fixture.spinner
    )
}
