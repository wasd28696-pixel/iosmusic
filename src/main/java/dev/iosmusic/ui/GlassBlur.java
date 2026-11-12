package dev.iosmusic.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Separable Gaussian blur used by the glass compositor. Each pass reads one low-resolution target
 * and writes the other, so the result is a proper smooth Gaussian instead of the vanilla box blur.
 */
public final class GlassBlur {
    private static final Logger LOG = Logger.getLogger("iOSMusic");
    private static ShaderInstance shader;

    private GlassBlur() {
    }

    public static void register(RegisterShadersEvent event) {
        ResourceProvider provider = event.getResourceProvider();
        try {
            ShaderInstance instance = new ShaderInstance(provider, "iosmusic:glass_blur",
                    DefaultVertexFormat.POSITION_COLOR);
            event.registerShader(instance, value -> shader = value);
        } catch (IOException e) {
            LOG.warning("Could not load glass blur shader: " + e);
        }
    }

    public static void downsample(RenderTarget source, RenderTarget destination, RenderTarget main) {
        // Do not use bindRead()/bindWrite() together here: bindWrite rebinds GL_FRAMEBUFFER and
        // would drop the read framebuffer, causing the blit to read from the destination.
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(
                org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(
                org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, destination.frameBufferId);
        com.mojang.blaze3d.platform.GlStateManager._glBlitFrameBuffer(
                0, 0, source.width, source.height,
                0, 0, destination.width, destination.height,
                org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT,
                org.lwjgl.opengl.GL11.GL_LINEAR);
        main.bindWrite(false);
    }

    public static void gaussian(RenderTarget source, RenderTarget destination, float spread,
                                boolean horizontal, RenderTarget main) {
        if (shader == null) {
            downsample(source, destination, main);
            return;
        }
        destination.bindWrite(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, source.getColorTextureId());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        shader.safeGetUniform("Size").set((float) destination.width, (float) destination.height);
        shader.safeGetUniform("TexelSize").set(1f / destination.width, 1f / destination.height);
        shader.safeGetUniform("Direction").set(horizontal ? spread : 0f, horizontal ? 0f : spread);

        Matrix4f identity = new Matrix4f();
        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.addVertex(identity, 0, 0, 0).setColor(0xFFFFFFFF);
        builder.addVertex(identity, destination.width, 0, 0).setColor(0xFFFFFFFF);
        builder.addVertex(identity, destination.width, destination.height, 0).setColor(0xFFFFFFFF);
        builder.addVertex(identity, 0, destination.height, 0).setColor(0xFFFFFFFF);
        MeshData data = builder.build();
        if (data != null) BufferUploader.drawWithShader(data);
        main.bindWrite(false);
    }
}
