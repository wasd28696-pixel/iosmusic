package dev.iosmusic.ui;

import net.minecraft.client.gui.GuiGraphics;

/** Vector-style icons drawn from rounded primitives, so no texture atlas is needed. */
public final class Icons {
    public enum Icon {
        PLAY, PAUSE, NEXT, PREV, SEARCH, HOME, LIBRARY, SETTINGS, VOLUME, CLOSE, BACK,
        FORWARD, SHUFFLE, REPEAT, REPEAT_ONE, LYRICS, USER, HEART, CHECK, PLUS, MORE,
        REFRESH, MUSIC, CLOUD, ARROW_DOWN, LOGIN, TOGETHER
    }

    private Icons() {
    }

    public static void draw(GuiGraphics graphics, Icon icon, float x, float y, float size, int color) {
        Glass.Batch b = Glass.Batch.begin(graphics);
        float s = size;
        float cx = x + s / 2f;
        float cy = y + s / 2f;
        switch (icon) {
            case PLAY -> b.triangle(x + s * 0.30f, y + s * 0.18f, x + s * 0.30f, y + s * 0.82f,
                    x + s * 0.82f, y + s * 0.50f, color);
            case PAUSE -> {
                b.rounded(x + s * 0.28f, y + s * 0.20f, s * 0.16f, s * 0.60f, s * 0.05f, color, color);
                b.rounded(x + s * 0.56f, y + s * 0.20f, s * 0.16f, s * 0.60f, s * 0.05f, color, color);
            }
            case NEXT -> {
                b.triangle(x + s * 0.22f, y + s * 0.20f, x + s * 0.22f, y + s * 0.80f,
                        x + s * 0.66f, y + s * 0.50f, color);
                b.rounded(x + s * 0.70f, y + s * 0.20f, s * 0.10f, s * 0.60f, s * 0.04f, color, color);
            }
            case PREV -> {
                b.triangle(x + s * 0.78f, y + s * 0.20f, x + s * 0.78f, y + s * 0.80f,
                        x + s * 0.34f, y + s * 0.50f, color);
                b.rounded(x + s * 0.20f, y + s * 0.20f, s * 0.10f, s * 0.60f, s * 0.04f, color, color);
            }
            case SEARCH -> {
                b.ring(x + s * 0.43f, y + s * 0.42f, s * 0.26f, s * 0.075f, color);
                b.line(x + s * 0.62f, y + s * 0.62f, x + s * 0.82f, y + s * 0.84f, s * 0.09f, color);
            }
            case HOME -> {
                b.line(x + s * 0.18f, y + s * 0.48f, x + s * 0.50f, y + s * 0.18f, s * 0.09f, color);
                b.line(x + s * 0.50f, y + s * 0.18f, x + s * 0.82f, y + s * 0.48f, s * 0.09f, color);
                b.line(x + s * 0.25f, y + s * 0.44f, x + s * 0.25f, y + s * 0.80f, s * 0.08f, color);
                b.line(x + s * 0.75f, y + s * 0.44f, x + s * 0.75f, y + s * 0.80f, s * 0.08f, color);
                b.line(x + s * 0.25f, y + s * 0.80f, x + s * 0.75f, y + s * 0.80f, s * 0.08f, color);
            }
            case LIBRARY -> {
                b.rounded(x + s * 0.18f, y + s * 0.24f, s * 0.12f, s * 0.56f, s * 0.04f, color, color);
                b.rounded(x + s * 0.43f, y + s * 0.16f, s * 0.12f, s * 0.64f, s * 0.04f, color, color);
                b.line(x + s * 0.70f, y + s * 0.24f, x + s * 0.82f, y + s * 0.76f, s * 0.11f, color);
            }
            case SETTINGS -> {
                for (int i = 0; i < 3; i++) {
                    float yy = y + s * (0.28f + i * 0.22f);
                    b.line(x + s * 0.16f, yy, x + s * 0.84f, yy, s * 0.07f, color);
                    float knob = x + s * (i == 1 ? 0.62f : 0.38f);
                    b.circle(knob, yy, s * 0.105f, color);
                }
            }
            case VOLUME -> {
                b.rounded(x + s * 0.14f, y + s * 0.40f, s * 0.14f, s * 0.20f, s * 0.03f, color, color);
                b.triangle(x + s * 0.28f, y + s * 0.36f, x + s * 0.28f, y + s * 0.64f,
                        x + s * 0.52f, y + s * 0.78f, color);
                b.triangle(x + s * 0.28f, y + s * 0.36f, x + s * 0.52f, y + s * 0.22f,
                        x + s * 0.52f, y + s * 0.78f, color);
                b.arc(x + s * 0.52f, y + s * 0.50f, s * 0.28f, s * 0.065f, -55, 110, color);
                b.arc(x + s * 0.52f, y + s * 0.50f, s * 0.40f, s * 0.06f, -45, 90, Glass.withAlpha(color, 140));
            }
            case CLOSE -> {
                b.line(x + s * 0.28f, y + s * 0.28f, x + s * 0.72f, y + s * 0.72f, s * 0.09f, color);
                b.line(x + s * 0.72f, y + s * 0.28f, x + s * 0.28f, y + s * 0.72f, s * 0.09f, color);
            }
            case BACK -> {
                b.line(x + s * 0.62f, y + s * 0.22f, x + s * 0.34f, y + s * 0.50f, s * 0.08f, color);
                b.line(x + s * 0.34f, y + s * 0.50f, x + s * 0.62f, y + s * 0.78f, s * 0.08f, color);
            }
            case FORWARD -> {
                b.line(x + s * 0.38f, y + s * 0.22f, x + s * 0.66f, y + s * 0.50f, s * 0.08f, color);
                b.line(x + s * 0.66f, y + s * 0.50f, x + s * 0.38f, y + s * 0.78f, s * 0.08f, color);
            }
            case SHUFFLE -> {
                b.line(x + s * 0.16f, y + s * 0.32f, x + s * 0.38f, y + s * 0.32f, s * 0.07f, color);
                b.line(x + s * 0.38f, y + s * 0.32f, x + s * 0.64f, y + s * 0.68f, s * 0.07f, color);
                b.line(x + s * 0.64f, y + s * 0.68f, x + s * 0.84f, y + s * 0.68f, s * 0.07f, color);
                b.line(x + s * 0.16f, y + s * 0.68f, x + s * 0.38f, y + s * 0.68f, s * 0.07f, color);
                b.line(x + s * 0.38f, y + s * 0.68f, x + s * 0.64f, y + s * 0.32f, s * 0.07f, color);
                b.line(x + s * 0.64f, y + s * 0.32f, x + s * 0.84f, y + s * 0.32f, s * 0.07f, color);
                b.triangle(x + s * 0.80f, y + s * 0.26f, x + s * 0.80f, y + s * 0.38f,
                        x + s * 0.90f, y + s * 0.32f, color);
            }
            case REPEAT, REPEAT_ONE -> {
                b.arc(cx, cy, s * 0.31f, s * 0.065f, -35, 285, color);
                b.triangle(x + s * 0.70f, y + s * 0.16f, x + s * 0.91f, y + s * 0.24f,
                        x + s * 0.72f, y + s * 0.34f, color);
                if (icon == Icon.REPEAT_ONE) b.circle(cx, cy, s * 0.075f, color);
            }
            case LYRICS -> {
                b.rounded(x + s * 0.18f, y + s * 0.22f, s * 0.64f, s * 0.56f, s * 0.12f,
                        Glass.withAlpha(color, 70), Glass.withAlpha(color, 35));
                b.line(x + s * 0.30f, y + s * 0.40f, x + s * 0.70f, y + s * 0.40f, s * 0.055f, color);
                b.line(x + s * 0.30f, y + s * 0.52f, x + s * 0.62f, y + s * 0.52f, s * 0.055f, color);
                b.line(x + s * 0.30f, y + s * 0.64f, x + s * 0.54f, y + s * 0.64f, s * 0.055f, color);
            }
            case USER -> {
                b.circle(cx, y + s * 0.33f, s * 0.155f, color);
                b.arc(cx, y + s * 0.82f, s * 0.31f, s * 0.095f, 195, 150, color);
            }
            case TOGETHER -> {
                b.circle(x + s * 0.34f, y + s * 0.33f, s * 0.13f, color);
                b.circle(x + s * 0.66f, y + s * 0.33f, s * 0.13f, color);
                b.rounded(x + s * 0.18f, y + s * 0.52f, s * 0.30f, s * 0.19f, s * 0.09f, color, color);
                b.rounded(x + s * 0.52f, y + s * 0.52f, s * 0.30f, s * 0.19f, s * 0.09f, color, color);
            }
            case HEART -> {
                b.circle(x + s * 0.35f, y + s * 0.37f, s * 0.20f, color);
                b.circle(x + s * 0.65f, y + s * 0.37f, s * 0.20f, color);
                b.triangle(x + s * 0.16f, y + s * 0.42f, x + s * 0.84f, y + s * 0.42f,
                        x + s * 0.50f, y + s * 0.86f, color);
            }
            case CHECK -> {
                b.line(x + s * 0.20f, y + s * 0.52f, x + s * 0.42f, y + s * 0.73f, s * 0.09f, color);
                b.line(x + s * 0.42f, y + s * 0.73f, x + s * 0.81f, y + s * 0.28f, s * 0.09f, color);
            }
            case PLUS -> {
                b.line(cx, y + s * 0.22f, cx, y + s * 0.78f, s * 0.08f, color);
                b.line(x + s * 0.22f, cy, x + s * 0.78f, cy, s * 0.08f, color);
            }
            case MORE -> {
                b.circle(x + s * 0.26f, cy, s * 0.065f, color);
                b.circle(x + s * 0.50f, cy, s * 0.065f, color);
                b.circle(x + s * 0.74f, cy, s * 0.065f, color);
            }
            case REFRESH -> {
                b.arc(cx, cy, s * 0.31f, s * 0.07f, 35, 290, color);
                b.triangle(x + s * 0.69f, y + s * 0.17f, x + s * 0.92f, y + s * 0.26f,
                        x + s * 0.72f, y + s * 0.36f, color);
            }
            case MUSIC -> {
                b.circle(x + s * 0.36f, y + s * 0.70f, s * 0.13f, color);
                b.line(x + s * 0.47f, y + s * 0.68f, x + s * 0.47f, y + s * 0.26f, s * 0.075f, color);
                b.line(x + s * 0.47f, y + s * 0.26f, x + s * 0.78f, y + s * 0.34f, s * 0.075f, color);
                b.line(x + s * 0.78f, y + s * 0.34f, x + s * 0.78f, y + s * 0.54f, s * 0.075f, color);
                b.circle(x + s * 0.68f, y + s * 0.56f, s * 0.10f, color);
            }
            case CLOUD -> {
                b.circle(x + s * 0.34f, y + s * 0.55f, s * 0.19f, color);
                b.circle(x + s * 0.55f, y + s * 0.44f, s * 0.24f, color);
                b.circle(x + s * 0.73f, y + s * 0.57f, s * 0.17f, color);
                b.rounded(x + s * 0.24f, y + s * 0.55f, s * 0.56f, s * 0.24f, s * 0.11f, color, color);
            }
            case ARROW_DOWN -> {
                b.line(cx, y + s * 0.20f, cx, y + s * 0.72f, s * 0.08f, color);
                b.line(x + s * 0.28f, y + s * 0.50f, cx, y + s * 0.76f, s * 0.08f, color);
                b.line(x + s * 0.72f, y + s * 0.50f, cx, y + s * 0.76f, s * 0.08f, color);
            }
            case LOGIN -> {
                b.line(x + s * 0.22f, y + s * 0.22f, x + s * 0.22f, y + s * 0.78f, s * 0.075f, color);
                b.line(x + s * 0.22f, y + s * 0.22f, x + s * 0.54f, y + s * 0.22f, s * 0.075f, color);
                b.line(x + s * 0.22f, y + s * 0.78f, x + s * 0.54f, y + s * 0.78f, s * 0.075f, color);
                b.line(x + s * 0.44f, y + s * 0.50f, x + s * 0.82f, y + s * 0.50f, s * 0.075f, color);
                b.line(x + s * 0.66f, y + s * 0.36f, x + s * 0.82f, y + s * 0.50f, s * 0.075f, color);
                b.line(x + s * 0.66f, y + s * 0.64f, x + s * 0.82f, y + s * 0.50f, s * 0.075f, color);
            }
        }
        b.flush();
    }

    /** A tiny equalizer used on loading cards and the no-artwork placeholder. */
    public static void equalizer(GuiGraphics graphics, float x, float y, float width, float height, int color, long time) {
        Glass.Batch b = Glass.Batch.begin(graphics);
        int bars = 4;
        for (int i = 0; i < bars; i++) {
            float phase = (float) ((time / 170.0 + i * 0.9) % 2.0);
            float factor = 0.25f + 0.75f * (phase > 1 ? 2 - phase : phase);
            float barW = width / (bars * 2f - 1);
            float barH = height * factor;
            b.rounded(x + i * barW * 2, y + height - barH, barW, barH, barW / 2,
                    Glass.withAlpha(color, 220), Glass.withAlpha(color, 120));
        }
        b.flush();
    }
}
