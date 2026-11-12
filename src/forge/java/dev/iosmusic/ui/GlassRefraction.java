package dev.iosmusic.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.logging.Logger;

/** Draws the rounded glass body by refracting the captured game background. */
public final class GlassRefraction {
    private static final Logger LOG = Logger.getLogger("iOSMusic");
    private static ShaderInstance shader;

    private GlassRefraction() {
    }

    public static void register(RegisterShadersEvent event) {
        ResourceProvider provider = event.getResourceProvider();
        try {
            ShaderInstance instance = new ShaderInstance(provider, "iosmusic:glass",
                    DefaultVertexFormat.POSITION_COLOR);
            event.registerShader(instance, value -> shader = value);
        } catch (IOException e) {
            LOG.warning("Could not load glass refraction shader: " + e);
        }
    }

    public static boolean available() {
        return shader != null && GlassSnapshot.available();
    }

    public static boolean draw(GuiGraphics graphics, float x, float y, float w, float h,
                               float radius, int tint) {
        return draw(graphics, x, y, w, h, radius, tint,
                GlassSnapshot.blurredTextureId(), GlassSnapshot.blurredWidth(),
                GlassSnapshot.blurredHeight(), false);
    }

    /** Same downsample + 5x5 blur method, but no lens/rim/refraction (frosted glass). */
    public static boolean drawFrosted(GuiGraphics graphics, float x, float y, float w, float h,
                                      float radius, int tint) {
        return draw(graphics, x, y, w, h, radius, tint,
                GlassSnapshot.blurredTextureId(), GlassSnapshot.blurredWidth(),
                GlassSnapshot.blurredHeight(), true);
    }

    /** Lyrics use the exact same downsample + 5x5 method, just with their own target. */
    public static boolean drawLyrics(GuiGraphics graphics, float x, float y, float w, float h,
                                     float radius, int tint) {
        return draw(graphics, x, y, w, h, radius, tint,
                GlassSnapshot.lyricsTextureId(), GlassSnapshot.lyricsWidth(),
                GlassSnapshot.lyricsHeight(), false);
    }

    public static boolean drawLyricsFrosted(GuiGraphics graphics, float x, float y, float w, float h,
                                            float radius, int tint) {
        return draw(graphics, x, y, w, h, radius, tint,
                GlassSnapshot.lyricsTextureId(), GlassSnapshot.lyricsWidth(),
                GlassSnapshot.lyricsHeight(), true);
    }

    private static boolean draw(GuiGraphics graphics, float x, float y, float w, float h,
                                float radius, int tint, int blurTexture, float blurWidth,
                                float blurHeight, boolean frostOnly) {
        if (!available() || w <= 0 || h <= 0 || blurTexture == 0) return false;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, blurTexture);
        RenderSystem.setShaderTexture(1, GlassSnapshot.sharpTextureId());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        shader.safeGetUniform("Rect").set(x, y, w, h);
        shader.safeGetUniform("Radius").set(Math.max(0f, radius));
        Minecraft minecraft = Minecraft.getInstance();
        shader.safeGetUniform("ScreenSize").set((float) minecraft.getWindow().getWidth(),
                (float) minecraft.getWindow().getHeight());
        float guiScale = minecraft.getWindow().getWidth()
                / Math.max(1f, (float) minecraft.getWindow().getGuiScaledWidth());
        shader.safeGetUniform("GuiScale").set(guiScale);
        shader.safeGetUniform("BlurTexel").set(1f / Math.max(1f, blurWidth),
                1f / Math.max(1f, blurHeight));
        shader.safeGetUniform("FrostOnly").set(frostOnly ? 1f : 0f);

        float pad = 2.5f;
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.addVertex(matrix, x - pad, y - pad, 0).setColor(tint);
        builder.addVertex(matrix, x + w + pad, y - pad, 0).setColor(tint);
        builder.addVertex(matrix, x + w + pad, y + h + pad, 0).setColor(tint);
        builder.addVertex(matrix, x - pad, y + h + pad, 0).setColor(tint);
        MeshData data = builder.build();
        if (data != null) BufferUploader.drawWithShader(data);
        RenderSystem.enableCull();
        return true;
    }
}
