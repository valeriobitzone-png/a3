// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.overlay

/**
 * Percentiles over a list of millisecond samples. Used for gfxinfo dumps and
 * compositor/blur benches. Does not invent frames.
 */
data class FramePercentiles(
    val count: Int,
    val p50Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maxMs: Double
) {
    fun meetsBudget(budgetMs: Double = BUDGET_60FPS_MS): Boolean = count > 0 && p95Ms < budgetMs

    companion object {
        const val BUDGET_60FPS_MS = 16.7

        fun of(samplesMs: List<Double>): FramePercentiles {
            if (samplesMs.isEmpty()) {
                return FramePercentiles(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN)
            }
            val sorted = samplesMs.sorted()
            return FramePercentiles(
                count = sorted.size,
                p50Ms = percentile(sorted, 0.50),
                p95Ms = percentile(sorted, 0.95),
                p99Ms = percentile(sorted, 0.99),
                maxMs = sorted.last()
            )
        }

        private fun percentile(sorted: List<Double>, p: Double): Double {
            val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
            return sorted[idx]
        }
    }
}

object GfxInfoParser {
    fun parse(dump: String): FramePercentiles? {
        val named = linkedMapOf<String, Double>()
        val namedRe = Regex("""(?i)(50|90|95|99)(?:th)?\s*(?:percentile)?\s*[:=]\s*([0-9]+(?:\.[0-9]+)?)\s*ms""")
        for (match in namedRe.findAll(dump)) {
            named[match.groupValues[1]] = match.groupValues[2].toDouble()
        }
        if ("95" in named) {
            return FramePercentiles(
                count = Regex("""(?i)Total frames rendered:\s*(\d+)""").find(dump)?.groupValues?.get(1)?.toInt() ?: -1,
                p50Ms = named["50"] ?: Double.NaN,
                p95Ms = named.getValue("95"),
                p99Ms = named["99"] ?: Double.NaN,
                maxMs = named["99"] ?: named.getValue("95")
            )
        }
        val frames = framestatsMs(dump)
        if (frames.isEmpty()) return null
        return FramePercentiles.of(frames)
    }

    /** Historic dumpsys gfxinfo framestats: flags,intendedVsync,...,frameCompleted */
    fun framestatsMs(dump: String): List<Double> {
        val out = ArrayList<Double>()
        var inTable = false
        for (line in dump.lineSequence()) {
            if (line.contains("---PROFILEDATA---")) {
                inTable = true
                continue
            }
            if (!inTable) continue
            if (line.startsWith("---") || line.isBlank()) {
                if (out.isNotEmpty()) break
                continue
            }
            if (line.startsWith("Flags")) continue
            val cols = line.split(',')
            if (cols.size < 13) continue
            val intended = cols[1].trim().toLongOrNull() ?: continue
            val completed = cols.last().trim().toLongOrNull() ?: continue
            if (intended <= 0L || completed <= 0L) continue
            out += (completed - intended) / 1_000_000.0
        }
        return out
    }
}

object BlurBench {
    fun run(width: Int, height: Int, radius: Int, frames: Int, seed: Int = 7): FramePercentiles {
        val px = IntArray(width * height)
        var s = seed
        for (i in px.indices) {
            s = s * 1664525 + 1013904223
            px[i] = s
        }
        val samples = ArrayList<Double>(frames)
        repeat(frames) {
            val t0 = System.nanoTime()
            OverlayBlur.blur(px, width, height, radius)
            samples += (System.nanoTime() - t0) / 1_000_000.0
        }
        return FramePercentiles.of(samples)
    }
}
