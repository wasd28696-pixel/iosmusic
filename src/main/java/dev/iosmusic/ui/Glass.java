package dev.iosmusic.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.iosmusic.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Immediate-mode rounded shapes and the iOS-style frosted glass panels used by every surface. */
public final class Glass {
    private static final int SEGMENTS = 18;

    private Glass() {
    }

    public static final class Batch {
        private final GuiGraphics graphics;
        private final Matrix4f matrix;

        private Batch(GuiGraphics graphics) {
            this.graphics = graphics;
            this.matrix = graphics.pose().last().pose();
        }

        public static Batch begin(GuiGraphics graphics) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            return new Batch(graphics);
        }

        public void flush() {
            // Every primitive is submitted immediately. Keeping this no-op preserves the old API.
        }

        private void mesh(java.util.function.Consumer<BufferBuilder> writer) {
            BufferBuilder builder = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            writer.accept(builder);
            MeshData data = builder.build();
            if (data != null) {
                RenderSystem.disableCull();
                RenderType type = RenderType.guiOverlay();
                type.setupRenderState();
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                BufferUploader.drawWithShader(data);
                type.clearRenderState();
            }
            RenderSystem.enableCull();
        }

        private void add(BufferBuilder builder, float x, float y, int color) {
            builder.addVertex(matrix, x, y, 0).setColor(color);
        }

        public void triangle(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
            if (GlassShader.available()) {
                GlassShader.triangle(graphics, x1, y1, x2, y2, x3, y3, color);
                return;
            }
            mesh(b -> {
                add(b, x1, y1, color);
                add(b, x2, y2, color);
                add(b, x3, y3, color);
                add(b, x3, y3, color);
            });
        }

