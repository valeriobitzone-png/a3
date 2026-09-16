// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(0xFFFFFFFF.toInt())
        val reduced = intent.getBooleanExtra(
            EXTRA_REDUCED_MOTION,
            DeviceAccessibility.reducedMotion(this)
        )
        val highContrast = intent.getBooleanExtra(
            EXTRA_HIGH_CONTRAST,
            DeviceAccessibility.highContrast(this)
        )
        val vm = A3HostViewModel.uncertain(reducedMotion = reduced)
        setContent {
            val ui by vm.ui.collectAsState()
            A3Screen(
                output = ui.output,
                onAction = vm::onAction,
                trustVisible = ui.trustHold,
                rollbackVisible = ui.rollbackVisible,
                onApproveTrust = vm::approveTrust,
                stage = ui.stage,
                highContrast = highContrast
            )
        }
    }

    companion object {
        const val EXTRA_REDUCED_MOTION = "a3_reduced_motion"
        const val EXTRA_HIGH_CONTRAST = "a3_high_contrast"
    }
}
