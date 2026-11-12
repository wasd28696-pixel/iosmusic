package dev.iosmusic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent, user-facing settings. All values are also editable from the in-game settings page. */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance;

    /** AUTO, OFFICIAL or THIRD_PARTY. */
    public String apiMode = "AUTO";
    public String thirdPartyBaseUrl = "http://127.0.0.1:3000";
    public String thirdPartyHeaders = "";
    public int glassTransparency = 55;
    public int blur = 5;
    public boolean hudEnabled = true;
    public boolean hudLyrics = true;
    public boolean hudTranslation = true;
    public boolean hudAutoJade = true;
    public boolean hudCoverSpin = true;
    public int hudCoverSpinSpeed = 24;
    public int hudOffsetY = 42;
    public int hudOpacity = 82;
    public int hudBlur = 4;
    public boolean lyricsGlass = true;
    public int lyricsOpacity = 45;
    public int lyricsBlur = 4;
    public boolean reduceMotion = false;
    public String accent = "#0A84FF";
    public int volume = 72;
    public boolean autoLogin = true;
    public int configVersion = 11;

    public static ModConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static Path directory() {
        Path gameDir;
        try {
            gameDir = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath();
        } catch (Throwable t) {
            gameDir = Path.of(".");
        }
        return gameDir.resolve("config").resolve("iosmusic");
    }

    public static Path file() {
        return directory().resolve("config.json");
    }

    private static ModConfig load() {
        Path path = file();
        if (Files.isRegularFile(path)) {
            try {
                ModConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ModConfig.class);
                if (loaded != null) {
                    loaded.clamp();
                    return loaded;
                }
            } catch (Exception ignored) {
            }
        }
        ModConfig fresh = new ModConfig();
        fresh.save();
        return fresh;
    }

    public void clamp() {
        glassTransparency = Math.max(0, Math.min(100, glassTransparency));
        blur = Math.max(0, Math.min(10, blur));
        volume = Math.max(0, Math.min(100, volume));
        hudOffsetY = Math.max(-20, Math.min(140, hudOffsetY));
        hudOpacity = Math.max(0, Math.min(100, hudOpacity));
        hudBlur = Math.max(0, Math.min(10, hudBlur));
        if (configVersion < 2) {
            // Older config files predate the movable island and its lyric line.
            hudLyrics = true;
            if (hudOffsetY == 0) hudOffsetY = 42;
            configVersion = 2;
        }
        if (configVersion < 3) {
            // v3 adds independent island opacity/blur controls.
            if (hudOpacity == 0) hudOpacity = 82;
            if (hudBlur == 0) hudBlur = 4;
            configVersion = 3;
        }
        if (configVersion < 4) {
            configVersion = 4;
        }
        if (configVersion < 6) {
            lyricsGlass = true;
            if (lyricsOpacity == 0) lyricsOpacity = 45;
            configVersion = 6;
        }
        if (configVersion < 7) {
            if (lyricsBlur == 0) lyricsBlur = 4;
            configVersion = 7;
        }
        if (configVersion < 8) {
            hudTranslation = true;
            configVersion = 8;
        }
        if (configVersion < 9) {
            hudAutoJade = true;
            configVersion = 9;
        }
        if (configVersion < 10) {
            hudCoverSpin = true;
            configVersion = 10;
        }
        if (configVersion < 11) {
            hudCoverSpinSpeed = 24;
            configVersion = 11;
        }
        hudCoverSpinSpeed = Math.max(0, Math.min(120, hudCoverSpinSpeed));
        lyricsOpacity = Math.max(0, Math.min(100, lyricsOpacity));
        lyricsBlur = Math.max(0, Math.min(10, lyricsBlur));
        if (apiMode == null) apiMode = "AUTO";
        if (!apiMode.equals("AUTO") && !apiMode.equals("OFFICIAL") && !apiMode.equals("THIRD_PARTY")) {
            apiMode = "AUTO";
        }
        if (thirdPartyBaseUrl == null || thirdPartyBaseUrl.isBlank()) {
            thirdPartyBaseUrl = "http://127.0.0.1:3000";
        }
        if (accent == null || !accent.matches("#?[0-9a-fA-F]{6}")) accent = "#0A84FF";
    }

    public synchronized void save() {
        try {
            Files.createDirectories(directory());
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public int accentColor() {
        String value = accent == null ? "0A84FF" : accent.replace("#", "");
        try {
            return 0xFF000000 | Integer.parseInt(value, 16);
        } catch (NumberFormatException e) {
            return 0xFF0A84FF;
        }
    }

    public static void reload() {
        instance = load();
    }
}
