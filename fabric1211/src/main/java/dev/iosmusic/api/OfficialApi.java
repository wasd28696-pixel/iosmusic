package dev.iosmusic.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Direct client for music.163.com. It performs the same weapi encryption as the web player, keeps a
 * browser-like cookie jar, and therefore supports anonymous browsing plus QR/phone/cookie login.
 */
public final class OfficialApi implements MusicApi {
    private static final Logger LOG = Logger.getLogger("iOSMusic");
    private static final String BASE = "https://music.163.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, String> cookies = new LinkedHashMap<>();
    private volatile long uid;

    public OfficialApi() {
        resetAnonymousCookies();
    }

    @Override
    public String backendName() {
        return "网易云官方直连";
    }

    private void resetAnonymousCookies() {
        cookies.clear();
        String nuid = hex(16);
        String device = hex(26).toUpperCase();
        cookies.put("_ntes_nuid", nuid);
        cookies.put("_ntes_nnid", nuid + "," + System.currentTimeMillis());
        cookies.put("WNMCID", randomLetters(6) + "." + System.currentTimeMillis() + ".01.0");
        cookies.put("NMTID", "00O" + randomLetters(24));
        cookies.put("os", "pc");
        cookies.put("appver", "3.1.17.204416");
        cookies.put("osver", "Microsoft-Windows-10-Professional-build-19045-64bit");
        cookies.put("deviceId", device);
        cookies.put("channel", "netease");
        cookies.put("__remember_me", "true");
    }

    @Override
    public Models.HomeData home() throws Exception {
        List<Models.Playlist> recommended = new ArrayList<>();
        List<Models.Playlist> toplists = new ArrayList<>();
        List<Models.Song> newSongs = new ArrayList<>();
        List<Models.Song> daily = new ArrayList<>();
        List<Models.Playlist> userPlaylists = new ArrayList<>();
        Exception firstError = null;

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("limit", 12);
            payload.addProperty("total", true);
            payload.addProperty("n", 1000);
            JsonObject json = post("/weapi/personalized/playlist", payload);
            recommended = JsonUtil.playlists(json.getAsJsonArray("result"));
        } catch (Exception e) {
            firstError = e;
            LOG.warning("Official personalized playlists failed: " + e);
        }

