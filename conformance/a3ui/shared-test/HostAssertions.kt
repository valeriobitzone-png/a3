// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui.conformance.ui

import a3.a3ui.conformance.A3UiFixtures
import a3.a3ui.conformance.CtaLaw
import a3.a3ui.conformance.ObservedSurface
import a3.a3ui.conformance.RenderObservation
import a3.a3ui.conformance.SurfaceFixture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.io.File

private val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

fun ComposeContentTestRule.host(
    reduced: Boolean = false,
    blurOff: Boolean = false,
    fixtures: List<SurfaceFixture> = A3UiFixtures.lawful()
) {
    setContent {
        Box(Modifier.size(411.dp, 891.dp)) {
            ConformanceHost(reducedMotion = reduced, blurOff = blurOff, fixtures = fixtures)
        }
    }
    waitForIdle()
}

fun ComposeContentTestRule.exists(tag: String): Boolean =
    onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

fun ComposeContentTestRule.assertLawfulScene() {
    onNodeWithTag("conformance-host").assertIsDisplayed()
    onNodeWithTag("fixture-hotel").assertIsDisplayed()
    onNodeWithTag("fixture-calendar").assertIsDisplayed()
    onNodeWithTag("fixture-train").assertIsDisplayed()
    onNodeWithTag("fixture-flight").assertIsDisplayed()
    onNodeWithTag("cta-calendar").assertIsNotEnabled()
    onNodeWithText(CtaLaw.FORBID_REASON).assertIsDisplayed()
    onNodeWithTag("cta-hotel").assertIsEnabled()
    onNodeWithText(CtaLaw.STALE_WARNING).assertIsDisplayed()
    onNodeWithTag("cta-train").assertIsNotEnabled()
    onNodeWithText(CtaLaw.PENDING_LABEL).assertIsDisplayed()
    onNodeWithTag("pending-slot").assertIsDisplayed()
    check(!exists("pending-spinner"))
    onNodeWithTag("cta-flight").assertIsEnabled()
    onNodeWithTag("fact-baseline").assertIsDisplayed()
}

fun ComposeContentTestRule.observation(
    reduced: Boolean = false,
    blurOff: Boolean = false
): RenderObservation {
    val surfaces = A3UiFixtures.lawful().map { fixture ->
        val cta = onNodeWithTag("cta-${fixture.id}").fetchSemanticsNode()
        ObservedSurface(
            mark = fixture.mark,
            ctaEnabled = !cta.config.contains(SemanticsProperties.Disabled),
            confirmationRequired = fixture.confirmationRequired,
            reason = fixture.reason,
            warning = fixture.warning,
            stateDescription = cta.config.getOrNull(SemanticsProperties.StateDescription)
                ?: fixture.stateDescription,
            spinner = exists("pending-spinner"),
            marksVisible = true,
            marksReadable = true,
            reducedMotion = reduced,
            blurOff = blurOff,
            motionMs = if (reduced) 0 else 240
        )
    }
    return RenderObservation(
        surfaces = surfaces,
        reducedMotion = reduced,
        blurOff = blurOff,
        marksVisible = exists("fixture-calendar") && exists("fixture-hotel") &&
            exists("fixture-train") && exists("fixture-flight"),
        marksReadable = exists("stale-warning") && exists("forbid-reason") && exists("pending-label"),
        motionMs = if (reduced) 0 else 240
    )
}

fun dumpParseTree(root: SemanticsNode): Map<String, Any?> {
    val texts = root.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()
    return linkedMapOf(
        "tag" to root.config.getOrNull(SemanticsProperties.TestTag),
        "text" to texts,
        "enabled" to !root.config.contains(SemanticsProperties.Disabled),
        "stateDescription" to root.config.getOrNull(SemanticsProperties.StateDescription),
        "contentDescription" to root.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
        "children" to root.children.map { dumpParseTree(it) }
    )
}

fun writeTree(file: File, root: SemanticsNode) {
    file.parentFile?.mkdirs()
    file.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dumpParseTree(root)))
}

fun SemanticsNodeInteraction.stateDescription(): String? =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
