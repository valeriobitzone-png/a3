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
