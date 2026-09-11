#include <metal_stdlib>
using namespace metal;
fragment float4 refractionFragment(float4 position [[position]], texture2d<float> src [[texture(0)]], sampler samp [[sampler(0)]], constant float2& iResolution [[buffer(0)]]) {
  float2 off = float2(1.25 * sin(position.y * 0.21), 0.85 * sin(position.x * 0.17));
  float2 uv = (position.xy + off) / iResolution;
  return src.sample(samp, clamp(uv, 0.0, 1.0));
}
