package dev.iosmusic.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tolerant JSON readers. NetEase and its community servers disagree on many field names. */
public final class JsonUtil {
    private JsonUtil() {
    }

    public static JsonObject parse(String body) {
        JsonElement element = JsonParser.parseString(body == null ? "{}" : body);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    public static String str(JsonObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            JsonElement e = object.get(key);
            if (e != null && !e.isJsonNull() && e.isJsonPrimitive()) {
                return e.getAsString();
            }
        }
        return "";
    }

    public static long lng(JsonObject object, String... keys) {
        if (object == null) return 0;
        for (String key : keys) {
            JsonElement e = object.get(key);
            if (e != null && !e.isJsonNull() && e.isJsonPrimitive()) {
                try {
                    return e.getAsLong();
                } catch (Exception ignored) {
                }
            }
        }
        return 0;
    }

    public static int integer(JsonObject object, int fallback, String... keys) {
        if (object == null) return fallback;
        for (String key : keys) {
            JsonElement e = object.get(key);
            if (e != null && !e.isJsonNull() && e.isJsonPrimitive()) {
                try {
                    return e.getAsInt();
                } catch (Exception ignored) {
                }
            }
        }
        return fallback;
    }

    public static boolean bool(JsonObject object, String key) {
        JsonElement e = object == null ? null : object.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    public static JsonObject obj(JsonObject object, String key) {
        if (object == null) return null;
        JsonElement e = object.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    public static JsonArray arr(JsonObject object, String key) {
        if (object == null) return null;
        JsonElement e = object.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    public static String firstUrl(JsonObject object, String... keys) {
        String value = str(object, keys);
        if (value.isBlank()) return "";
        if (value.startsWith("//")) value = "https:" + value;
        if (value.startsWith("http://")) {
            value = "https://" + value.substring("http://".length());
        }
        return value;
    }

    public static Models.Song song(JsonObject object) {
        if (object == null) return null;
        JsonObject inner = obj(object, "song");
        if (inner != null) object = inner;
        long id = lng(object, "id", "songId");
        if (id <= 0) return null;
        String name = str(object, "name", "songName");
        if (name.isBlank()) return null;

        String artist = "";
        JsonArray artists = arr(object, "ar");
        if (artists == null) artists = arr(object, "artists");
        if (artists != null) {
            List<String> names = new ArrayList<>();
            for (JsonElement e : artists) {
                if (e.isJsonObject()) {
                    String n = str(e.getAsJsonObject(), "name");
                    if (!n.isBlank()) names.add(n);
                }
            }
            artist = String.join(" / ", names);
        }
        if (artist.isBlank()) artist = str(object, "artist", "singer");

        JsonObject album = obj(object, "al");
        if (album == null) album = obj(object, "album");
        String albumName = album == null ? "" : str(album, "name");
        long albumId = album == null ? 0 : lng(album, "id");
        String cover = album == null ? "" : firstUrl(album, "picUrl", "blurPicUrl", "coverUrl");
        if (cover.isBlank()) cover = firstUrl(object, "picUrl", "coverUrl", "img1v1Url");

        long duration = lng(object, "dt", "duration", "interval");
        int fee = integer(object, 0, "fee");
        boolean trial = object.has("freeTrialInfo") && !object.get("freeTrialInfo").isJsonNull();
        return new Models.Song(id, name, artist, albumName, albumId, cover, duration, fee, trial);
    }

    public static Models.Playlist playlist(JsonObject object) {
        if (object == null) return null;
        long id = lng(object, "id", "playlistId");
        if (id <= 0) return null;
        String name = str(object, "name", "title");
        if (name.isBlank()) return null;
        String cover = firstUrl(object, "coverImgUrl", "picUrl", "coverUrl", "img1v1Url");
        int count = integer(object, 0, "trackCount", "songCount");
        String description = str(object, "description", "desc");
        String creator = "";
        JsonObject creatorObj = obj(object, "creator");
        if (creatorObj != null) creator = str(creatorObj, "nickname", "name");
        if (creator.isBlank()) creator = str(object, "creatorName");
        String update = str(object, "updateFrequency");
        return new Models.Playlist(id, name, cover, count, description, creator, update);
    }

    public static Models.UserProfile profile(JsonObject root) {
        if (root == null) return null;
        JsonObject object = obj(root, "profile");
        if (object == null) object = obj(root, "account");
        if (object == null && (root.has("nickname") || root.has("userId"))) object = root;
        if (object == null) return null;
        long id = lng(object, "userId", "id");
        String nickname = str(object, "nickname", "userName");
        if (id <= 0 && nickname.isBlank()) return null;
        String avatar = firstUrl(object, "avatarUrl", "avatar", "avatarImgUrl");
        int vip = integer(object, 0, "vipType");
        String signature = str(object, "signature");
        int level = integer(object, 0, "level");
        return new Models.UserProfile(id, nickname, avatar, vip, signature, level);
    }

    public static List<Models.Song> songs(JsonArray array) {
        List<Models.Song> songs = new ArrayList<>();
        if (array == null) return songs;
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                Models.Song song = song(element.getAsJsonObject());
                if (song != null) songs.add(song);
            }
        }
        return songs;
    }

    public static List<Models.Playlist> playlists(JsonArray array) {
        List<Models.Playlist> playlists = new ArrayList<>();
        if (array == null) return playlists;
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                Models.Playlist playlist = playlist(element.getAsJsonObject());
                if (playlist != null) playlists.add(playlist);
            }
        }
        return playlists;
    }

    public static Models.Lyrics lyrics(JsonObject root) {
        JsonObject lrc = obj(root, "lrc");
        if (lrc == null) return Models.Lyrics.empty();
        String raw = str(lrc, "lyric");
        if (raw.isBlank()) return Models.Lyrics.empty();
        Map<Long, String> translations = new LinkedHashMap<>();
        JsonObject tlyric = obj(root, "tlyric");
        if (tlyric != null) {
            parseLrc(str(tlyric, "lyric"), translations);
        }
        Map<Long, String> original = new LinkedHashMap<>();
        parseLrc(raw, original);
        List<Models.LyricLine> lines = new ArrayList<>();
        for (var entry : original.entrySet()) {
            String translated = translations.getOrDefault(entry.getKey(), "");
            lines.add(new Models.LyricLine(entry.getKey(), entry.getValue(), translated));
        }
        return new Models.Lyrics(List.copyOf(lines), raw);
    }

    private static void parseLrc(String raw, Map<Long, String> target) {
        if (raw == null) return;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
        for (String rawLine : raw.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            var matcher = pattern.matcher(line);
            List<Long> times = new ArrayList<>();
            int lastEnd = -1;
            while (matcher.find()) {
                int minutes = Integer.parseInt(matcher.group(1));
                int seconds = Integer.parseInt(matcher.group(2));
                String fraction = matcher.group(3);
                int millis = 0;
                if (fraction != null) {
                    if (fraction.length() == 1) millis = Integer.parseInt(fraction) * 100;
                    else if (fraction.length() == 2) millis = Integer.parseInt(fraction) * 10;
                    else millis = Integer.parseInt(fraction.substring(0, 3));
                }
                times.add((minutes * 60L + seconds) * 1000L + millis);
                lastEnd = matcher.end();
            }
            if (times.isEmpty() || lastEnd < 0) continue;
            String text = line.substring(lastEnd).trim();
            if (text.isEmpty()) continue;
            for (long time : times) target.putIfAbsent(time, text);
        }
    }

    private static long parseStamp(String value) {
        try {
            String[] main = value.split(":");
            if (main.length != 2) return -1;
            int minutes = Integer.parseInt(main[0].trim());
            String secondsPart = main[1].trim().replace(',', '.');
            double seconds = Double.parseDouble(secondsPart);
            return (long) ((minutes * 60.0 + seconds) * 1000.0);
        } catch (Exception e) {
            return -1;
        }
    }

    public static int code(JsonObject object) {
        return integer(object, -1, "code", "status");
    }

    public static String message(JsonObject object) {
        return str(object, "message", "msg", "error");
    }
}
