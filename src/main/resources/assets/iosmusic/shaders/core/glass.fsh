#version 150

in vec4 vertexColor;
in vec2 screenPos;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform vec4 Rect;
uniform float Radius;
uniform vec2 ScreenSize;
uniform float GuiScale;
uniform vec2 BlurTexel;
uniform float FrostOnly;

out vec4 fragColor;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 center = Rect.xy + Rect.zw * 0.5;
    vec2 halfSize = max(Rect.zw * 0.5, vec2(0.0));
    float radius = clamp(Radius, 0.0, min(halfSize.x, halfSize.y));
    vec2 p = screenPos - center;
    float d = sdRoundBox(p, halfSize, radius);
    float aa = max(fwidth(d), 0.0001);
    float coverage = 1.0 - smoothstep(-aa, aa, d);
    if (coverage <= 0.0005) {
        discard;
    }

    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec2 dir = normalize(p + vec2(0.0001, 0.0001));
    float edge = smoothstep(-min(halfSize.x, halfSize.y) * 0.28, 0.0, d);
    vec2 lens = p * GuiScale * 0.044 / ScreenSize;
    vec2 offset = lens + dir * edge * 0.016;

    // Medium-resolution source with a normalized 5x5 Gaussian kernel. Unrolled to avoid driver
    // differences in dynamic local-array indexing.
    vec2 centerUv = clamp(uv + offset, 0.0, 1.0);
    vec2 t = BlurTexel;
    vec3 soft = vec3(0.0);
    soft += texture(Sampler0, clamp(centerUv + vec2(-2.0, -2.0) * t, 0.0, 1.0)).rgb * 1.0;
    soft += texture(Sampler0, clamp(centerUv + vec2(-1.0, -2.0) * t, 0.0, 1.0)).rgb * 4.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 0.0, -2.0) * t, 0.0, 1.0)).rgb * 6.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 1.0, -2.0) * t, 0.0, 1.0)).rgb * 4.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 2.0, -2.0) * t, 0.0, 1.0)).rgb * 1.0;
    soft += texture(Sampler0, clamp(centerUv + vec2(-2.0, -1.0) * t, 0.0, 1.0)).rgb * 4.0;
    soft += texture(Sampler0, clamp(centerUv + vec2(-1.0, -1.0) * t, 0.0, 1.0)).rgb * 16.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 0.0, -1.0) * t, 0.0, 1.0)).rgb * 24.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 1.0, -1.0) * t, 0.0, 1.0)).rgb * 16.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 2.0, -1.0) * t, 0.0, 1.0)).rgb * 4.0;
    soft += texture(Sampler0, clamp(centerUv + vec2(-2.0,  0.0) * t, 0.0, 1.0)).rgb * 6.0;
    soft += texture(Sampler0, clamp(centerUv + vec2(-1.0,  0.0) * t, 0.0, 1.0)).rgb * 24.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 0.0,  0.0) * t, 0.0, 1.0)).rgb * 36.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 1.0,  0.0) * t, 0.0, 1.0)).rgb * 24.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 2.0,  0.0) * t, 0.0, 1.0)).rgb * 6.0;
    soft += texture(Sampler0, clamp(centerUv + vec2(-2.0,  1.0) * t, 0.0, 1.0)).rgb * 4.0;
    soft += texture(Sampler0, clamp(centerUv + vec2(-1.0,  1.0) * t, 0.0, 1.0)).rgb * 16.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 0.0,  1.0) * t, 0.0, 1.0)).rgb * 24.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 1.0,  1.0) * t, 0.0, 1.0)).rgb * 16.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 2.0,  1.0) * t, 0.0, 1.0)).rgb * 4.0;
    soft += texture(Sampler0, clamp(centerUv + vec2(-2.0,  2.0) * t, 0.0, 1.0)).rgb * 1.0;
    soft += texture(Sampler0, clamp(centerUv + vec2(-1.0,  2.0) * t, 0.0, 1.0)).rgb * 4.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 0.0,  2.0) * t, 0.0, 1.0)).rgb * 6.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 1.0,  2.0) * t, 0.0, 1.0)).rgb * 4.0;
    soft += texture(Sampler0, clamp(centerUv + vec2( 2.0,  2.0) * t, 0.0, 1.0)).rgb * 1.0;
    soft /= 256.0;

    float rimBand = 1.0 - smoothstep(0.0, 14.0, -d);
    vec3 background;
    if (FrostOnly > 0.5) {
        background = soft;
    } else {
        vec2 rimOffset = lens * 1.35 + dir * edge * 0.034;
        vec3 rim = vec3(
            texture(Sampler1, clamp(uv + rimOffset * 1.32, 0.0, 1.0)).r,
            texture(Sampler1, clamp(uv + rimOffset, 0.0, 1.0)).g,
            texture(Sampler1, clamp(uv + rimOffset * 0.68, 0.0, 1.0)).b
        );
        background = mix(soft, rim, rimBand * 0.9);
    }
    float luma = dot(background, vec3(0.299, 0.587, 0.114));
    background = mix(vec3(luma), background, 1.15);

    vec4 tint = vertexColor * ColorModulator;
    vec3 color = mix(background, tint.rgb, tint.a);

    // The material is defined by its blur and refracting edge, not by painted gloss. A very slight
    // darkening at the inner edge gives the pane thickness without introducing a white rim.
    float innerShade = smoothstep(-10.0, 0.0, d);
    color *= 1.0 - 0.06 * innerShade;

    fragColor = vec4(color, coverage);
}
