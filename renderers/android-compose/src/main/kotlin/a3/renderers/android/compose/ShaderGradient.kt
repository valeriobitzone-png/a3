package a3.renderers.android.compose

import kotlin.math.sin

/**
 * Flowing gradient: one formula, three programs (GLSL / AGSL / Metal) plus CPU eval.
 * Palette from [DynamicPalette] when extracted, else token ink/paper/amber.
 * mixAmt is glass.fillOpacity so the wash never paints over epistemic ink.
 */
internal object ShaderGradient {
    const val SPEED = 0.18f
    const val AMPLITUDE = 0.35f
    const val TAU = 6.2831853f

    data class Stop(val r: Float, val g: Float, val b: Float)

    fun mixAmt(): Float = GraphicsTokens.snapshot.fillOpacity * 0.45f

    fun speed(): Float = SPEED

    fun amplitude(): Float = AMPLITUDE

    fun palette(tonal: DynamicPalette.Tonal? = null): Triple<Stop, Stop, Stop> {
        val t = tonal ?: DynamicPalette.fallback()
        return Triple(stop(t.paper), stop(t.secondary), stop(t.primary))
    }

    fun shade(u: Float, v: Float, timeSec: Float, pal: Triple<Stop, Stop, Stop> = palette()): Int {
        val t = timeSec * SPEED
        val n = 0.5f + 0.5f * sin(TAU * (u * 0.70f + v * 0.40f + t))
        val m = 0.5f + 0.5f * sin(TAU * (u * 0.30f - v * 0.80f + t * 0.73f) + AMPLITUDE)
        val blob = mix(pal.second, pal.third, n)
        val col = mix(pal.first, blob, m * mixAmt())
        return pack(col)
    }

    fun qualitative(width: Int, height: Int, timeSec: Float, pal: Triple<Stop, Stop, Stop> = palette()): String {
        val cells = 8
        val out = StringBuilder(cells * cells)
        var cy = 0
        while (cy < cells) {
            var cx = 0
            while (cx < cells) {
                val u = (cx + 0.5f) / cells
                val v = (cy + 0.5f) / cells
                val p = shade(u, v, timeSec, pal)
                val luma = ((p shr 16) and 0xFF) * 3 + ((p shr 8) and 0xFF) * 6 + (p and 0xFF)
                out.append("0123456789abcdef"[(luma / 160).coerceIn(0, 15)])
                cx++
            }
            cy++
        }
        return out.toString()
    }

    private fun stop(c: DynamicPalette.Rgb) = Stop(c.r / 255f, c.g / 255f, c.b / 255f)

    private fun mix(a: Stop, b: Stop, t: Float): Stop {
        val u = t.coerceIn(0f, 1f)
        return Stop(a.r + (b.r - a.r) * u, a.g + (b.g - a.g) * u, a.b + (b.b - a.b) * u)
    }

    private fun pack(c: Stop): Int {
        val r = (c.r * 255f).toInt().coerceIn(0, 255)
        val g = (c.g * 255f).toInt().coerceIn(0, 255)
        val b = (c.b * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    const val GLSL = """#version 320 es
precision highp float;
uniform vec2 iResolution;
uniform float iTime;
uniform float speed;
uniform float amplitude;
uniform vec3 cPaper;
uniform vec3 cAmber;
uniform vec3 cInk;
uniform float mixAmt;
out vec4 fragColor;
void main() {
  vec2 uv = gl_FragCoord.xy / iResolution;
  float t = iTime * speed;
  float n = 0.5 + 0.5 * sin(6.2831853 * (uv.x * 0.70 + uv.y * 0.40 + t));
  float m = 0.5 + 0.5 * sin(6.2831853 * (uv.x * 0.30 - uv.y * 0.80 + t * 0.73) + amplitude);
  vec3 blob = mix(cAmber, cInk, n);
  fragColor = vec4(mix(cPaper, blob, m * mixAmt), 1.0);
}
"""

    const val AGSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float speed;
uniform float amplitude;
uniform float3 cPaper;
uniform float3 cAmber;
uniform float3 cInk;
uniform float mixAmt;

half4 main(float2 fragCoord) {
  float2 uv = fragCoord / iResolution;
  float t = iTime * speed;
  float n = 0.5 + 0.5 * sin(6.2831853 * (uv.x * 0.70 + uv.y * 0.40 + t));
  float m = 0.5 + 0.5 * sin(6.2831853 * (uv.x * 0.30 - uv.y * 0.80 + t * 0.73) + amplitude);
  float3 blob = mix(cAmber, cInk, n);
  return half4(mix(cPaper, blob, m * mixAmt), 1.0);
}
"""

    const val METAL = """
#include <metal_stdlib>
using namespace metal;
struct Uniforms {
  float2 iResolution;
  float iTime;
  float speed;
  float amplitude;
  float3 cPaper;
  float3 cAmber;
  float3 cInk;
  float mixAmt;
};
fragment float4 gradientFragment(float4 position [[position]], constant Uniforms& u [[buffer(0)]]) {
  float2 uv = position.xy / u.iResolution;
  float t = u.iTime * u.speed;
  float n = 0.5 + 0.5 * sin(6.2831853 * (uv.x * 0.70 + uv.y * 0.40 + t));
  float m = 0.5 + 0.5 * sin(6.2831853 * (uv.x * 0.30 - uv.y * 0.80 + t * 0.73) + u.amplitude);
  float3 blob = mix(u.cAmber, u.cInk, n);
  return float4(mix(u.cPaper, blob, m * u.mixAmt), 1.0);
}
"""
}
