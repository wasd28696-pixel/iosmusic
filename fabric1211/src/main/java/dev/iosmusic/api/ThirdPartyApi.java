package dev.iosmusic.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Adapter for the widely deployed NeteaseCloudMusicApi HTTP service. Both the classic Binaryify
 * routes and the newer "enhanced" routes are accepted, with graceful fallbacks.
 */
public final class ThirdPartyApi implements MusicApi {
    private static final Logger LOG = Logger.getLogger("iOSMusic");
    private final String base;
    private final Map<String, String> extraHeaders;
    private volatile String cookie = "";
    private volatile long uid;

    public ThirdPartyApi(String base, String headerBlock) {
        this.base = Http.normalizeBase(base);
        this.extraHeaders = parseHeaders(headerBlock);
    }

    @Override
    public String backendName() {
        return "第三方 API";
    }

    public String baseUrl() {
        return base;
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
            JsonObject json = getJson("/personalized", Map.of("limit", "12"));
            recommended = JsonUtil.playlists(json.getAsJsonArray("result"));
            if (recommended.isEmpty()) recommended = JsonUtil.playlists(json.getAsJsonArray("data"));
        } catch (Exception e) {
            firstError = e;
            LOG.warning("Third-party personalized failed: " + e);
        }

        try {
            JsonObject json = getJson("/toplist", Map.of());
            toplists = JsonUtil.playlists(json.getAsJsonArray("list"));
            if (toplists.isEmpty()) toplists = JsonUtil.playlists(json.getAsJsonArray("data"));
        } catch (Exception e) {
            if (firstError == null) firstError = e;
            LOG.warning("Third-party toplist failed: " + e);
        }

        try {
            JsonObject json = getJson("/personalized/newsong", Map.of("limit", "12"));
            newSongs = parseNewsongs(json);
        } catch (Exception e) {
            if (firstError == null) firstError = e;
            LOG.warning("Third-party new songs failed: " + e);
        }

