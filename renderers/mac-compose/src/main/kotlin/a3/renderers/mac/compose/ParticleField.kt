// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import kotlin.math.cos
import kotlin.math.sin

/**
 * Confirm/unlock sparks. No particles without an explicit emit trigger.
 */
internal object ParticleField {
    const val COUNT = 12
    const val TAU = 6.2831853f

    data class Spark(val x: Float, val y: Float, val vx: Float, val vy: Float, val life: Float)

    fun emit(count: Int = COUNT, seed: Int = 7, cx: Float = 0.5f, cy: Float = 0.5f): List<Spark> {
        var s = seed
        val out = ArrayList<Spark>(count)
        var i = 0
        while (i < count) {
            s = s * 1664525 + 1013904223
            val ang = ((s ushr 16) and 0xFFFF) / 65536f * TAU
            val spd = 0.08f + ((s ushr 8) and 255) / 255f * 0.12f
            out += Spark(cx, cy, cos(ang) * spd, sin(ang) * spd, 1f)
            i++
        }
        return out
    }

    fun step(sparks: List<Spark>, t: Float): List<Spark> {
        val u = t.coerceIn(0f, 1f)
        return sparks.map {
            Spark(it.x + it.vx * u, it.y + it.vy * u, it.vx, it.vy, (1f - u).coerceAtLeast(0f))
        }
    }

    fun frame(trigger: Boolean, t: Float, seed: Int = 7): List<Spark> {
        if (!trigger) return emptyList()
        return step(emit(COUNT, seed), t)
    }

    const val GLSL = """#version 320 es
precision highp float;
uniform vec2 iResolution;
uniform float iTime;
uniform float emit;
out vec4 fragColor;
void main() {
  if (emit < 0.5) { fragColor = vec4(0.0); return; }
  vec2 uv = gl_FragCoord.xy / iResolution;
  float d = 1.0;
  for (int i = 0; i < 12; i++) {
    float a = float(i) * 0.5235988;
    vec2 p = vec2(0.5) + vec2(cos(a), sin(a)) * (0.08 + 0.12 * fract(float(i) * 0.17)) * iTime;
    d = min(d, length(uv - p));
  }
  float spark = smoothstep(0.04, 0.0, d) * (1.0 - iTime);
  fragColor = vec4(vec3(spark), spark);
}
"""

    const val AGSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float emit;

half4 main(float2 fragCoord) {
  if (emit < 0.5) return half4(0.0);
  float2 uv = fragCoord / iResolution;
  float d = 1.0;
  for (int i = 0; i < 12; i++) {
    float a = float(i) * 0.5235988;
    float2 p = float2(0.5) + float2(cos(a), sin(a)) * (0.08 + 0.12 * fract(float(i) * 0.17)) * iTime;
    d = min(d, length(uv - p));
  }
  float spark = smoothstep(0.04, 0.0, d) * (1.0 - iTime);
  return half4(half3(spark), spark);
}
"""

    const val METAL = """
#include <metal_stdlib>
using namespace metal;
struct Uniforms { float2 iResolution; float iTime; float emit; };
fragment float4 particleFragment(float4 position [[position]], constant Uniforms& u [[buffer(0)]]) {
  if (u.emit < 0.5) return float4(0.0);
  float2 uv = position.xy / u.iResolution;
  float d = 1.0;
  for (int i = 0; i < 12; i++) {
    float a = float(i) * 0.5235988;
    float2 p = float2(0.5) + float2(cos(a), sin(a)) * (0.08 + 0.12 * fract(float(i) * 0.17)) * u.iTime;
    d = min(d, length(uv - p));
  }
  float spark = smoothstep(0.04, 0.0, d) * (1.0 - u.iTime);
  return float4(float3(spark), spark);
}
"""
}
