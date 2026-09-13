package a3.a3ui.conformance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

object CategoryJudge {
    private val mapper = ObjectMapper()

    fun judgeFile(file: File): Nothing {
        val root = mapper.readTree(file)
        val code = root.path("reject_code").asText()
        val observed = observation(root.path("observed"))
        try {
            CtaLaw.judgeRender(observed)
        } catch (e: ConformanceReject) {
            if (e.code == code) throw e
            throw e
        }
        throw ConformanceReject(code, "implementation accepted a forbidden CTA mapping")
    }

    fun observation(node: JsonNode): RenderObservation {
        val surfaces = node.path("surfaces").map { surface(it) }
        val reduced = node.path("reduced_motion").asBoolean(false)
        val blurOff = node.path("blur_off").asBoolean(false)
        return RenderObservation(
            surfaces = surfaces,
            reducedMotion = reduced,
            blurOff = blurOff,
            marksVisible = node.path("marks_visible").asBoolean(true),
            marksReadable = node.path("marks_readable").asBoolean(true),
            motionMs = node.path("motion_ms").asInt(0)
        )
    }

    private fun surface(node: JsonNode): ObservedSurface = ObservedSurface(
        mark = node.path("mark").asText(),
        ctaEnabled = node.path("cta_enabled").asBoolean(),
        confirmationRequired = node.path("confirmation_required").asBoolean(false),
        reason = node.path("reason").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
        warning = node.path("warning").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
        stateDescription = node.path("state_description").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
        spinner = node.path("spinner").asBoolean(false),
        marksVisible = node.path("marks_visible").asBoolean(true),
        marksReadable = node.path("marks_readable").asBoolean(true),
        reducedMotion = node.path("reduced_motion").asBoolean(false),
        blurOff = node.path("blur_off").asBoolean(false),
        motionMs = node.path("motion_ms").asInt(0)
    )
}