        if (isLoggedIn()) {
            try {
                JsonObject json = getJson("/recommend/songs", Map.of());
                JsonObject data = JsonUtil.obj(json, "data");
                daily = JsonUtil.songs(JsonUtil.arr(data, "dailySongs"));
            } catch (Exception e) {
                LOG.warning("Third-party daily songs failed: " + e);
            }
            try {
                userPlaylists = userPlaylists();
            } catch (Exception e) {
                LOG.warning("Third-party user playlists failed: " + e);
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
        Map<String, String> params = new LinkedHashMap<>();
        params.put("keywords", keywords);
        params.put("type", "1");
        params.put("limit", String.valueOf(Math.max(1, limit)));
        params.put("offset", String.valueOf(Math.max(0, offset)));
        JsonObject json;
        try {
            json = getJson("/cloudsearch", params);
        } catch (Exception first) {
            json = getJson("/search", params);
        }
        JsonObject result = JsonUtil.obj(json, "result");
        if (result == null) throw new ApiException(JsonUtil.code(json), JsonUtil.message(json));
        List<Models.Song> songs = JsonUtil.songs(JsonUtil.arr(result, "songs"));
        int total = JsonUtil.integer(result, songs.size(), "songCount", "count");
        return new Models.SearchResult(List.copyOf(songs), total);
    }

    @Override
    public Models.PlaylistDetail playlist(long id) throws Exception {
        JsonObject json = getJson("/playlist/detail", Map.of("id", String.valueOf(id)));
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
        for (int start = 0; start < ids.size(); start += 400) {
            List<Long> chunk = ids.subList(start, Math.min(ids.size(), start + 400));
            StringBuilder idList = new StringBuilder();
            for (Long id : chunk) {
                if (idList.length() > 0) idList.append(',');
                idList.append(id);
            }
            JsonObject json = getJson("/song/detail", Map.of("ids", idList.toString()));
            result.addAll(JsonUtil.songs(json.getAsJsonArray("songs")));
        }
        return result;
    }

    @Override
    public Models.SongUrl songUrl(long id) throws Exception {
        JsonObject json;
        try {
            json = getJson("/song/url/v1", Map.of("id", String.valueOf(id), "level", "standard"));
        } catch (Exception first) {
            json = getJson("/song/url", Map.of("id", String.valueOf(id), "br", "128000"));
        }
        JsonObject object = firstData(json);
        String url = object == null ? "" : JsonUtil.str(object, "url", "proxyUrl");
        if (url.isBlank()) {
            json = getJson("/song/url", Map.of("id", String.valueOf(id), "br", "128000"));
            object = firstData(json);
            url = object == null ? "" : JsonUtil.str(object, "url", "proxyUrl");
        }
        if (object != null && !url.isBlank()) {
            String type = JsonUtil.str(object, "type", "encodeType");
            long size = JsonUtil.lng(object, "size");
            int fee = JsonUtil.integer(object, 0, "fee");
            boolean trial = object.has("freeTrialInfo") && !object.get("freeTrialInfo").isJsonNull();
            return new Models.SongUrl(url, type, size, fee, trial, "");
        }
        String message = JsonUtil.message(json);
        return new Models.SongUrl("", "", 0, 0, false,
                message.isBlank() ? "第三方接口没有返回可用音源" : message);
    }

    @Override
    public Models.Lyrics lyrics(long id) throws Exception {
        return JsonUtil.lyrics(getJson("/lyric", Map.of("id", String.valueOf(id))));
    }

    @Override
    public Models.UserProfile loginStatus() throws Exception {
        JsonObject json = getJson("/login/status", Map.of(), true);
        JsonObject data = JsonUtil.obj(json, "data");
        Models.UserProfile profile = JsonUtil.profile(json);
        if (profile == null && data != null) profile = JsonUtil.profile(data);
        if (profile != null) uid = profile.id();
        return profile;
    }

    @Override
    public Models.QrSession qrCreate() throws Exception {
        JsonObject keyJson = getJson("/login/qr/key",
                Map.of("timestamp", String.valueOf(System.currentTimeMillis())));
        JsonObject data = JsonUtil.obj(keyJson, "data");
        String key = data == null ? "" : JsonUtil.str(data, "unikey", "key");
        if (key.isBlank()) throw new ApiException(JsonUtil.code(keyJson), JsonUtil.message(keyJson));
        String url = "https://music.163.com/login?codekey=" + key;
        String image = "";
        try {
            JsonObject create = getJson("/login/qr/create",
                    Map.of("key", key, "qrimg", "true",
                            "timestamp", String.valueOf(System.currentTimeMillis())));
            JsonObject createData = JsonUtil.obj(create, "data");
            if (createData != null) {
                String returned = JsonUtil.str(createData, "qrurl");
                if (!returned.isBlank()) url = returned;
                image = JsonUtil.str(createData, "qrimg");
            }
        } catch (Exception ignored) {
        }
        return new Models.QrSession(key, url, image);
    }

    @Override
    public Models.QrStatus qrCheck(String key) throws Exception {
        JsonObject json = getJson("/login/qr/check",
                Map.of("key", key, "timestamp", String.valueOf(System.currentTimeMillis())), true);
        int code = JsonUtil.code(json);
        String message = JsonUtil.message(json);
        String returnedCookie = JsonUtil.str(json, "cookie");
        if (!returnedCookie.isBlank()) cookie = returnedCookie;
        Models.UserProfile profile = null;
        if (code == 803) {
            profile = loginStatus();
            if (profile == null) profile = JsonUtil.profile(json);
        }
        return new Models.QrStatus(code, message, profile);
    }

    @Override
    public Models.AuthResult loginCellphone(String phone, String password, String countryCode) throws Exception {
        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            return new Models.AuthResult(false, null, "请输入手机号和密码");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("phone", phone.trim());
        params.put("md5_password", WeApi.md5(password));
        params.put("countrycode", countryCode == null || countryCode.isBlank() ? "86" : countryCode.trim());
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        JsonObject json = getJson("/login/cellphone", params, true);
        int code = JsonUtil.code(json);
        if (code != 200) {
            String message = JsonUtil.message(json);
            return new Models.AuthResult(false, null,
                    message.isBlank() ? "登录失败，请检查账号或密码" : message);
        }
        String returned = JsonUtil.str(json, "cookie");
        if (!returned.isBlank()) cookie = returned;
        Models.UserProfile profile = loginStatus();
        if (profile == null) profile = JsonUtil.profile(json);
        return new Models.AuthResult(profile != null, profile,
                profile == null ? "登录成功，但读取账号资料失败" : "登录成功");
    }

    @Override
    public Models.AuthResult loginCookie(String cookie) throws Exception {
        if (cookie == null || cookie.isBlank()) return new Models.AuthResult(false, null, "Cookie 为空");
        this.cookie = cookie.trim();
        Models.UserProfile profile = loginStatus();
        if (profile == null) return new Models.AuthResult(false, null, "Cookie 无效或已过期");
        return new Models.AuthResult(true, profile, "登录成功");
    }

    @Override
    public void logout() {
        cookie = "";
        uid = 0;
    }

    @Override
    public String exportCookie() {
        return cookie;
    }

    @Override
    public void restoreCookie(String cookie) {
        this.cookie = cookie == null ? "" : cookie.trim();
    }

    private boolean isLoggedIn() {
        return cookie != null && !cookie.isBlank();
    }

    private List<Models.Playlist> userPlaylists() throws Exception {
        JsonObject json = getJson("/user/playlist", Map.of("uid", String.valueOf(uid), "limit", "100"));
        return JsonUtil.playlists(json.getAsJsonArray("playlist"));
    }

    private List<Models.Song> parseNewsongs(JsonObject json) {
        List<Models.Song> songs = new ArrayList<>();
        JsonArray array = json.getAsJsonArray("result");
        if (array == null) array = json.getAsJsonArray("data");
        if (array == null) return songs;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            Models.Song song = JsonUtil.song(element.getAsJsonObject());
            if (song != null) songs.add(song);
        }
        return songs;
    }

    private static JsonObject firstData(JsonObject json) {
        JsonArray data = json.getAsJsonArray("data");
        if (data == null || data.isEmpty()) return null;
        JsonElement element = data.get(0);
        return element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private JsonObject getJson(String path, Map<String, String> params) throws Exception {
        return getJson(path, params, false);
    }

    private JsonObject getJson(String path, Map<String, String> params, boolean allowBusinessError) throws Exception {
        Map<String, String> query = new LinkedHashMap<>(params);
        if (cookie != null && !cookie.isBlank()) query.put("cookie", cookie);
        query.put("timestamp", String.valueOf(System.currentTimeMillis()));
        String url = Http.appendQuery(base + path, query);
        Http.Response response = Http.getFull(url, headers());
        JsonObject json = JsonUtil.parse(response.body());
        int code = JsonUtil.code(json);
        if (!allowBusinessError && code != 200) {
            throw new ApiException(code, JsonUtil.message(json));
        }
        return json;
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "iOSMusic/1.0 (Minecraft NeoForge)");
        headers.put("Accept", "application/json, text/plain, */*");
        if (cookie != null && !cookie.isBlank()) headers.put("Cookie", cookie);
        headers.putAll(extraHeaders);
        return headers;
    }

    private static Map<String, String> parseHeaders(String block) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (block == null) return headers;
        for (String line : block.split("\\R")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (!name.isEmpty() && !value.isEmpty()) headers.put(name, value);
        }
        return headers;
    }
}
