// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MacRendererTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val assets = File("../../review-assets")
    private val reports = File("build/reports")

    private fun save(name: String, image: java.awt.image.BufferedImage) {
        reports.mkdirs()
        assets.mkdirs()
        MacRaster.write(image, File(reports, name))
        MacRaster.write(image, File(assets, name))
        assertTrue(File(assets, name).length() > 0)
    }

    @Test
    fun MR_001_four_axes_and_unknown_dash() {
        val (_, calendar) = MacFixtures.composeCalendar()
        val meeting = MacFixtures.byId(calendar, "text_cal.meeting")
        val flight = MacFixtures.byId(calendar, "text_cal.flight")
        val hotel = MacFixtures.byId(calendar, "text_cal.hotel")
        val dinner = MacFixtures.byId(calendar, "text_cal.dinner")
        assertEquals("high", MacExposure.of(meeting).support)
        assertEquals("done", MacExposure.of(meeting).action)
        assertEquals("medium", MacExposure.of(flight).support)
        assertEquals("aging", MacExposure.of(flight).freshness)
        assertEquals("pending", MacExposure.of(flight).action)
        assertEquals("low", MacExposure.of(hotel).support)
        assertEquals("stale", MacExposure.of(hotel).freshness)
        assertEquals("held", MacExposure.of(hotel).status)
        assertEquals("unknown", MacExposure.of(hotel).action)
        assertEquals("contradicted", MacExposure.of(dinner).status)
        assertEquals("compensated", MacExposure.of(dinner).action)
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                MacRenderer(calendar)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Riunione 09:00 — Ada").assertIsDisplayed()
        composeRule.onNodeWithText("Volo 14:30 — FCO→LIN").assertIsDisplayed()
        composeRule.onNodeWithText("Hotel Milano").assertIsDisplayed()
        composeRule.onNodeWithText("Prenotazione ristorante").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.meeting-done").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.flight-pending").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.hotel-held").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.hotel-low").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.dinner-compensated").assertIsDisplayed()
        val calBmp = MacRaster.paint(calendar)
        save("mac-renderer-normal.png", calBmp)
        val hotelBmp = MacRaster.paint(hotel)
        assertTrue(MacRaster.inkCount(hotelBmp, MacRaster.LOW_BAR_X1) >= hotelBmp.height - 4)
        assertTrue(MacRaster.rowInk(hotelBmp, MacRaster.UNKNOWN_INSET) < hotelBmp.width / 8)

        val (_, unknownOut) = MacFixtures.interpret(
            MacFixtures.pricePresentation(),
            MacFixtures.priceProjection(),
            MacFixtures.unknownSupportFacts()
        )
        val unknown = MacFixtures.byId(unknownOut, "text_train.price")
        assertEquals("unknown", MacExposure.of(unknown).support)
        assertEquals(1f, MacExposure.textAlpha(MacExposure.of(unknown)))
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                MacRenderer(unknownOut)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_train.price-uncertain").assertIsDisplayed()
        composeRule.onNodeWithTag("text_train.price-low").assertDoesNotExist()
        val unknownBmp = MacRaster.paint(unknown)
        assertTrue(MacRaster.inkCount(unknownBmp, MacRaster.LOW_BAR_X1) < unknownBmp.height / 4)
        assertTrue(MacRaster.rowInk(unknownBmp, MacRaster.UNKNOWN_INSET) > unknownBmp.width / 8)
        val (_, lowOut) = MacFixtures.interpret(
            MacFixtures.pricePresentation(),
            MacFixtures.priceProjection(),
            MacFixtures.lowSupportFacts()
        )
        val low = MacFixtures.byId(lowOut, "text_train.price")
        assertEquals(0.50f, MacExposure.textAlpha(MacExposure.of(low)))
        assertNotEquals(MacRaster.fingerprint(MacRaster.paint(low)), MacRaster.fingerprint(unknownBmp))
    }

    @Test
    fun MR_002_presentation_hash_matches_android() {
        val (hash, _) = MacFixtures.composeCalendar()
        assertEquals(MacFixtures.ANDROID_CALENDAR_HASH, hash)
        val again = MacFixtures.composeCalendar().first
        assertEquals(hash, again)
        val (trainHash, train) = MacFixtures.interpret(
            MacFixtures.trainPresentation(),
            MacFixtures.trainProjection(),
            emptyMap()
        )
        assertTrue(trainHash.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(hash, trainHash)
        assertTrue(train.nodes.isNotEmpty())
    }

    @Test
    fun MR_003_voiceover_content_description_on_non_default() {
        val (_, calendar) = MacFixtures.composeCalendar()
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                MacRenderer(calendar)
            }
        }
        composeRule.waitForIdle()
        val ids = listOf(
            "text_cal.meeting",
            "text_cal.flight",
            "text_cal.hotel",
            "text_cal.dinner"
        )
        for (id in ids) {
            val node = MacFixtures.byId(calendar, id)
            val spoken = MacExposure.contentDescription(node)
            assertTrue(spoken.isNotEmpty(), id)
            assertTrue(spoken.contains(node.text), id)
            assertTrue(spoken.contains(node.stateDescription), id)
            composeRule.onNodeWithTag(id).assert(
                SemanticsMatcher("VoiceOver contentDescription") { semantics ->
                    if (!semantics.config.contains(SemanticsProperties.ContentDescription)) {
                        false
                    } else {
                        semantics.config[SemanticsProperties.ContentDescription]
                            .joinToString(" ")
                            .contains(node.stateDescription)
                    }
                }
            )
            composeRule.onNodeWithTag(id).assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    node.stateDescription
                )
            )
        }
        val hotelSpoken = MacExposure.contentDescription(MacFixtures.byId(calendar, "text_cal.hotel"))
        assertTrue(hotelSpoken.contains("pending review"))
        assertTrue(hotelSpoken.contains("stale"))
        val dinnerSpoken = MacExposure.contentDescription(MacFixtures.byId(calendar, "text_cal.dinner"))
        assertTrue(dinnerSpoken.contains("contradicted"))
        assertTrue(dinnerSpoken.contains("compensated"))
        save("mac-renderer-voiceover.png", MacRaster.paintSpoken(calendar))
    }

    @Test
    fun MR_004_reduce_motion_zeroes_verbs() {
        val (_, moving) = MacFixtures.interpret(
            MacFixtures.calendarPresentation(),
            MacFixtures.calendarProjection(),
            MacFixtures.calendarFacts(),
            reducedMotion = false
        )
        val (_, still) = MacFixtures.interpret(
            MacFixtures.calendarPresentation(),
            MacFixtures.calendarProjection(),
            MacFixtures.calendarFacts(),
            reducedMotion = true
        )
        assertTrue(still.reducedMotion)
        val hotel = MacFixtures.byId(still, "text_cal.hotel")
        val dinner = MacFixtures.byId(still, "text_cal.dinner")
        val flight = MacFixtures.byId(still, "text_cal.flight")
        assertEquals(0, MacExposure.motionMs(MacExposure.of(hotel), reduced = true))
        assertEquals(0, MacExposure.motionMs(MacExposure.of(dinner), reduced = true))
        assertEquals(0, MacExposure.motionMs(MacExposure.of(flight), reduced = true))
        assertEquals(2800, MacExposure.motionMs(MacExposure.of(hotel), reduced = false))
        assertEquals(240, MacExposure.motionMs(MacExposure.of(dinner), reduced = false))
        assertEquals(
            MacFixtures.byId(moving, "text_cal.hotel").stateDescription,
            hotel.stateDescription
        )
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                MacRenderer(still)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_cal.hotel-held").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.dinner-compensated").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.flight-pending").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.meeting-done").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.hotel-motion-pulse").assertDoesNotExist()
        composeRule.onNodeWithTag("text_cal.dinner-motion-fade-back").assertDoesNotExist()
        save("mac-renderer-reduce-motion.png", MacRaster.paint(still))
    }

    @Test
    fun MR_005_high_contrast_without_color() {
        val (_, calendar) = MacFixtures.composeCalendar()
        composeRule.setContent {
            Box(Modifier.size(400.dp, 800.dp)) {
                MacRenderer(calendar, highContrast = true)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_cal.hotel-held").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.flight-aging").assertIsDisplayed()
        composeRule.onNodeWithTag("text_cal.dinner-compensated").assertIsDisplayed()
        val grayscaleRaster = MacRaster.paint(calendar, highContrast = true)
        val color = MacRaster.paint(calendar, highContrast = false)
        assertNotEquals(MacRaster.fingerprint(grayscaleRaster), MacRaster.fingerprint(color))
        val flight = MacRaster.paint(MacFixtures.byId(calendar, "text_cal.flight"), highContrast = true)
        val hotel = MacRaster.paint(MacFixtures.byId(calendar, "text_cal.hotel"), highContrast = true)
        val dinner = MacRaster.paint(MacFixtures.byId(calendar, "text_cal.dinner"), highContrast = true)
        val meeting = MacRaster.paint(MacFixtures.byId(calendar, "text_cal.meeting"), highContrast = true)
        val hashes = setOf(
            MacRaster.fingerprint(flight),
            MacRaster.fingerprint(hotel),
            MacRaster.fingerprint(dinner),
            MacRaster.fingerprint(meeting)
        )
        assertEquals(4, hashes.size)
        save("mac-renderer-high-contrast.png", grayscaleRaster)
    }
}
