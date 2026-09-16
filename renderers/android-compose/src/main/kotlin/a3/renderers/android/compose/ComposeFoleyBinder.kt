// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import a3.renderers.android.core.model.RendererContext
import a3.renderers.android.core.model.SharedElementPlan
import kotlinx.coroutines.delay

@Composable
fun ComposeFoleyBinder(
    plan: SharedElementPlan,
    durationHint: Long,
    sink: FoleySink,
    content: @Composable () -> Unit
) {
    val stage = LocalRendererStage.current
    val crack = LocalCrack.current
    if (sink is AudioTrackFoleySink) {
        sink.durationHintMs = durationHint
    }
    LaunchedEffect(stage, crack) {
        when {
            crack -> {
                sink.start(FoleyCause.CRACK)
                delay(Theme.crackMs.toLong())
                sink.stop()
            }
            stage == RendererContext.STAGE_LAVORO || stage == RendererContext.STAGE_PRONTO -> {
                sink.stop()
            }
            stage == RendererContext.STAGE_ASCOLTO -> {
                sink.start(FoleyCause.ASCOLTO)
                delay(Theme.airBedMs)
                sink.stop()
            }
            stage == RendererContext.STAGE_APPROVA -> {
                sink.start(FoleyCause.APPROVA)
                delay(Theme.hapticGapMs * 2)
                sink.stop()
            }
        }
    }
    var firstMorph by remember { mutableStateOf(true) }
    LaunchedEffect(plan.to, plan.shared) {
        if (firstMorph) {
            firstMorph = false
            return@LaunchedEffect
        }
        if (crack) return@LaunchedEffect
        if (stage == RendererContext.STAGE_LAVORO || stage == RendererContext.STAGE_PRONTO) {
            return@LaunchedEffect
        }
        sink.start(FoleyCause.MORPH)
        delay(durationHint)
        if (stage != RendererContext.STAGE_APPROVA && !crack) {
            sink.stop()
        }
    }
    DisposableEffect(sink) {
        onDispose { sink.stop() }
    }
    content()
}
