package dev.iosmusic.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.iosmusic.ModConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Glass compositor source buffers.
 *
 * <p>The sharp target is the untouched scene. The blurred target is produced by a separable
 * Gaussian chain at reduced resolution and is the only thing the glass shader samples. Nothing is
 * ever drawn over the area outside the panes, so the world stays sharp there.</p>
 */
public final class GlassSnapshot {
    private static TextureTarget sharp;
    private static TextureTarget blurA;
    private static final TextureTarget[] lyricsTargets = new TextureTarget[6];
    private static int lyricsTarget;

    private GlassSnapshot() {
    }

    public static void captureSharp() {
        if (!RenderSystem.isOnRenderThreadOrInit()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) return;

        sharp = ensure(sharp, width, height);
        // Pre-allocate every lyrics downsample level up front. The slider then only switches which
        // already-sized target is sampled, never resizes an FBO during the frame.
        for (int factor = 1; factor <= 5; factor++) {
            lyricsTargets[factor] = ensure(lyricsTargets[factor],
                    Math.max(1, width / factor), Math.max(1, height / factor));
        }
        RenderTarget main = minecraft.getMainRenderTarget();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sharp.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
    }

    public static void captureBlur() {
        captureBlur(ModConfig.get().blur);
    }

    /** Runs a separable Gaussian blur over the sharp capture. */
    public static void captureBlur(int blur) {
        if (!RenderSystem.isOnRenderThreadOrInit()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        RenderTarget main = minecraft.getMainRenderTarget();
        if (sharp == null) return;

        int amount = Math.max(0, Math.min(10, blur));
        // Medium-resolution source; the glass shader applies a 5x5 Gaussian kernel in UV space.
        int factor = Math.max(1, Math.round(1 + amount * 0.45f));
        int width = Math.max(1, minecraft.getWindow().getWidth() / factor);
        int height = Math.max(1, minecraft.getWindow().getHeight() / factor);
        blurA = ensure(blurA, width, height);

        GlassBlur.downsample(sharp, blurA, main);
        main.bindWrite(false);
    }

    /** Same downsample + 5x5 kernel method as the panels, with its own adjustable factor. */
    public static void captureLyricsBlur(int blur) {
        if (!RenderSystem.isOnRenderThreadOrInit()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || sharp == null) return;
        int amount = Math.max(0, Math.min(10, blur));
        int factor = Math.max(1, Math.min(5, Math.round(1 + amount * 0.45f)));
        TextureTarget target = lyricsTargets[factor];
        if (target == null) return;
        lyricsTarget = factor;
        GlassBlur.downsample(sharp, target, minecraft.getMainRenderTarget());
        minecraft.getMainRenderTarget().bindWrite(false);
    }

    private static TextureTarget ensure(TextureTarget target, int width, int height) {
        if (target == null) {
            target = new TextureTarget(width, height, false, false);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, false);
        }
        target.setFilterMode(GL11.GL_LINEAR);
        return target;
    }

    public static int sharpTextureId() {
        return sharp == null ? 0 : sharp.getColorTextureId();
    }

    public static int blurredTextureId() {
        return blurA == null ? 0 : blurA.getColorTextureId();
    }

    public static float blurredWidth() {
        return blurA == null ? 1f : blurA.width;
    }

    public static float blurredHeight() {
        return blurA == null ? 1f : blurA.height;
    }

    public static float blurSpread() {
        return 1f;
    }

    public static int lyricsTextureId() {
        TextureTarget target = lyricsTargets[lyricsTarget];
        return target == null ? 0 : target.getColorTextureId();
    }

    public static float lyricsWidth() {
        TextureTarget target = lyricsTargets[lyricsTarget];
        return target == null ? 1f : target.width;
    }

    public static float lyricsHeight() {
        TextureTarget target = lyricsTargets[lyricsTarget];
        return target == null ? 1f : target.height;
    }

    public static boolean available() {
        return sharp != null && blurA != null
                && sharp.getColorTextureId() > 0 && blurA.getColorTextureId() > 0;
    }
}
