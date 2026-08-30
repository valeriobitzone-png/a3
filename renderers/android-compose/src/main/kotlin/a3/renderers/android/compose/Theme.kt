package a3.renderers.android.compose

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import a3.renderers.android.core.model.RendererContext

internal val LocalRendererStage = staticCompositionLocalOf { RendererContext.STAGE_PRONTO }
internal val LocalCrack = staticCompositionLocalOf { false }

/**
 * Grammar numbers for occupancy, type, motion, and material temperature.
 * Not Material Design. Not a Spec.
 */
object Theme {
    val space = 16.dp
    val type = TextStyle(fontSize = 18.sp, color = Color.Black)
    val actionMin = 64.dp
    val listWeight = 2f
    val slotWeight = 1f
    val holdScale = 1.03f
    val crackMs = 80
    val crackPeak = 0.97f
    val listenFrom = 0.98f
    val hapticGapMs = 40L

    fun material(stage: String): ColorMatrix {
        val matrix = ColorMatrix()
        when (stage) {
            RendererContext.STAGE_ASCOLTO -> {
                matrix.setToSaturation(0.65f)
                val exposure = ColorMatrix()
                exposure.setToScale(0.88f, 0.90f, 0.96f, 1f)
                matrix.timesAssign(exposure)
            }
            RendererContext.STAGE_LAVORO -> {
                matrix.setToScale(1.08f, 1.06f, 1.02f, 1f)
            }
            RendererContext.STAGE_PRONTO -> { }
            RendererContext.STAGE_APPROVA -> {
                matrix.setToScale(1.12f, 1.00f, 0.86f, 1f)
            }
        }
        return matrix
    }
}
