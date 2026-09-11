#version 320 es
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
