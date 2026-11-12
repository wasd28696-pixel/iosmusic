package dev.iosmusic.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Signed-distance-field shapes for the whole UI. Rounded rectangles, rings, capsules, triangles
 * and arcs all get an analytic one-pixel anti-aliased edge instead of the stair-stepping produced
 * by triangle fans.
 */
public final class GlassShader {
    private static final Logger LOG = Logger.getLogger("iOSMusic");
    private static ShaderInstance shader;

    private GlassShader() {
    }

    private static void ensure() {
        if (shader != null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        try {
            shader = new ShaderInstance(minecraft.getResourceManager(), "iosmusic_rounded_rect",
                    DefaultVertexFormat.POSITION_COLOR);
        } catch (IOException e) {
            LOG.warning("Could not load GlassShader shader: " + e);
        }
    }

    public static boolean available() {
        ensure();
        return shader != null;
    }

    public static void fill(GuiGraphics graphics, float x, float y, float w, float h, float radius,
                            int top, int bottom) {
        if (shader == null || w <= 0 || h <= 0) return;
        begin();
        setRect(x, y, w, h, radius, 0f, 0f);
        drawQuad(graphics, x - 2, y - 2, x + w + 2, y + h + 2, top, bottom);
    }

    public static void outline(GuiGraphics graphics, float x, float y, float w, float h, float radius,
                               float thickness, int top, int bottom) {
        if (shader == null || w <= 0 || h <= 0) return;
        begin();
        setRect(x, y, w, h, radius, thickness, 1f);
        drawQuad(graphics, x - 2, y - 2, x + w + 2, y + h + 2, top, bottom);
    }

    public static void circle(GuiGraphics graphics, float cx, float cy, float radius, int color) {
        fill(graphics, cx - radius, cy - radius, radius * 2f, radius * 2f, radius, color, color);
    }

    public static void ring(GuiGraphics graphics, float cx, float cy, float radius, float thickness, int color) {
        outline(graphics, cx - radius, cy - radius, radius * 2f, radius * 2f, radius, thickness, color, color);
    }

    /** A capsule with round end caps. Used for all icon strokes. */
    public static void line(GuiGraphics graphics, float x1, float y1, float x2, float y2,
                            float thickness, int color) {
        if (shader == null) return;
        begin();
        shader.safeGetUniform("Mode").set(2f);
        shader.safeGetUniform("Thickness").set(Math.max(0.01f, thickness));
        shader.safeGetUniform("P0").set(x1, y1);
        shader.safeGetUniform("P1").set(x2, y2);
        float half = thickness * 0.5f + 2f;
        drawQuad(graphics, Math.min(x1, x2) - half, Math.min(y1, y2) - half,
                Math.max(x1, x2) + half, Math.max(y1, y2) + half, color, color);
    }

    public static void triangle(GuiGraphics graphics, float x1, float y1, float x2, float y2,
                                float x3, float y3, int color) {
        if (shader == null) return;
        begin();
        shader.safeGetUniform("Mode").set(3f);
        shader.safeGetUniform("P0").set(x1, y1);
        shader.safeGetUniform("P1").set(x2, y2);
        shader.safeGetUniform("P2").set(x3, y3);
        float pad = 2f;
        drawQuad(graphics, Math.min(x1, Math.min(x2, x3)) - pad, Math.min(y1, Math.min(y2, y3)) - pad,
                Math.max(x1, Math.max(x2, x3)) + pad, Math.max(y1, Math.max(y2, y3)) + pad,
                color, color);
    }

    public static void arc(GuiGraphics graphics, float cx, float cy, float radius, float thickness,
                           float startDegrees, float sweepDegrees, int color) {
        if (shader == null || radius <= 0) return;
        begin();
        shader.safeGetUniform("Mode").set(4f);
        shader.safeGetUniform("Thickness").set(Math.max(0.01f, thickness));
        shader.safeGetUniform("P0").set(cx, cy);
        shader.safeGetUniform("ArcRadius").set(radius);
        shader.safeGetUniform("ArcStart").set(startDegrees);
        shader.safeGetUniform("ArcSweep").set(Math.max(0.1f, sweepDegrees));
        float extent = radius + thickness * 0.5f + 2f;
        drawQuad(graphics, cx - extent, cy - extent, cx + extent, cy + extent, color, color);
    }

    private static void begin() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void setRect(float x, float y, float w, float h, float radius,
                                float thickness, float mode) {
        shader.safeGetUniform("Rect").set(x, y, w, h);
        shader.safeGetUniform("Radius").set(Math.max(0f, radius));
        shader.safeGetUniform("Thickness").set(Math.max(0f, thickness));
        shader.safeGetUniform("Mode").set(mode);
    }

    private static void drawQuad(GuiGraphics graphics, float x0, float y0, float x1, float y1,
                                 int top, int bottom) {
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.addVertex(matrix, x0, y0, 0).setColor(top);
        builder.addVertex(matrix, x1, y0, 0).setColor(top);
        builder.addVertex(matrix, x1, y1, 0).setColor(bottom);
        builder.addVertex(matrix, x0, y1, 0).setColor(bottom);
        MeshData data = builder.build();
        if (data != null) BufferUploader.drawWithShader(data);
        RenderSystem.enableCull();
    }
}
