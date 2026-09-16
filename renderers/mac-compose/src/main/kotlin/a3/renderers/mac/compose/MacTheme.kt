// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalReducedMotion = staticCompositionLocalOf { false }
internal val LocalHighContrast = staticCompositionLocalOf { false }
internal val LocalDensityHint = staticCompositionLocalOf { "comfortable" }

object MacTheme {
    val space = 16.dp
    val type = TextStyle(fontSize = 18.sp, color = Color.Black)
    val actionMin = 64.dp
    val listWeight = 2f
    val slotWeight = 1f
    val heldPulseMs = 2800
    val unknownShimmerMs = 800
    val contradictedCrackMs = 240
    val staleFadeMs = 180
    val compensatedFadeMs = 220
}
