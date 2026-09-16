// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.renderers.mac.compose

import kotlin.math.sin

/**
 * Subtle glass refraction and grain. Mix stays well below fillOpacity so the
 * epistemic overlay stamped afterwards stays exact. Liquid distortion is deferred.
 */
internal object GlassOptics {
    const val REFRACT_X = 1.25f
    const val REFRACT_Y = 0.85f
    const val FREQ_Y = 0.21f
    const val FREQ_X = 0.17f
    const val NOISE_STRENGTH = 0.04f
    const val LIQUID = "liquid distortion deferred"

    fun offsetX(x: Int, y: Int): Float = REFRACT_X * sin(y * FREQ_Y)

    fun offsetY(x: Int, y: Int): Float = REFRACT_Y * sin(x * FREQ_X)

    fun noiseAt(x: Int, y: Int): Float {
        var n = x * 374761 + y * 668265 + 1013904223
        n = n xor (n shl 13)
        val u = ((n ushr 8) and 255) / 255f
        return (u - 0.5f) * 2f * NOISE_STRENGTH
    }

    const val GLSL_REFRACT = """#version 320 es
precision highp float;
uniform sampler2D src;
uniform vec2 iResolution;
out vec4 fragColor;
void main() {
  vec2 fragCoord = gl_FragCoord.xy;
  vec2 off = vec2(1.25 * sin(fragCoord.y * 0.21), 0.85 * sin(fragCoord.x * 0.17));
  vec2 uv = (fragCoord + off) / iResolution;
  fragColor = texture(src, clamp(uv, 0.0, 1.0));
}
"""

    const val AGSL_REFRACT = """
uniform shader src;
uniform float2 iResolution;

half4 main(float2 fragCoord) {
  float2 off = float2(1.25 * sin(fragCoord.y * 0.21), 0.85 * sin(fragCoord.x * 0.17));
  return src.eval(clamp(fragCoord + off, float2(0.0), iResolution - float2(1.0)));
}
"""

    const val METAL_REFRACT = """
#include <metal_stdlib>
using namespace metal;
fragment float4 refractionFragment(float4 position [[position]], texture2d<float> src [[texture(0)]], sampler samp [[sampler(0)]], constant float2& iResolution [[buffer(0)]]) {
  float2 off = float2(1.25 * sin(position.y * 0.21), 0.85 * sin(position.x * 0.17));
  float2 uv = (position.xy + off) / iResolution;
  return src.sample(samp, clamp(uv, 0.0, 1.0));
}
"""

    const val GLSL_NOISE = """#version 320 es
precision highp float;
uniform sampler2D src;
uniform vec2 iResolution;
out vec4 fragColor;
void main() {
  vec2 fragCoord = gl_FragCoord.xy;
  vec4 base = texture(src, fragCoord / iResolution);
  float n = fract(sin(dot(fragCoord, vec2(12.9898, 78.233))) * 43758.5453);
  float g = (n - 0.5) * 2.0 * 0.04;
  fragColor = vec4(clamp(base.rgb + g, 0.0, 1.0), base.a);
}
"""

    const val AGSL_NOISE = """
uniform shader src;
uniform float2 iResolution;

half4 main(float2 fragCoord) {
  half4 base = src.eval(fragCoord);
  float n = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
  float g = (n - 0.5) * 2.0 * 0.04;
  return half4(clamp(base.rgb + g, 0.0, 1.0), base.a);
}
"""

    const val METAL_NOISE = """
#include <metal_stdlib>
using namespace metal;
fragment float4 noiseFragment(float4 position [[position]], texture2d<float> src [[texture(0)]], sampler samp [[sampler(0)]], constant float2& iResolution [[buffer(0)]]) {
  float4 base = src.sample(samp, position.xy / iResolution);
  float n = fract(sin(dot(position.xy, float2(12.9898, 78.233))) * 43758.5453);
  float g = (n - 0.5) * 2.0 * 0.04;
  return float4(clamp(base.rgb + g, 0.0, 1.0), base.a);
}
"""
}
