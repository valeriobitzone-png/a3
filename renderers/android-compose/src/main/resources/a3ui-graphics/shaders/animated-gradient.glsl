#version 320 es
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
