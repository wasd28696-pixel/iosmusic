package dev.iosmusic.api;

import dev.iosmusic.ModConfig;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Application-level facade: chooses the backend, serializes the login session and exposes
 * asynchronous operations for the screen.
 */
public final class ApiClient {
    private static final Logger LOG = Logger.getLogger("iOSMusic");
    private static final ApiClient INSTANCE = new ApiClient();
    private static final ExecutorService IO = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "iOSMusic-IO-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    private volatile MusicApi api;
    private volatile String effectiveMode = "OFFICIAL";
    private volatile String backend = "";
    private volatile Models.UserProfile profile;
    private volatile boolean loggedIn;
    private volatile SessionStore.Saved saved = new SessionStore.Saved();
    private volatile String lastError = "";

    private ApiClient() {
    }

    public static ApiClient get() {
        return INSTANCE;
    }

    public synchronized void init() {
        if (api != null) return;
        ModConfig config = ModConfig.get();
        saved = SessionStore.load();
        effectiveMode = chooseMode(config, true);
        api = createApi(config, effectiveMode);
        backend = api.backendName();
        if (saved.cookie != null && saved.mode.equals(effectiveMode) && !saved.cookie.isBlank()) {
            try {
                api.restoreCookie(saved.cookie);
            } catch (Exception e) {
                LOG.warning("Could not restore session: " + e);
            }
            if (saved.loggedIn) {
                profile = new Models.UserProfile(saved.uid, saved.nickname, saved.avatar, saved.vip, "", saved.level);
                loggedIn = true;
                if (config.autoLogin) refreshLoginStatus(true);
            }
        }
    }

    public synchronized void reload() {
        ModConfig config = ModConfig.get();
        saved = SessionStore.load();
        effectiveMode = chooseMode(config, false);
        api = createApi(config, effectiveMode);
        backend = api.backendName();
        profile = null;
        loggedIn = false;
        if (saved.cookie != null && saved.mode.equals(effectiveMode) && !saved.cookie.isBlank()) {
            api.restoreCookie(saved.cookie);
            if (saved.loggedIn) {
                profile = new Models.UserProfile(saved.uid, saved.nickname, saved.avatar, saved.vip, "", saved.level);
                loggedIn = true;
                refreshLoginStatus(true);
            }
        }
    }

    public synchronized void setMode(String mode) {
        ModConfig config = ModConfig.get();
        config.apiMode = mode;
        config.save();
        reload();
    }

    public MusicApi api() {
        if (api == null) init();
        return api;
    }

    public String backend() {
        return backend;
    }

    public String effectiveMode() {
        return effectiveMode;
    }

    public Models.UserProfile profile() {
        return profile;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public String lastError() {
        return lastError;
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                throw new CompletionException(e);
            }
        }, IO);
    }

    public CompletableFuture<Models.HomeData> home() {
        return submit(() -> api().home());
    }

    public CompletableFuture<Models.SearchResult> search(String keywords, int limit, int offset) {
        return submit(() -> api().search(keywords, limit, offset));
    }

    public CompletableFuture<Models.PlaylistDetail> playlist(long id) {
        return submit(() -> api().playlist(id));
    }

    public CompletableFuture<List<Models.Song>> songDetails(List<Long> ids) {
        return submit(() -> api().songDetails(ids));
    }

    public CompletableFuture<Models.SongUrl> songUrl(long id) {
        return submit(() -> api().songUrl(id));
    }

    public CompletableFuture<Models.Lyrics> lyrics(long id) {
        return submit(() -> api().lyrics(id));
    }

    public CompletableFuture<Models.UserProfile> loginStatus() {
        return submit(() -> {
            Models.UserProfile result = api().loginStatus();
            if (result != null) onAuthenticated(result);
            else clearLoginState();
            return result;
        });
    }

    public CompletableFuture<Models.QrSession> qrCreate() {
        return submit(() -> api().qrCreate());
    }

    public CompletableFuture<Models.QrStatus> qrCheck(String key) {
        return submit(() -> {
            Models.QrStatus status = api().qrCheck(key);
            if (status.profile() != null) onAuthenticated(status.profile());
            return status;
        });
    }

    public CompletableFuture<Models.AuthResult> loginCellphone(String phone, String password, String countryCode) {
        return submit(() -> {
            Models.AuthResult result = api().loginCellphone(phone, password, countryCode);
            if (result.success() && result.profile() != null) onAuthenticated(result.profile());
            return result;
        });
    }

    public CompletableFuture<Models.AuthResult> loginCookie(String cookie) {
        return submit(() -> {
            Models.AuthResult result = api().loginCookie(cookie);
            if (result.success() && result.profile() != null) onAuthenticated(result.profile());
            return result;
        });
    }

    public CompletableFuture<Void> logout() {
        return submit(() -> {
            try {
                api().logout();
            } catch (Exception ignored) {
            }
            clearLoginState();
            SessionStore.clear();
            return null;
        });
    }

    private void refreshLoginStatus(boolean quiet) {
        submit(() -> api().loginStatus()).whenComplete((result, error) -> {
            if (error != null) {
                if (!quiet) LOG.warning("Login check failed: " + error);
                return;
            }
            if (result == null) clearLoginState();
            else onAuthenticated(result);
        });
    }

    private synchronized void onAuthenticated(Models.UserProfile newProfile) {
        profile = newProfile;
        loggedIn = true;
        saved.mode = effectiveMode;
        saved.cookie = api().exportCookie();
        saved.uid = newProfile.id();
        saved.nickname = newProfile.nickname();
        saved.avatar = newProfile.avatarUrl();
        saved.vip = newProfile.vipType();
        saved.level = newProfile.level();
        saved.loggedIn = true;
        SessionStore.save(saved);
    }

    private synchronized void clearLoginState() {
        profile = null;
        loggedIn = false;
        if (saved != null) {
            saved.loggedIn = false;
            saved.cookie = "";
            SessionStore.save(saved);
        }
    }

    private MusicApi createApi(ModConfig config, String mode) {
        if ("THIRD_PARTY".equals(mode)) {
            return new ThirdPartyApi(config.thirdPartyBaseUrl, config.thirdPartyHeaders);
        }
        return new OfficialApi();
    }

    private String chooseMode(ModConfig config, boolean probe) {
        String configured = config.apiMode;
        if ("OFFICIAL".equals(configured) || "THIRD_PARTY".equals(configured)) return configured;
        if (saved != null && "THIRD_PARTY".equals(saved.mode)) return "THIRD_PARTY";
        if (probe && Http.reachable(config.thirdPartyBaseUrl, 850)) return "THIRD_PARTY";
        return "OFFICIAL";
    }
}
