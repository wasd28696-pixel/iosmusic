package dev.iosmusic.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;

/** Measures the vanilla tab list so the island can sit below it even with a tall server header. */
public final class TabListCompat {
    private static boolean resolved;
    private static Field headerField;
    private static Field footerField;

    private TabListCompat() {
    }

    public static float bottom(Minecraft minecraft) {
        if (minecraft == null || minecraft.getConnection() == null
                || !minecraft.options.keyPlayerList.isDown()) {
            return 0f;
        }
        int count = minecraft.getConnection().getListedOnlinePlayers().size();
        if (count <= 0) return 0f;
        // Vanilla never puts more than 20 rows in one column; using 20 is a safe upper bound.
        int rows = Math.min(20, count);
        int headerLines = 0;
        int footerLines = 0;
        resolve();
        try {
            PlayerTabOverlay overlay = minecraft.gui.getTabList();
            if (overlay != null) {
                Component header = (Component) headerField.get(overlay);
                Component footer = (Component) footerField.get(overlay);
                if (header != null) {
                    headerLines = minecraft.font.split(header,
                            Math.max(40, minecraft.getWindow().getGuiScaledWidth() - 50)).size();
                }
                if (footer != null) {
                    footerLines = minecraft.font.split(footer,
                            Math.max(40, minecraft.getWindow().getGuiScaledWidth() - 50)).size();
                }
            }
        } catch (Throwable ignored) {
            // Conservative fallback for a tall server banner.
            headerLines = 4;
        }
        return 10f + headerLines * 9f + (headerLines > 0 ? 1f : 0f)
                + rows * 9f + footerLines * 9f + 3f;
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            headerField = PlayerTabOverlay.class.getDeclaredField("header");
            footerField = PlayerTabOverlay.class.getDeclaredField("footer");
            headerField.setAccessible(true);
            footerField.setAccessible(true);
        } catch (Throwable ignored) {
            headerField = null;
            footerField = null;
        }
    }
}
