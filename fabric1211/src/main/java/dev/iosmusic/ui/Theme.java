package dev.iosmusic.ui;

import dev.iosmusic.ModConfig;

/** Dark iOS-inspired palette used by the player. */
public final class Theme {
    private Theme() {
    }

    public static int text() {
        return 0xFFF7F8FC;
    }

    public static int secondary() {
        return 0xB9E7EAF2;
    }

    public static int muted() {
        return 0x78D5DAE5;
    }

    public static int faint() {
        return 0x44D5DAE5;
    }

    public static int accent() {
        return ModConfig.get().accentColor();
    }

    public static int accentSoft(int alpha) {
        int a = accent();
        return Glass.argb(alpha, Glass.red(a), Glass.green(a), Glass.blue(a));
    }

    public static int success() {
        return 0xFF34D399;
    }

    public static int warning() {
        return 0xFFFFB020;
    }

    public static int danger() {
        return 0xFFFF5A67;
    }

    public static int pink() {
        return 0xFFFF5E9A;
    }

    public static int purple() {
        return 0xFF9B7BFF;
    }
}
