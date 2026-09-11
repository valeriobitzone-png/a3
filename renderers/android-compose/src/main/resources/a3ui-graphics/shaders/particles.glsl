#version 320 es
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
