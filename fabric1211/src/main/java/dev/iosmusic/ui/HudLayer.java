package dev.iosmusic.ui;

import dev.iosmusic.ModConfig;
import dev.iosmusic.MusicPlayer;
import dev.iosmusic.api.Models;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Small Dynamic-Island-style now playing pill drawn above the vanilla HUD. */
public final class HudLayer {
    private static float animatedWidth = -1;
    private static float animatedHeight = -1;
    private static float animatedY = -1;
    private static long lastFrame;
    private static float coverAngle;
    private static long coverSongId = Long.MIN_VALUE;

    private HudLayer() {
    }

    public static void render(GuiGraphics g, DeltaTracker deltaTracker) {        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.screen != null || minecraft.options.hideGui) {
            reset();
            return;
        }
        ModConfig config = ModConfig.get();
        if (!config.hudEnabled) {
            reset();
            return;
        }
        MusicPlayer player = MusicPlayer.get();
        Models.Song song = player.current();
        if (song == null) {
            reset();
            return;
        }
        CoverCache.get().tick();
        GlassTexture.noise();

        String title = song.name();
        String secondLine = song.artist();
        String translation = "";
        int secondColor = 0xB8FFFFFF;
        boolean playing = player.state() == MusicPlayer.Playback.PLAYING;
        if (config.hudLyrics && playing) {
            Models.Lyrics lyrics = player.lyrics();
            int index = lyrics.indexAt(player.positionSeconds());
            if (index >= 0) {
                Models.LyricLine lyric = lyrics.lines().get(index);
                if (lyric.text() != null && !lyric.text().isBlank()) {
                    secondLine = lyric.text();
                    secondColor = 0xE6FFFFFF;
                    if (config.hudTranslation && lyric.translation() != null
                            && !lyric.translation().isBlank()) {
                        translation = lyric.translation();
                    }
                }
            }
        }

        Font font = UiFonts.get();
        float titleW = font.width(title);
        float secondW = font.width(secondLine);
        // Width follows the title and the lyric only. A translation must never stretch the pill;
        // it is trimmed inside the same width and only the height animates.
        float targetTextW = Math.max(110, Math.min(300, Math.max(titleW, secondW) + 10));
        float targetWidth = Math.min(g.guiWidth() - 20, 58 + targetTextW);
        // Fixed height: adding/removing the translation line never resizes the island.
        float targetHeight = 44;
        float baseY = 10f + (config.hudAutoJade ? 0f : config.hudOffsetY);
        float jadeBottom = config.hudAutoJade ? JadeCompat.tooltipBottom() : 0f;
        // Vanilla's tab list occupies the top centre. Measure its actual header/footer line count;
        // a server with a tall banner is exactly where a fixed estimate used to fail.
        float tabBottom = TabListCompat.bottom(minecraft);
        float targetY = Math.max(baseY, Math.max(jadeBottom > 0 ? jadeBottom + 8f : 0f,
                tabBottom > 0 ? tabBottom + 8f : 0f));
        targetY = Math.max(2, Math.min(g.guiHeight() - targetHeight - 6, targetY));

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (animatedWidth < 0) {
            animatedWidth = targetWidth;
            animatedHeight = targetHeight;
            animatedY = targetY;
        } else {
            float k = 1f - (float) Math.exp(-dt * 14f);
            animatedWidth += (targetWidth - animatedWidth) * k;
            animatedHeight += (targetHeight - animatedHeight) * k;
            animatedY += (targetY - animatedY) * k;
            if (Math.abs(targetWidth - animatedWidth) < 0.2f) animatedWidth = targetWidth;
            if (Math.abs(targetHeight - animatedHeight) < 0.2f) animatedHeight = targetHeight;
            if (Math.abs(targetY - animatedY) < 0.2f) animatedY = targetY;
        }

        float width = animatedWidth;
        float height = animatedHeight;
        float x = (g.guiWidth() - width) / 2f;
        float y = animatedY;
        GlassSnapshot.captureSharp();
        GlassSnapshot.captureBlur(config.hudBlur);
        int pillAlpha = (int) (Math.max(0, Math.min(100, config.hudOpacity)) * 2.55f);
        boolean glass = GlassRefraction.draw(g, x, y, width, height, height / 2f,
                Glass.argb(pillAlpha, 9, 11, 17));
        if (!glass) {
            Glass.rounded(g, x, y, width, height, height / 2f,
                    Glass.argb(pillAlpha, 9, 11, 17), Glass.argb(pillAlpha, 6, 8, 13));
        }
        Glass.roundedOutline(g, x + 0.5f, y + 0.5f, width - 1, height - 1, height / 2f, 1f,
                Glass.argb(55, 255, 255, 255));

        float art = 32;
        float artY = y + (height - art) / 2f;
        if (song.id() != coverSongId) {
            coverSongId = song.id();
            coverAngle = 0;
        }
        ResourceLocation texture = CoverCache.get().textureCircle(song.coverUrl());
        if (texture != null) {
            net.minecraft.client.renderer.texture.AbstractTexture image =
                    Minecraft.getInstance().getTextureManager().getTexture(texture);
            if (image != null) image.setFilter(true, false);
            float artX = x + 6;
            boolean spin = config.hudCoverSpin && playing;
            if (spin) {
                coverAngle = (coverAngle + dt * config.hudCoverSpinSpeed) % 360f;
                g.pose().pushPose();
                g.pose().translate(artX + art / 2f, artY + art / 2f, 0);
                g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(coverAngle));
                g.pose().translate(-(artX + art / 2f), -(artY + art / 2f), 0);
            }
            g.blit(texture, (int) artX, (int) artY, 0, 0, (int) art, (int) art, (int) art, (int) art);
            if (spin) g.pose().popPose();
        } else {
            Glass.rounded(g, x + 6, artY, art, art, art / 2f,
                    Glass.argb(120, 40, 48, 66), Glass.argb(80, 16, 20, 30));
            Icons.draw(g, Icons.Icon.MUSIC, x + 12, artY + 6, 20, Glass.withAlpha(0xFFFFFFFF, 130));
        }

        float textW = Math.max(40, width - 58);
        boolean hasTranslation = !translation.isBlank();
        // Without a translation the title/lyric stay at their original positions. When the
        // translation is present they move up to make room inside the fixed 44px height.
        float titleY = y + (hasTranslation ? 6 : 9);
        float secondY = y + (hasTranslation ? 20 : 25);
        Text.draw(g, font, Text.ellipsize(font, title, textW, 0.74f),
                x + 46, titleY, 0.74f, 0xF4FFFFFF);
        Text.draw(g, font, Text.ellipsize(font, secondLine, textW, 0.66f),
                x + 46, secondY, 0.66f, secondColor);
        if (hasTranslation) {
            Text.drawSmall(g, font, Text.ellipsize(font, translation, textW / 0.66f, 1f),
                    x + 46, y + 33, 0.66f, 0xC0FFFFFF);
        }
    }

    private static void reset() {
        animatedWidth = -1;
        animatedHeight = -1;
        animatedY = -1;
        lastFrame = 0;
    }
}
