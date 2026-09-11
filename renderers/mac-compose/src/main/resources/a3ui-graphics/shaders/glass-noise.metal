#include <metal_stdlib>
using namespace metal;
fragment float4 noiseFragment(float4 position [[position]], texture2d<float> src [[texture(0)]], sampler samp [[sampler(0)]], constant float2& iResolution [[buffer(0)]]) {
  float4 base = src.sample(samp, position.xy / iResolution);
  float n = fract(sin(dot(position.xy, float2(12.9898, 78.233))) * 43758.5453);
  float g = (n - 0.5) * 2.0 * 0.04;
  return float4(clamp(base.rgb + g, 0.0, 1.0), base.a);
}
