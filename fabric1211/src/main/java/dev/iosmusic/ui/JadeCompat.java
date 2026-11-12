package dev.iosmusic.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;

import java.lang.reflect.Field;

/**
 * Soft bridge to Jade. Reflection keeps the mod optional: if Jade is absent or its internals
 * change, the HUD simply falls back to the normal top position.
 */
public final class JadeCompat {
    private static boolean resolved;
    private static Field shownField;
    private static Field rectField;
    private static Field rectBoundsField;

    private JadeCompat() {
    }

    /** Bottom of Jade's currently visible tooltip in GUI coordinates, or 0 when not visible. */
    public static float tooltipBottom() {
        resolve();
        if (shownField == null || rectField == null || rectBoundsField == null) return 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.screen != null) return 0;
        try {
            if (!shownField.getBoolean(null)) return 0;
            Object tooltipRect = rectField.get(null);
            if (tooltipRect == null) return 0;
            Rect2i bounds = (Rect2i) rectBoundsField.get(tooltipRect);
            if (bounds == null || bounds.getWidth() <= 0 || bounds.getHeight() <= 0) return 0;
            // Only avoid overlays sitting in the upper half; a bottom-positioned Jade panel should
            // not push the island off screen.
            if (bounds.getY() > minecraft.getWindow().getGuiScaledHeight() / 2) return 0;
            return bounds.getY() + bounds.getHeight();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> renderer = Class.forName("snownee.jade.overlay.OverlayRenderer");
            shownField = renderer.getField("shown");
            rectField = renderer.getField("rect");
            Class<?> tooltipRect = Class.forName("snownee.jade.api.ui.TooltipRect");
            rectBoundsField = tooltipRect.getField("rect");
        } catch (Throwable ignored) {
            shownField = null;
            rectField = null;
            rectBoundsField = null;
        }
    }
}
