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
