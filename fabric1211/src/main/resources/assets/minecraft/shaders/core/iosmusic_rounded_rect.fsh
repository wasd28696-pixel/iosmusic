#version 150

in vec4 vertexColor;
in vec2 screenPos;

uniform vec4 ColorModulator;
uniform vec4 Rect;
uniform float Radius;
uniform float Thickness;
uniform float Mode;
uniform vec2 P0;
uniform vec2 P1;
uniform vec2 P2;
uniform float ArcRadius;
uniform float ArcStart;
uniform float ArcSweep;

out vec4 fragColor;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float edge(vec2 a, vec2 b, vec2 p) {
    return (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x);
}

float sdSegment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.00001), 0.0, 1.0);
    return length(pa - ba * h);
}

void main() {
    if (vertexColor.a <= 0.001) {
        discard;
    }

    float aa;
    float coverage = 0.0;

    if (Mode < 0.5) {
        vec2 center = Rect.xy + Rect.zw * 0.5;
        vec2 halfSize = max(Rect.zw * 0.5, vec2(0.0));
        float radius = clamp(Radius, 0.0, min(halfSize.x, halfSize.y));
        float d = sdRoundBox(screenPos - center, halfSize, radius);
        aa = max(fwidth(d), 0.0001);
        coverage = 1.0 - smoothstep(-aa, aa, d);
    } else if (Mode < 1.5) {
        vec2 center = Rect.xy + Rect.zw * 0.5;
        vec2 halfSize = max(Rect.zw * 0.5, vec2(0.0));
        float radius = clamp(Radius, 0.0, min(halfSize.x, halfSize.y));
        float d = sdRoundBox(screenPos - center, halfSize, radius);
        aa = max(fwidth(d), 0.0001);
        coverage = 1.0 - smoothstep(-aa, aa, d);
        float innerRadius = max(radius - Thickness, 0.0);
        vec2 innerHalf = max(halfSize - vec2(Thickness), vec2(0.0));
        float di = sdRoundBox(screenPos - center, innerHalf, innerRadius);
        float innerCoverage = 1.0 - smoothstep(-aa, aa, di);
        coverage = clamp(coverage - innerCoverage, 0.0, 1.0);
    } else if (Mode < 2.5) {
        float d = sdSegment(screenPos, P0, P1) - Thickness * 0.5;
        aa = max(fwidth(d), 0.0001);
        coverage = 1.0 - smoothstep(-aa, aa, d);
    } else if (Mode < 3.5) {
        float orientation = sign(edge(P0, P1, P2));
        if (orientation == 0.0) orientation = 1.0;
        float e0 = edge(P0, P1, screenPos) * orientation;
        float e1 = edge(P1, P2, screenPos) * orientation;
        float e2 = edge(P2, P0, screenPos) * orientation;
        float l0 = max(length(P1 - P0), 0.0001);
        float l1 = max(length(P2 - P1), 0.0001);
        float l2 = max(length(P0 - P2), 0.0001);
        float d = min(min(e0 / l0, e1 / l1), e2 / l2);
        aa = max(fwidth(d), 0.0001);
        coverage = smoothstep(-aa, aa, d);
    } else {
        vec2 q = screenPos - P0;
        float radial = abs(length(q) - ArcRadius) - Thickness * 0.5;
        float angle = degrees(atan(q.y, q.x));
        float rel = mod(angle - ArcStart + 720.0, 360.0);
        float angularInside = min(rel, max(ArcSweep, 0.1) - rel) * 0.01745329252 * max(ArcRadius, 0.001);
        float d = max(radial, -angularInside);
        aa = max(fwidth(d), 0.0001);
        coverage = 1.0 - smoothstep(-aa, aa, d);
    }

    if (coverage <= 0.0005) {
        discard;
    }

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * coverage);
}
