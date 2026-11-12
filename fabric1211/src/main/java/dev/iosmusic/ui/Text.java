package dev.iosmusic.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Shared text layout helpers. Minecraft's bitmap font is scaled for display-style typography. */
public final class Text {
    /**
     * Minecraft's bitmap font is designed for integer GUI scales. Non-integer scaling makes CJK
     * glyphs collapse into each other, so display text never drops below 1.0 and positions are
     * snapped to half a GUI pixel.
     */
    private static final float MIN_SCALE = 1.0f;

    private Text() {
    }

    private static float scale(float value) {
        return Math.max(MIN_SCALE, value);
    }

    private static float snap(float value) {
        return Math.round(value * 2f) / 2f;
    }

    public static void draw(GuiGraphics g, net.minecraft.client.gui.Font font, String value,
                            float x, float y, float scale, int color) {
        if (value == null || value.isEmpty()) return;
        scale = scale(scale);
        g.pose().pushPose();
        g.pose().translate(snap(x), snap(y) + 1f, 0);
        g.pose().scale(scale, scale, 1f);
        // A very soft 1px shadow keeps type legible on bright parts of the glass without looking
        // like the chunky drop shadow of the vanilla bitmap font.
        g.drawString(font, value, 1, 1, 0x45000000, false);
        g.drawString(font, value, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Tiny secondary line (for example a lyric translation), allowed below the UI's 1.0 floor. */
    public static void drawSmall(GuiGraphics g, net.minecraft.client.gui.Font font, String value,
                                 float x, float y, float scale, int color) {
        if (value == null || value.isEmpty()) return;
        float s = Math.max(0.5f, Math.min(1f, scale));
        g.pose().pushPose();
        g.pose().translate(snap(x), snap(y) + 0.5f, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, value, 0, 0, color, false);
        g.pose().popPose();
    }

    public static void shadow(GuiGraphics g, net.minecraft.client.gui.Font font, String value,
                              float x, float y, float scale, int color) {        if (value == null || value.isEmpty()) return;
        scale = scale(scale);
        g.pose().pushPose();
        g.pose().translate(snap(x), snap(y) + 1f, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, value, 0, 0, color, true);
        g.pose().popPose();
    }

    public static void centered(GuiGraphics g, net.minecraft.client.gui.Font font, String value,
                                float cx, float y, float scale, int color) {
        draw(g, font, value, cx - width(font, value, scale) / 2f, y, scale, color);
    }

    public static void spaced(GuiGraphics g, net.minecraft.client.gui.Font font, String value,
                              float x, float y, float scale, float spacing, int color) {
        if (value == null || value.isEmpty()) return;
        scale = scale(scale);
        float cursor = x;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            draw(g, font, ch, cursor, y, scale, color);
            cursor += font.width(ch) * scale + spacing;
            i += Character.charCount(cp);
        }
    }

    public static float width(net.minecraft.client.gui.Font font, String value, float scale) {
        return value == null ? 0 : font.width(value) * scale(scale);
    }

    public static String ellipsize(net.minecraft.client.gui.Font font, String value, float maxWidth, float scale) {
        if (value == null) return "";
        scale = scale(scale);
        if (width(font, value, scale) <= maxWidth) return value;
        String dots = "…";
        float dotsWidth = width(font, dots, scale);
        StringBuilder builder = new StringBuilder();
        float used = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            float w = font.width(ch) * scale;
            if (used + w + dotsWidth > maxWidth) break;
            builder.append(ch);
            used += w;
            i += Character.charCount(cp);
        }
        return builder + dots;
    }

    public static List<String> wrap(net.minecraft.client.gui.Font font, String value, float maxWidth, float scale) {
        List<String> lines = new ArrayList<>();
        if (value == null || value.isEmpty()) return lines;
        scale = scale(scale);
        StringBuilder current = new StringBuilder();
        float used = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            float w = font.width(ch) * scale;
            if (ch.equals("\n")) {
                lines.add(current.toString());
                current.setLength(0);
                used = 0;
                i++;
                continue;
            }
            if (used + w > maxWidth && current.length() > 0) {
                int breakAt = current.lastIndexOf(" ");
                if (breakAt > 0 && !isCjk(ch)) {
                    String rest = current.substring(breakAt + 1);
                    lines.add(current.substring(0, breakAt));
                    current.setLength(0);
                    current.append(rest);
                    used = font.width(rest) * scale;
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    used = 0;
                }
            }
            current.append(ch);
            used += w;
            i += Character.charCount(cp);
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private static boolean isCjk(String ch) {
        if (ch.isEmpty()) return false;
        char c = ch.charAt(0);
        return c >= 0x2E80 && c <= 0x9FFF;
    }
}
