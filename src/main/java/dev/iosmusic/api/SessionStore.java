package dev.iosmusic.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.iosmusic.ModConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stores the latest login cookie. Kept outside config.json so it is easy to delete. */
public final class SessionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Saved {
        public String mode = "OFFICIAL";
        public String cookie = "";
        public String nickname = "";
        public String avatar = "";
        public long uid;
        public int vip;
        public int level;
        public boolean loggedIn;
    }

    private SessionStore() {
    }

    public static Path file() {
        return ModConfig.directory().resolve("session.json");
    }

    public static Saved load() {
        try {
            if (Files.isRegularFile(file())) {
                Saved saved = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Saved.class);
                if (saved != null && saved.cookie != null) return saved;
            }
        } catch (Exception ignored) {
        }
        return new Saved();
    }

    public static void save(Saved saved) {
        try {
            Files.createDirectories(ModConfig.directory());
            Files.writeString(file(), GSON.toJson(saved), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(file());
        } catch (Exception ignored) {
        }
    }
}
