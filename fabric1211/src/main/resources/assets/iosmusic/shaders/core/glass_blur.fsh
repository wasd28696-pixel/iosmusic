#version 150

in vec2 uv;

uniform sampler2D Sampler0;
uniform vec2 TexelSize;
uniform vec2 Direction;

out vec4 fragColor;

void main() {
    // 9-tap Gaussian (sigma ≈ 2), run horizontally then vertically.
    vec2 step = TexelSize * Direction;
    vec4 color = texture(Sampler0, uv) * 0.2270270270;
    color += texture(Sampler0, uv + step * 1.0) * 0.1945945946;
    color += texture(Sampler0, uv - step * 1.0) * 0.1945945946;
    color += texture(Sampler0, uv + step * 2.0) * 0.1216216216;
    color += texture(Sampler0, uv - step * 2.0) * 0.1216216216;
    color += texture(Sampler0, uv + step * 3.0) * 0.0540540541;
    color += texture(Sampler0, uv - step * 3.0) * 0.0540540541;
    color += texture(Sampler0, uv + step * 4.0) * 0.0162162162;
    color += texture(Sampler0, uv - step * 4.0) * 0.0162162162;
    fragColor = vec4(color.rgb, 1.0);
}
