// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTest {
    @Test
    fun PF_001_enum_matrix_and_manual_override() {
        val high = FeatureMatrix.of(A3UiProfile.HIGH)
        assertEquals(BlurMode.FULL, high.blur)
        assertEquals(18, high.blurRadiusPx)
        assertTrue(high.particles)
        assertEquals(MotionMode.FULL, high.motion)
        assertTrue(high.dynamicColor)

        val mid = FeatureMatrix.of(A3UiProfile.MID)
        assertEquals(BlurMode.REDUCED, mid.blur)
        assertEquals(6, mid.blurRadiusPx)
        assertFalse(mid.particles)
        assertEquals(MotionMode.SIMPLIFIED, mid.motion)

        val blurOff = FeatureMatrix.of(A3UiProfile.BLUR_OFF)
        assertEquals(BlurMode.OFF, blurOff.blur)
        assertEquals(0, blurOff.blurRadiusPx)
        assertFalse(blurOff.particles)
        assertFalse(blurOff.blurEnabled)

        val particlesOff = FeatureMatrix.of(A3UiProfile.PARTICLES_OFF)
        assertEquals(high.blur, particlesOff.blur)
        assertEquals(high.blurRadiusPx, particlesOff.blurRadiusPx)
        assertEquals(high.motion, particlesOff.motion)
        assertEquals(high.dynamicColor, particlesOff.dynamicColor)
        assertFalse(particlesOff.particles)

        val store = MemoryProfileStore()
        val phone = ProfileDetect.nothingPhone3()
        val auto = ProfileSession.decide(phone, store)
        assertEquals(A3UiProfile.HIGH, auto.detected)
        assertEquals(A3UiProfile.HIGH, auto.active)
        assertEquals(ProfileSource.AUTO, auto.source)
        assertTrue(auto.reason.contains("source=auto"))
        assertNull(auto.blurMessage)

        ProfileSession.setOverride(store, A3UiProfile.MID)
        val manual = ProfileSession.decide(phone, store)
        assertEquals(A3UiProfile.MID, manual.active)
        assertEquals(A3UiProfile.HIGH, manual.detected)
        assertEquals(ProfileSource.MANUAL, manual.source)
        assertEquals("MID · manual", manual.pillText)
        assertTrue(manual.reason.contains("override=MID"))

        ProfileSession.setOverride(store, null)
        assertEquals(A3UiProfile.HIGH, ProfileSession.decide(phone, store).active)
        println("PASS PF-001 profile enum + matrix + override")
    }

    @Test
    fun PF_006_autodetect_never_silent() {
        val weak = DeviceSignals(
            name = "low-ram",
            ramMb = 2048,
            screenW = 720,
            screenH = 1280,
            gpuTier = GpuTier.SOFTWARE,
            glesVersion = 0x20000,
            hardware = "goldfish",
            platform = "emulator"
        )
        val detected = ProfileDetect.detect(weak)
        assertEquals(A3UiProfile.BLUR_OFF, detected)
        val decision = ProfileDetect.resolve(detected, null)
        assertEquals(ProfileSource.AUTO, decision.source)
        assertNotNull(decision.blurMessage)
        assertTrue(decision.blurMessage!!.contains(OverlayPolicy.BLUR_UNAVAILABLE))
        assertTrue(decision.reason.contains("source=auto"))
        assertTrue(decision.pillText.contains("BLUR_OFF"))

        val midRam = weak.copy(name = "mid-flags", ramMb = 4096, gpuTier = GpuTier.HIGH, glesVersion = 0x30000, hardware = "qcom", platform = "msmnile")
        assertEquals(A3UiProfile.MID, ProfileDetect.detect(midRam))
        println("PASS PF-006 auto-detect visible")
    }

    @Test
    fun PF_008_particles_off_keeps_high_chrome() {
        val high = FeatureMatrix.of(A3UiProfile.HIGH)
        val off = FeatureMatrix.of(A3UiProfile.PARTICLES_OFF)
        assertFalse(off.particles)
        assertEquals(high.blur, off.blur)
        assertEquals(high.blurRadiusPx, off.blurRadiusPx)
        assertEquals(high.motion, off.motion)
        assertEquals(high.dynamicColor, off.dynamicColor)
        assertEquals(high.noise, off.noise)
        println("PASS PF-008 PARTICLES_OFF")
    }

    @Test
    fun PF_009_frozen_trees_empty() {
        val root = java.io.File("../..")
        fun diff(vararg paths: String): String {
            val proc = ProcessBuilder("git", "diff", "--stat", "--", *paths)
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            check(proc.waitFor() == 0)
            return out
        }
        val frozen = diff(
            "core/",
            "broker/",
            "agent/",
            "adapters/",
            "conformance/",
            "spec/",
            "a3ui/"
        )
        assertTrue(frozen.isBlank(), frozen)
        println("PASS PF-009 freeze")
    }

    @Test
    fun PF_gfxinfo_parser_reads_percentile_lines() {
        val dump = """
            Total frames rendered: 120
            Janky frames: 2 (1.67%)
            50th percentile: 8.20ms
            90th percentile: 12.10ms
            95th percentile: 14.40ms
            99th percentile: 18.00ms
        """.trimIndent()
        val stats = GfxInfoParser.parse(dump)
        assertNotNull(stats)
        assertEquals(120, stats.count)
        assertEquals(14.40, stats.p95Ms)
        assertTrue(stats.meetsBudget())
    }

    @Test
    fun PF_blur_off_is_identity() {
        val src = intArrayOf(1, 2, 3, 4)
        assertTrue(OverlayBlur.blur(src, 2, 2, 0).contentEquals(src))
    }
}
