#version 150

in vec3 Position;
in vec4 Color;

uniform vec2 Size;

out vec2 uv;

void main() {
    uv = Position.xy / Size;
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
}
