// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.android.compose

/**
 * Real-time foley sink. Causes are visible host/renderer signals, not a Spec.
 * Test uses [CountingFoleySink]. Device uses PCM playback behind this same interface.
 */
interface FoleySink {
    fun start(cause: String)
    fun stop()
    fun generators(): Int
    fun masterGain(): Float
}

object FoleyCause {
    const val ASCOLTO = "ascolto"
    const val MORPH = "morph"
    const val APPROVA = "approva"
    const val CRACK = "crack"
}

/**
 * In-memory sink for A1. No device playback. Same methods A6 logs on device.
 */
class CountingFoleySink : FoleySink {
    val starts = ArrayList<String>()
    private var running = 0

    override fun start(cause: String) {
        starts += cause
        running = when (cause) {
            FoleyCause.APPROVA -> 2
            FoleyCause.ASCOLTO, FoleyCause.MORPH, FoleyCause.CRACK -> 1
            else -> 0
        }
    }

    override fun stop() {
        running = 0
    }

    override fun generators(): Int = running

    override fun masterGain(): Float = Theme.voiceCeiling
}
