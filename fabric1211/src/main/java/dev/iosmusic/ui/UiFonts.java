package dev.iosmusic.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads the bundled Inter / Noto Sans SC font through Minecraft's own font stack. The game has no
 * public API for retrieving a named FontSet, so the manager's font map is read reflectively once
 * and exposed as a normal {@link Font}. Missing glyphs fall back to unifont through the font JSON.
 */
public final class UiFonts {
    private static final Logger LOG = Logger.getLogger("iOSMusic");
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("iosmusic", "ui");
    private static Font font;
    private static boolean warned;

    private UiFonts() {
    }

    public static Font get() {
        if (font == null) build();
        return font != null ? font : Minecraft.getInstance().font;
    }

    private static void build() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Field managerField = Minecraft.class.getDeclaredField("fontManager");
            managerField.setAccessible(true);
            FontManager manager = (FontManager) managerField.get(minecraft);
            Field setsField = FontManager.class.getDeclaredField("fontSets");
            setsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<ResourceLocation, FontSet> sets = (Map<ResourceLocation, FontSet>) setsField.get(manager);
            FontSet custom = sets.get(ID);
            if (custom == null) {
                if (!warned) {
                    warned = true;
                    LOG.warning("Bundled font iosmusic:ui was not loaded; using the vanilla font");
                }
                return;
            }
            // Every chat component has the style font "minecraft:default", so the Font must route
            // all lookups to the bundled set. Returning the id's own FontSet here would silently
            // keep using the vanilla pixel font even though iosmusic:ui loaded correctly.
            font = new Font(id -> custom, false);
            LOG.info("Using bundled Inter / Noto Sans SC UI font");
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOG.warning("Could not load the bundled UI font: " + t);
            }
        }
    }
}