        try {
            JsonObject json = post("/weapi/toplist", new JsonObject());
            toplists = JsonUtil.playlists(json.getAsJsonArray("list"));
        } catch (Exception e) {
            if (firstError == null) firstError = e;
            LOG.warning("Official toplist failed: " + e);
        }

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("type", "recommend");
            payload.addProperty("limit", 12);
            JsonObject json = post("/weapi/personalized/newsong", payload);
            JsonArray array = json.getAsJsonArray("result");
            if (array != null) {
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) continue;
                    Models.Song song = JsonUtil.song(element.getAsJsonObject());
                    if (song != null) newSongs.add(song);
                }
            }
        } catch (Exception e) {
            if (firstError == null) firstError = e;
            LOG.warning("Official new songs failed: " + e);
        }

        if (isLoggedIn()) {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("afresh", false);
                JsonObject json = post("/weapi/v3/discovery/recommend/songs", payload);
                JsonObject data = JsonUtil.obj(json, "data");
                daily = JsonUtil.songs(JsonUtil.arr(data, "dailySongs"));
            } catch (Exception e) {
                LOG.warning("Official daily songs failed: " + e);
            }
            try {
                userPlaylists = userPlaylists();
            } catch (Exception e) {
                LOG.warning("Official user playlists failed: " + e);
            }
        }

        if (recommended.isEmpty() && toplists.isEmpty() && newSongs.isEmpty() && firstError != null) {
            throw firstError;
        }
        return new Models.HomeData(List.copyOf(recommended), List.copyOf(toplists),
                List.copyOf(newSongs), List.copyOf(daily), List.copyOf(userPlaylists));
    }

    @Override
    public Models.SearchResult search(String keywords, int limit, int offset) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("s", keywords);
        payload.addProperty("type", 1);
        payload.addProperty("limit", Math.max(1, limit));
        payload.addProperty("offset", Math.max(0, offset));
        payload.addProperty("total", true);
        JsonObject json = post("/weapi/search/get", payload);
        JsonObject result = JsonUtil.obj(json, "result");
        if (result == null) throw new ApiException(JsonUtil.code(json), JsonUtil.message(json));
        List<Models.Song> songs = JsonUtil.songs(JsonUtil.arr(result, "songs"));
        int total = JsonUtil.integer(result, songs.size(), "songCount", "count");
        return new Models.SearchResult(List.copyOf(songs), total);
    }

    @Override
    public Models.PlaylistDetail playlist(long id) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("id", id);
        payload.addProperty("n", 100000);
        payload.addProperty("s", 8);
        JsonObject json = post("/weapi/v6/playlist/detail", payload);
        JsonObject object = JsonUtil.obj(json, "playlist");
        Models.Playlist playlist = JsonUtil.playlist(object);
        if (playlist == null) throw new ApiException(JsonUtil.code(json), JsonUtil.message(json));
        List<Models.Song> tracks = JsonUtil.songs(JsonUtil.arr(object, "tracks"));
        if (tracks.isEmpty()) {
            JsonArray trackIds = JsonUtil.arr(object, "trackIds");
            if (trackIds != null) {
                List<Long> ids = new ArrayList<>();
                for (JsonElement e : trackIds) {
                    if (e.isJsonObject()) ids.add(JsonUtil.lng(e.getAsJsonObject(), "id"));
                }
                tracks = songDetails(ids);
            }
        }
        return new Models.PlaylistDetail(playlist, List.copyOf(tracks));
    }

    @Override
    public List<Models.Song> songDetails(List<Long> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Models.Song> result = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += 500) {
            List<Long> chunk = ids.subList(start, Math.min(ids.size(), start + 500));
            StringBuilder c = new StringBuilder("[");
            for (int i = 0; i < chunk.size(); i++) {
                if (i > 0) c.append(',');
                c.append("{\"id\":").append(chunk.get(i)).append('}');
            }
            c.append(']');
            JsonObject payload = new JsonObject();
            payload.addProperty("c", c.toString());
            JsonObject json = post("/weapi/v3/song/detail", payload);
            result.addAll(JsonUtil.songs(json.getAsJsonArray("songs")));
        }
        return result;
    }

    @Override
    public Models.SongUrl songUrl(long id) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("ids", "[" + id + "]");
        payload.addProperty("level", "standard");
        payload.addProperty("encodeType", "mp3");
        JsonObject json = post("/weapi/song/enhance/player/url/v1", payload);
        JsonArray data = json.getAsJsonArray("data");
        if (data == null || data.isEmpty()) {
            throw new ApiException(JsonUtil.code(json), JsonUtil.message(json));
        }
        JsonObject object = data.get(0).getAsJsonObject();
        String url = JsonUtil.str(object, "url");
        String type = JsonUtil.str(object, "type");
        long size = JsonUtil.lng(object, "size");
        int fee = JsonUtil.integer(object, 0, "fee");
        boolean trial = object.has("freeTrialInfo") && !object.get("freeTrialInfo").isJsonNull();
        if (url.isBlank()) {
            return new Models.SongUrl("", type, size, fee, trial,
                    "该歌曲暂无可用音源，可能需要会员或已下架");
        }
        return new Models.SongUrl(url, type, size, fee, trial, "");
    }

    @Override
    public Models.Lyrics lyrics(long id) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("id", id);
        payload.addProperty("lv", -1);
        payload.addProperty("tv", -1);
        payload.addProperty("rv", -1);
        payload.addProperty("kv", -1);
        JsonObject json = post("/weapi/song/lyric", payload);
        return JsonUtil.lyrics(json);
    }

    @Override
    public Models.UserProfile loginStatus() throws Exception {
        JsonObject json = post("/weapi/w/nuser/account/get", new JsonObject());
        Models.UserProfile profile = JsonUtil.profile(json);
        if (profile != null) {
            uid = profile.id();
        } else {
            uid = 0;
        }
        return profile;
    }

    @Override
    public Models.QrSession qrCreate() throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", 3);
        JsonObject json = post("/weapi/login/qrcode/unikey", payload);
        String key = JsonUtil.str(json, "unikey", "key");
        if (key.isBlank()) {
            throw new ApiException(JsonUtil.code(json), JsonUtil.message(json));
        }
        return new Models.QrSession(key, "https://music.163.com/login?codekey=" + key, "");
    }

    @Override
    public Models.QrStatus qrCheck(String key) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("key", key);
        payload.addProperty("type", 3);
        JsonObject json = post("/weapi/login/qrcode/client/login", payload, true);
        int code = JsonUtil.code(json);
        String message = JsonUtil.message(json);
        Models.UserProfile profile = null;
        if (code == 803) {
            profile = loginStatus();
        }
        return new Models.QrStatus(code, message, profile);
    }

    @Override
    public Models.AuthResult loginCellphone(String phone, String password, String countryCode) throws Exception {
        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            return new Models.AuthResult(false, null, "请输入手机号和密码");
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "1");
        payload.addProperty("https", "true");
        payload.addProperty("phone", phone.trim());
        payload.addProperty("countrycode", countryCode == null || countryCode.isBlank() ? "86" : countryCode.trim());
        payload.addProperty("password", WeApi.md5(password));
        payload.addProperty("remember", "true");
        payload.addProperty("rememberLogin", "true");

        JsonObject json;
        try {
            json = post("/weapi/w/login/cellphone", payload);
        } catch (ApiException e) {
            json = post("/weapi/login/cellphone", payload);
        }
        int code = JsonUtil.code(json);
        if (code != 200) {
            String message = JsonUtil.message(json);
            if (message.isBlank()) message = "登录失败，请检查账号、密码或验证码";
            return new Models.AuthResult(false, null, message);
        }
        Models.UserProfile profile = loginStatus();
        if (profile == null) profile = JsonUtil.profile(json);
        return new Models.AuthResult(profile != null, profile,
                profile == null ? "登录成功，但读取账号资料失败" : "登录成功");
    }

    @Override
    public Models.AuthResult loginCookie(String cookie) throws Exception {
        if (cookie == null || cookie.isBlank()) {
            return new Models.AuthResult(false, null, "Cookie 为空");
        }
        importCookies(cookie);
        Models.UserProfile profile = loginStatus();
        if (profile == null) {
            return new Models.AuthResult(false, null, "Cookie 无效或已过期");
        }
        return new Models.AuthResult(true, profile, "登录成功");
    }

    @Override
    public void logout() {
        try {
            post("/weapi/logout", new JsonObject());
        } catch (Exception ignored) {
        }
        resetAnonymousCookies();
        uid = 0;
    }

    @Override
    public String exportCookie() {
        StringBuilder sb = new StringBuilder();
        for (var entry : cookies.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    @Override
    public void restoreCookie(String cookie) {
        if (cookie != null && !cookie.isBlank()) importCookies(cookie);
    }

    public boolean isLoggedIn() {
        String musicU = cookies.get("MUSIC_U");
        return musicU != null && !musicU.isEmpty();
    }

    private void importCookies(String header) {
        for (String part : header.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String name = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (name.isEmpty()) continue;
            if (value.isEmpty()) cookies.remove(name);
            else cookies.put(name, value);
        }
    }

    private JsonObject post(String path, JsonObject payload) throws Exception {
        return post(path, payload, false);
    }

    private JsonObject post(String path, JsonObject payload, boolean allowBusinessError) throws Exception {
        String csrf = cookies.getOrDefault("__csrf", "");
        String url = BASE + path + (path.contains("?") ? "&" : "?") + "csrf_token="
                + URLEncoder.encode(csrf, StandardCharsets.UTF_8);
        Map<String, String> form = WeApi.encrypt(payload);
        Http.Response response = Http.postFormFull(url, form, headers());
        updateCookies(response.headers());
        JsonObject json = JsonUtil.parse(response.body());
        int code = JsonUtil.code(json);
        if (!allowBusinessError && code != 200) {
            throw new ApiException(code, JsonUtil.message(json));
        }
        return json;
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Referer", "https://music.163.com/");
        headers.put("Origin", "https://music.163.com");
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Cookie", exportCookie());
        return headers;
    }

    private void updateCookies(Map<String, List<String>> headers) {
        if (headers == null) return;
        for (var entry : headers.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase("set-cookie")) continue;
            for (String value : entry.getValue()) {
                if (value == null) continue;
                String[] pieces = value.split(";");
                if (pieces.length == 0) continue;
                int eq = pieces[0].indexOf('=');
                if (eq <= 0) continue;
                String name = pieces[0].substring(0, eq).trim();
                String cookieValue = pieces[0].substring(eq + 1).trim();
                boolean expired = false;
                for (int i = 1; i < pieces.length; i++) {
                    String p = pieces[i].trim();
                    if (p.regionMatches(true, 0, "Max-Age=", 0, 8)) {
                        try {
                            if (Integer.parseInt(p.substring(8).trim()) <= 0) expired = true;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                if (expired || cookieValue.isEmpty()) cookies.remove(name);
                else cookies.put(name, cookieValue);
            }
        }
    }

    private List<Models.Playlist> userPlaylists() throws Exception {
        if (uid <= 0) return List.of();
        JsonObject payload = new JsonObject();
        payload.addProperty("uid", uid);
        payload.addProperty("limit", 100);
        payload.addProperty("offset", 0);
        payload.addProperty("includeVideo", true);
        JsonObject json = post("/weapi/user/playlist", payload);
        return JsonUtil.playlists(json.getAsJsonArray("playlist"));
    }

    private static String hex(int bytes) {
        byte[] data = new byte[bytes];
        RANDOM.nextBytes(data);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String randomLetters(int count) {
        String alphabet = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return sb.toString();
    }
}
