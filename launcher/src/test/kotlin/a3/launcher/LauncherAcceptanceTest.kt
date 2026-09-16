// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.launcher

import a3.a3ui.engine.DeterministicA3UICompiler
import a3.core.model.TrustTier
import a3.core.time.SequentialIdGenerator
import a3.projection.model.Density
import a3.renderers.android.core.interp.A3UIInterpreter
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LauncherAcceptanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun L9_first_composition_uses_output_not_empty_stack() {
        val vm = A3HostViewModel.train()
        composeRule.setContent {
            val ui by vm.ui.collectAsState()
            A3Screen(ui.output, vm::onAction, ui.trustHold, ui.rollbackVisible, vm::approveTrust, ui.stage)
        }
        composeRule.onNodeWithTag("a3-host").assertIsDisplayed()
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
        composeRule.onNodeWithTag("a3-empty").assertDoesNotExist()
        composeRule.onNodeWithTag("field_train.passenger").assertTextContains("Ada")
        composeRule.onNodeWithTag("text_train.price").assertTextContains("12.40")
    }

    @Test
    fun L9_null_output_shows_empty_stack_not_blank_host() {
        composeRule.setContent {
            A3Screen(
                output = null,
                onAction = {},
                trustVisible = false,
                rollbackVisible = false,
                onApproveTrust = {}
            )
        }
        composeRule.onNodeWithTag("a3-host").assertIsDisplayed()
        composeRule.onNodeWithTag("a3-empty").assertIsDisplayed()
        composeRule.onNodeWithTag("a3-catalog").assertDoesNotExist()
    }

    @Test
    fun L2_train_path_02_has_list_departure_and_clickable_confirm() {
        val vm = A3HostViewModel.train()
        vm.start()
        assertEquals("inferred", vm.ui.value.intent!!.source)
        assertTrue(vm.ui.value.intent!!.confidence < 1.0)
        val surface = vm.ui.value.surface!!
        val empty = vm.interpret01(surface)
        val filled = vm.interpret02(surface)
        assertTrue(empty.nodes.isEmpty())
        assertTrue(filled.nodes.any { hasRole(it, "list") })
        val catalog = File(
            "../renderers/android-compose/src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt"
        ).readText()
        assertTrue(catalog.contains("Box"))
        assertTrue(catalog.contains("clickable"))
        assertFalse(catalog.contains("androidx.compose.material.Button("))

        composeRule.setContent {
            A3Screen(
                output = filled,
                onAction = {},
                trustVisible = false,
                rollbackVisible = false,
                onApproveTrust = {}
            )
        }
        composeRule.onNodeWithTag("timetable").assertIsDisplayed()
        composeRule.onNodeWithTag("item_train.slot.a").assertIsDisplayed()
        composeRule.onNodeWithTag("text_train.departure").assertIsDisplayed()
        composeRule.onNodeWithTag("text_train.departure").assertTextContains("08:45")
        composeRule.onNodeWithTag("action_ticket.owned").assertIsDisplayed()
        composeRule.onNodeWithTag("action_ticket.owned").assert(hasClickAction())
    }

    @Test
    fun L3_two_outputs_carry_morph_ids_and_interpreted_stiffness() {
        val compiler = DeterministicA3UICompiler(DemoFixtures.now, SequentialIdGenerator())
        val interpreter = A3UIInterpreter()
        val presentationA = DemoFixtures.presentation().copy(id = "ps_compact")
        val presentationB = DemoFixtures.presentation().copy(id = "ps_spacious")
        val compact = compiler.compile(
            DemoFixtures.projection(Density.COMPACT).copy(presentationId = "ps_compact"),
            presentationA
        )
        val spacious = compiler.compile(
            DemoFixtures.projection(Density.SPACIOUS).copy(presentationId = "ps_spacious"),
            presentationB
        )
        val a = interpreter.interpret(compact, presentationA, DemoFixtures.rendererContext())
        val b = interpreter.interpret(spacious, presentationB, DemoFixtures.rendererContext())
        assertEquals("shared-element", a.sharedElements.mode)
        assertEquals("shared-element", b.sharedElements.mode)
        assertEquals("ps_compact", a.sharedElements.to)
        assertEquals("ps_spacious", b.sharedElements.to)
        assertNotEquals(a.spring.stiffness, b.spring.stiffness)
        assertEquals(compact.motion.stiffness, a.spring.stiffness)
        assertEquals(spacious.motion.stiffness, b.spring.stiffness)
        val renderer = File(
            "../renderers/android-compose/src/main/kotlin/a3/renderers/android/compose/ComposeRenderer.kt"
        ).readText()
        assertTrue(renderer.contains("ComposeMorphApplier"))
        assertTrue(renderer.contains("ComposeMotionApplier"))
        composeRule.setContent { A3Screen(a, {}, false, false, {}) }
        composeRule.onNodeWithTag("a3-catalog").assertIsDisplayed()
    }

    @Test
    fun L5_irreversible_shows_trust_dialog_allow_does_not() {
        val train = A3HostViewModel.train()
        train.start()
        assertTrue(train.ui.value.plan!!.steps.any { it.trustTier == TrustTier.IRREVERSIBLE })
        composeRule.setContent {
            val ui by train.ui.collectAsState()
            A3Screen(ui.output!!, train::onAction, ui.trustHold, ui.rollbackVisible, train::approveTrust, ui.stage)
        }
        composeRule.onNodeWithTag("trust-dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("action_ticket.owned").performClick()
        composeRule.waitForIdle()
        assertTrue(train.ui.value.trustHold)
        composeRule.onNodeWithTag("trust-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("trust-approve").performClick()
        composeRule.waitForIdle()
        assertFalse(train.ui.value.trustHold)
        assertTrue(train.ui.value.lastExecution!!.committed)

        val reversible = A3HostViewModel.reversible()
        reversible.start()
        assertTrue(reversible.ui.value.plan!!.steps.none { it.trustTier == TrustTier.IRREVERSIBLE })
        reversible.onAction("confirm")
        assertFalse(reversible.ui.value.trustHold)
        assertTrue(reversible.ui.value.lastExecution!!.committed)
    }

    @Test
    fun L6_mismatch_shows_rollback_overlay() {
        val bad = A3HostViewModel.train(executor = DemoFixtures.mismatchExecutor())
        bad.start()
        bad.onAction("confirm")
        bad.approveTrust()
        assertTrue(bad.ui.value.lastExecution!!.rolledBack)
        composeRule.setContent {
            val ui = bad.ui.value
            A3Screen(ui.output!!, {}, ui.trustHold, ui.rollbackVisible, {}, ui.stage)
        }
        composeRule.onNodeWithTag("rollback-overlay").assertIsDisplayed()
    }

    @Test
    fun L6_match_does_not_show_rollback_overlay() {
        val good = A3HostViewModel.train()
        good.start()
        good.onAction("confirm")
        good.approveTrust()
        assertTrue(good.ui.value.lastExecution!!.committed)
        assertFalse(good.ui.value.rollbackVisible)
        composeRule.setContent {
            val ui = good.ui.value
            A3Screen(ui.output!!, {}, ui.trustHold, ui.rollbackVisible, {}, ui.stage)
        }
        composeRule.onNodeWithTag("rollback-overlay").assertDoesNotExist()
    }

    @Test
    fun L7_catalog_semantics_are_foundation_roles() {
        val vm = A3HostViewModel.train()
        vm.start()
        composeRule.setContent {
            A3Screen(vm.ui.value.output!!, {}, trustVisible = true, rollbackVisible = false, onApproveTrust = {}, stage = vm.ui.value.stage)
        }
        composeRule.onNodeWithTag("timetable").assert(
            SemanticsMatcher("collection") { node ->
                node.config.contains(SemanticsProperties.CollectionInfo)
            }
        )
        composeRule.onNodeWithTag("item_train.slot.a").assertIsDisplayed()
        composeRule.onNodeWithTag("action_ticket.owned").assert(hasClickAction())
        composeRule.onNodeWithTag("field_train.passenger").assert(
            SemanticsMatcher("editable") { node ->
                node.config.contains(SemanticsProperties.EditableText)
            }
        )
        composeRule.onNodeWithTag("text_train.departure").assertTextContains("08:45")
        composeRule.onNodeWithTag("root").assert(
            SemanticsMatcher("stack is not a collection") { node ->
                !node.config.contains(SemanticsProperties.CollectionInfo)
            }
        )
        composeRule.onNodeWithTag("trust-approve").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        )
        val catalog = File(
            "../renderers/android-compose/src/main/kotlin/a3/renderers/android/compose/ComposeCatalog.kt"
        ).readText()
        assertTrue(catalog.contains("BasicTextField"))
        assertFalse(catalog.contains("androidx.compose.material.Button("))
    }

    private fun hasRole(node: a3.renderers.android.core.model.RenderedNode, role: String): Boolean {
        if (node.role == role) return true
        return node.children.any { hasRole(it, role) }
    }
}
