#version 320 es
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
