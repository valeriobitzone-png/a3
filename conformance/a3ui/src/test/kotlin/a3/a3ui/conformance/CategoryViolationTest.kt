// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.a3ui.conformance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CategoryViolationTest {
    private val violations = File(ConformancePaths.fixturesDir(), "violations")

    @Test
    fun AC_001_contradicted_cta_enabled_fails() {
        val thrown = assertFailsWith<ConformanceReject> {
            CategoryJudge.judgeFile(File(violations, "ac-001-contradicted-cta-enabled.json"))
        }
        assertEquals("AC-001", thrown.code)
        println("PASS AC-001 ${thrown.message}")
    }

    @Test
    fun AC_002_pending_cta_without_confirmation_fails() {
        val thrown = assertFailsWith<ConformanceReject> {
            CategoryJudge.judgeFile(File(violations, "ac-002-pending-cta-enabled.json"))
        }
        assertEquals("AC-002", thrown.code)
        println("PASS AC-002 ${thrown.message}")
    }

    @Test
    fun AC_003_unknown_cta_without_confirmation_fails() {
        val thrown = assertFailsWith<ConformanceReject> {
            CategoryJudge.judgeFile(File(violations, "ac-003-unknown-cta-enabled.json"))
        }
        assertEquals("AC-003", thrown.code)
        println("PASS AC-003 ${thrown.message}")
    }

    @Test
    fun AC_004_stale_cta_without_warning_fails() {
        val thrown = assertFailsWith<ConformanceReject> {
            CategoryJudge.judgeFile(File(violations, "ac-004-stale-no-warning.json"))
        }
        assertEquals("AC-004", thrown.code)
        println("PASS AC-004 ${thrown.message}")
    }

    @Test
    fun AC_005_fact_disabled_without_reason_fails() {
        val thrown = assertFailsWith<ConformanceReject> {
            CategoryJudge.judgeFile(File(violations, "ac-005-fact-disabled-silent.json"))
        }
        assertEquals("AC-005", thrown.code)
        println("PASS AC-005 ${thrown.message}")
    }

    @Test
    fun AC_006_mark_without_state_description_fails() {
        val thrown = assertFailsWith<ConformanceReject> {
            CategoryJudge.judgeFile(File(violations, "ac-006-missing-state-description.json"))
        }
        assertEquals("AC-006", thrown.code)
        println("PASS AC-006 ${thrown.message}")
    }

    @Test
    fun AC_007_reduced_motion_hides_marks_fails() {
        val thrown = assertFailsWith<ConformanceReject> {
            CategoryJudge.judgeFile(File(violations, "ac-007-reduced-motion-hides-marks.json"))
        }
        assertEquals("AC-007", thrown.code)
        println("PASS AC-007 ${thrown.message}")
    }

    @Test
    fun AC_008_blur_off_unreadable_fails() {
        val thrown = assertFailsWith<ConformanceReject> {
            CategoryJudge.judgeFile(File(violations, "ac-008-blur-off-unreadable.json"))
        }
        assertEquals("AC-008", thrown.code)
        println("PASS AC-008 ${thrown.message}")
    }

    @Test
    fun lawful_showcase_v03_plus_fact_passes() {
        val observation = RenderObservation(
            surfaces = A3UiFixtures.lawful().map { CtaLaw.fromLawful(it) }
        )
        CtaLaw.judgeRender(observation)
        println("PASS lawful hotel/calendar/train/flight")
    }
}