        public void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color) {
            mesh(b -> {
                add(b, x1, y1, color);
                add(b, x2, y2, color);
                add(b, x3, y3, color);
                add(b, x4, y4, color);
            });
        }

        public void gradientQuad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
                                 int c1, int c2, int c3, int c4) {
            mesh(b -> {
                add(b, x1, y1, c1);
                add(b, x2, y2, c2);
                add(b, x3, y3, c3);
                add(b, x4, y4, c4);
            });
        }

        public void rounded(float x, float y, float w, float h, float radius, int top, int bottom) {
            if (GlassShader.available()) {
                GlassShader.fill(graphics, x, y, w, h, radius, top, bottom);
                return;
            }
            List<float[]> points = roundedPoints(x, y, w, h, radius);
            if (points.size() < 3) return;
            int center = mix(top, bottom, 0.5f);
            float cx = x + w / 2f;
            float cy = y + h / 2f;
            mesh(b -> {
                for (int i = 0; i < points.size(); i++) {
                    float[] a = points.get(i);
                    float[] c = points.get((i + 1) % points.size());
                    add(b, cx, cy, center);
                    add(b, a[0], a[1], gradientColor(top, bottom, y, h, a[1]));
                    add(b, c[0], c[1], gradientColor(top, bottom, y, h, c[1]));
                    add(b, c[0], c[1], gradientColor(top, bottom, y, h, c[1]));
                }
            });
        }

        private int gradientColor(int top, int bottom, float y, float h, float py) {
            float t = h <= 0.001f ? 0 : (py - y) / h;
            return mix(top, bottom, t);
        }

        public void outline(float x, float y, float w, float h, float radius, float thickness, int color) {
            if (GlassShader.available()) {
                GlassShader.outline(graphics, x, y, w, h, radius, thickness, color, color);
                return;
            }
            List<float[]> outer = roundedPoints(x, y, w, h, radius);
            List<float[]> inner = insetPoints(outer, x, y, w, h, thickness);
            mesh(b -> {
                for (int i = 0; i < outer.size(); i++) {
                    float[] a = outer.get(i);
                    float[] c = outer.get((i + 1) % outer.size());
                    float[] d = inner.get((i + 1) % inner.size());
                    float[] e = inner.get(i);
                    add(b, a[0], a[1], color);
                    add(b, c[0], c[1], color);
                    add(b, d[0], d[1], color);
                    add(b, e[0], e[1], color);
                }
            });
        }

        public void circle(float cx, float cy, float radius, int color) {
            if (radius <= 0) return;
            if (GlassShader.available()) {
                GlassShader.circle(graphics, cx, cy, radius, color);
                return;
            }
            int segments = Math.max(12, Math.min(64, (int) (radius * 1.6f)));
            mesh(b -> {
                for (int i = 0; i < segments; i++) {
                    double a1 = Math.PI * 2 * i / segments;
                    double a2 = Math.PI * 2 * (i + 1) / segments;
                    add(b, cx, cy, color);
                    add(b, (float) (cx + Math.cos(a1) * radius), (float) (cy + Math.sin(a1) * radius), color);
                    add(b, (float) (cx + Math.cos(a2) * radius), (float) (cy + Math.sin(a2) * radius), color);
                    add(b, (float) (cx + Math.cos(a2) * radius), (float) (cy + Math.sin(a2) * radius), color);
                }
            });
        }

        public void ring(float cx, float cy, float radius, float thickness, int color) {
            if (GlassShader.available()) {
                GlassShader.ring(graphics, cx, cy, radius, thickness, color);
                return;
            }
            arc(cx, cy, radius, thickness, 0, 360, color);
        }

        public void arc(float cx, float cy, float radius, float thickness, float startDegrees,
                        float sweepDegrees, int color) {
            if (radius <= 0) return;
            if (GlassShader.available()) {
                GlassShader.arc(graphics, cx, cy, radius, thickness, startDegrees, sweepDegrees, color);
                return;
            }
            float inner = Math.max(0, radius - thickness / 2f);
            float outer = radius + thickness / 2f;
            int segments = Math.max(8, (int) (Math.abs(sweepDegrees) / 5f));
            mesh(b -> {
                float prevOuterX = 0;
                float prevOuterY = 0;
                float prevInnerX = 0;
                float prevInnerY = 0;
                for (int i = 0; i <= segments; i++) {
                    double angle = Math.toRadians(startDegrees + sweepDegrees * i / segments);
                    float ox = (float) (cx + Math.cos(angle) * outer);
                    float oy = (float) (cy + Math.sin(angle) * outer);
                    float ix = (float) (cx + Math.cos(angle) * inner);
                    float iy = (float) (cy + Math.sin(angle) * inner);
                    if (i > 0) {
                        add(b, prevOuterX, prevOuterY, color);
                        add(b, ox, oy, color);
                        add(b, ix, iy, color);
                        add(b, prevInnerX, prevInnerY, color);
                    }
                    prevOuterX = ox;
                    prevOuterY = oy;
                    prevInnerX = ix;
                    prevInnerY = iy;
                }
            });
        }

        public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
            if (GlassShader.available()) {
                GlassShader.line(graphics, x1, y1, x2, y2, thickness, color);
                return;
            }
            float dx = x2 - x1;
            float dy = y2 - y1;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001f) return;
            float nx = -dy / len * thickness / 2f;
            float ny = dx / len * thickness / 2f;
            quad(x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny, color);
        }
    }

    public static void panel(GuiGraphics g, float x, float y, float w, float h, float radius) {
        panel(g, x, y, w, h, radius, ModConfig.get().accentColor(), 1f, true);
    }

    public static void panel(GuiGraphics g, float x, float y, float w, float h, float radius, int accent,
                             float opacity, boolean refract) {
        if (w <= 0 || h <= 0) return;
        Batch b = Batch.begin(g);
        float transparency = ModConfig.get().glassTransparency / 100f;
        int baseAlpha = (int) ((1f - transparency) * 255f * opacity);
        baseAlpha = Math.max(0, Math.min(255, baseAlpha));
        int base = argb(baseAlpha, 9, 11, 17);

        // Ambient shadow: more pronounced than the glass itself so the panes float above the world.
        for (int i = 6; i >= 1; i--) {
            float grow = i * 2.5f;
            int alpha = (int) (19 * opacity * (1f - i / 8f));
            b.rounded(x - grow, y - grow + 8, w + grow * 2, h + grow * 2, radius + grow,
                    argb(alpha, 0, 0, 0), argb((int) (alpha * 0.45f), 0, 0, 0));
        }

        boolean refracted = refract && GlassRefraction.draw(g, x, y, w, h, radius,
                argb(baseAlpha, 9, 11, 17));
        if (!refracted) {
            b.rounded(x, y, w, h, radius, base, base);
        }

        // Deliberately no gloss, glare, coloured rim or moving shine. The blur, refraction and
        // drop shadow alone define the material; painted highlights were making it look plastic.
        b.flush();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int border = argb((int) (72 * opacity), 255, 255, 255);
        if (GlassShader.available()) {
            GlassShader.outline(g, x + 0.5f, y + 0.5f, w - 1, h - 1, radius, 1f,
                    border, border);
        } else {
            outline(g, x + 0.5f, y + 0.5f, w - 1, h - 1, radius, 1f, border);
        }
    }

    public static void rounded(GuiGraphics g, float x, float y, float w, float h, float radius, int color) {        rounded(g, x, y, w, h, radius, color, color);
    }

    public static void rounded(GuiGraphics g, float x, float y, float w, float h, float radius, int top, int bottom) {
        Batch b = Batch.begin(g);
        b.rounded(x, y, w, h, radius, top, bottom);
        b.flush();
    }

    public static void roundedOutline(GuiGraphics g, float x, float y, float w, float h, float radius,
                                      float thickness, int color) {
        outline(g, x, y, w, h, radius, thickness, color);
    }

    public static void shadow(GuiGraphics g, float x, float y, float w, float h, float radius, int strength) {
        Batch b = Batch.begin(g);
        for (int i = 5; i >= 1; i--) {
            float grow = i * 2.2f;
            int alpha = (int) (strength * (1f - i / 7f));
            b.rounded(x - grow, y - grow + 6, w + grow * 2, h + grow * 2, radius + grow,
                    argb(alpha, 0, 0, 0), argb((int) (alpha * 0.55f), 0, 0, 0));
        }
        b.flush();
    }

    public static void circle(GuiGraphics g, float cx, float cy, float radius, int color) {
        Batch b = Batch.begin(g);
        b.circle(cx, cy, radius, color);
        b.flush();
    }

    public static void ring(GuiGraphics g, float cx, float cy, float radius, float thickness, int color) {
        Batch b = Batch.begin(g);
        b.ring(cx, cy, radius, thickness, color);
        b.flush();
    }

    public static void arc(GuiGraphics g, float cx, float cy, float radius, float thickness,
                           float startDegrees, float sweepDegrees, int color) {
        Batch b = Batch.begin(g);
        b.arc(cx, cy, radius, thickness, startDegrees, sweepDegrees, color);
        b.flush();
    }

    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int color) {
        Batch b = Batch.begin(g);
        b.line(x1, y1, x2, y2, thickness, color);
        b.flush();
    }

    public static void verticalGradient(GuiGraphics g, float x, float y, float w, float h, int top, int bottom) {
        Batch b = Batch.begin(g);
        b.gradientQuad(x, y, x + w, y, x + w, y + h, x, y + h, top, top, bottom, bottom);
        b.flush();
    }

    public static void horizontalGradient(GuiGraphics g, float x, float y, float w, float h, int left, int right) {
        Batch b = Batch.begin(g);
        b.gradientQuad(x, y, x + w, y, x + w, y + h, x, y + h, left, right, right, left);
        b.flush();
    }

    public static void glassButton(GuiGraphics g, float x, float y, float w, float h, float radius,
                                   boolean primary, boolean hovered) {
        int accent = ModConfig.get().accentColor();
        float r = Math.min(8f, Math.min(w, h) * 0.5f);
        Batch b = Batch.begin(g);
        if (primary) {
            int fill = hovered ? mix(accent, 0xFFFFFFFF, 0.14f) : accent;
            b.rounded(x, y, w, h, r, argb(238, red(fill), green(fill), blue(fill)),
                    argb(238, red(fill), green(fill), blue(fill)));
        } else {
            int a = hovered ? 40 : 26;
            b.rounded(x, y, w, h, r, argb(a, 255, 255, 255), argb(a, 255, 255, 255));
        }
        b.flush();
        outline(g, x + 0.5f, y + 0.5f, w - 1, h - 1, r, 1f,
                primary ? argb(hovered ? 90 : 70, 255, 255, 255) : argb(hovered ? 60 : 38, 255, 255, 255));
    }

    public static void outline(GuiGraphics g, float x, float y, float w, float h, float radius,
                               float thickness, int color) {
        Batch b = Batch.begin(g);
        b.outline(x, y, w, h, radius, thickness, color);
        b.flush();
    }

    public static void noise(GuiGraphics g, ResourceLocation texture, float x, float y, float w, float h, float alpha) {
        if (texture == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int tile = 128;
        g.enableScissor((int) x, (int) y, (int) (x + w), (int) (y + h));
        int color = argb((int) (alpha * 255), 255, 255, 255);
        for (float ty = y; ty < y + h; ty += tile) {
            for (float tx = x; tx < x + w; tx += tile) {
                g.setColor(1f, 1f, 1f, alpha);
                g.blit(texture, (int) tx, (int) ty, 0, 0, tile, tile, tile, tile);
            }
        }
        g.setColor(1f, 1f, 1f, 1f);
        g.disableScissor();
    }

    public static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    public static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return argb((int) (alpha(a) + (alpha(b) - alpha(a)) * t),
                (int) (red(a) + (red(b) - red(a)) * t),
                (int) (green(a) + (green(b) - green(a)) * t),
                (int) (blue(a) + (blue(b) - blue(a)) * t));
    }

    static List<float[]> roundedPoints(float x, float y, float w, float h, float radius) {
        float r = Math.max(0, Math.min(Math.min(w, h) / 2f, radius));
        int seg = Math.max(3, Math.min(SEGMENTS, (int) (r / 2f) + 3));
        List<float[]> points = new ArrayList<>();
        if (r <= 0.01f) {
            points.add(new float[]{x, y});
            points.add(new float[]{x + w, y});
            points.add(new float[]{x + w, y + h});
            points.add(new float[]{x, y + h});
            return points;
        }
        addArc(points, x + r, y + r, r, 180, 270, seg);
        addArc(points, x + w - r, y + r, r, 270, 360, seg);
        addArc(points, x + w - r, y + h - r, r, 0, 90, seg);
        addArc(points, x + r, y + h - r, r, 90, 180, seg);
        return points;
    }

    private static void addArc(List<float[]> points, float cx, float cy, float radius,
                               float start, float end, int segments) {
        for (int i = 0; i <= segments; i++) {
            double angle = Math.toRadians(start + (end - start) * i / segments);
            points.add(new float[]{(float) (cx + Math.cos(angle) * radius),
                    (float) (cy + Math.sin(angle) * radius)});
        }
    }

    private static List<float[]> insetPoints(List<float[]> points, float x, float y, float w, float h, float inset) {
        List<float[]> result = new ArrayList<>(points.size());
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        for (float[] p : points) {
            float dx = p[0] - cx;
            float dy = p[1] - cy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.0001f) {
                result.add(p);
            } else {
                float scale = Math.max(0f, (len - inset) / len);
                result.add(new float[]{cx + dx * scale, cy + dy * scale});
            }
        }
        return result;
    }

    public static void drawCheck(GuiGraphics g, float cx, float cy, float size, int color) {
        Batch b = Batch.begin(g);
        b.line(cx - size * 0.42f, cy + size * 0.02f, cx - size * 0.1f, cy + size * 0.34f, 2f, color);
        b.line(cx - size * 0.1f, cy + size * 0.34f, cx + size * 0.48f, cy - size * 0.34f, 2f, color);
        b.flush();
    }

    // Instance-style geometry methods kept on Batch for efficient batching.
    static {
    }
}
