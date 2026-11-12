package dev.iosmusic.ui;

import dev.iosmusic.ModConfig;
import dev.iosmusic.MusicPlayer;
import dev.iosmusic.api.ApiClient;
import dev.iosmusic.api.Models;
import dev.iosmusic.together.TogetherClient;
import dev.iosmusic.together.TogetherMessage;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.function.IntConsumer;

/**
 * The whole player surface. Layout is deliberately fluid: the sidebar and now-playing rail collapse
 * on small GUI scales while the glass surfaces keep their proportions.
 */
public class MusicScreen extends Screen {
    private enum Page {HOME, SEARCH, LIBRARY, SETTINGS, TOGETHER, PLAYLIST}

    private enum LoginTab {QR, PHONE, COOKIE}

    record Layout(float x, float y, float w, float h, float sidebarW,
                  float mainX, float mainY, float mainW, float mainH,
                  float rightX, float rightW) {
    }

    private record Hit(float x, float y, float w, float h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static final class Drag {
        final float x;
        final float w;
        final int min;
        final int max;
        final IntConsumer setter;
        final Runnable release;

        Drag(float x, float w, int min, int max, IntConsumer setter, Runnable release) {
            this.x = x;
            this.w = w;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.release = release;
        }
    }

    private final MusicPlayer player = MusicPlayer.get();
    private final ApiClient api = ApiClient.get();
    private final List<Hit> hits = new ArrayList<>();

    private Page page = Page.HOME;
    private Page playlistBack = Page.HOME;
    private Layout lastLayout;
    private boolean stateRestored;
    private float pendingMainScroll = -1;
    private float pendingSidebarScroll = -1;
    private float pendingNavScroll = -1;

    private static Page savedPage = Page.HOME;
    private static Page savedPlaylistBack = Page.HOME;
    private static long savedPlaylistId;
    private static float savedScroll;
    private static float savedSidebarScroll;
    private static float savedNavScroll;
    private static float savedDividerShift;
    private static boolean savedValid;

    private Models.HomeData home;
    private boolean homeLoading;
    private String homeError = "";
    private long homeToken;

    private Models.SearchResult searchResult;
    private boolean searchLoading;
    private String searchError = "";
    private String lastQuery = "";

    private Models.PlaylistDetail playlistDetail;
    private boolean playlistLoading;
    private String playlistError = "";
    private long playlistToken;
    private long requestedPlaylistId;

    private EditBox searchBox;
    private EditBox phoneBox;
    private EditBox passwordBox;
    private EditBox cookieBox;
    private EditBox apiUrlBox;
    private EditBox focusedField;

    private boolean loginOpen;
    private LoginTab loginTab = LoginTab.QR;
    private boolean loginBusy;
    private String loginError = "";
    private String qrStatus = "";
    private String qrKey = "";
    private ResourceLocation qrTexture;
    private long qrGeneration;
    private boolean qrPolling;
    private boolean qrBusy;

    private boolean lyricsOpen;
    private float lyricsScroll;
    private float sidebarDividerShift;
    private boolean dividerDragging;
    private float dividerDragStartY;
    private float dividerDragStartShift;
    private float dividerMinShift;
    private float dividerMaxShift;
    private float navScroll;
    private float navTargetScroll;
    private float navMaxScroll;
    private float navClipTop;
    private float navClipBottom;
    private float lastDividerY;
    private long lastNavFrame;
    private boolean navBarDragging;
    private float navBarDragStartY;
    private float navBarDragStartScroll;
    private float navBarTrackTop;
    private float navBarTrackHeight;
    private float lyricsTargetScroll;
    private long lyricsManualUntil;

    private boolean seeking;
    private double seekFraction;
    private Drag drag;
    private int mouseX;
    private int mouseY;

    private float scroll;
    private float targetScroll;
    private float maxScroll;
    private float viewportTop;
    private float viewportBottom;
    private boolean contentRendering;
    private float sidebarScroll;
    private float sidebarTargetScroll;
    private float sidebarMaxScroll;

    private long openAt;
    private long pageChangedAt;
    private long lastFrameAt;
    private String toastText = "";
    private long toastAt;

    public MusicScreen() {
        super(Component.literal("iOS Music"));
        this.openAt = Util.getMillis();
        this.pageChangedAt = openAt;
    }

    @Override
    protected void init() {
        this.font = UiFonts.get();
        api.init();
        player.setToast(this::toast);
        if (searchBox == null) {
            searchBox = field("搜索歌曲、歌手或歌单", 80, false);
            phoneBox = field("手机号", 11, false);
            phoneBox.setFilter(value -> value.matches("\\d*"));
            passwordBox = field("密码", 64, true);
            cookieBox = field("MUSIC_U=...; __csrf=...", 8192, false);
            apiUrlBox = field("http://127.0.0.1:3000", 300, false);
            apiUrlBox.setValue(ModConfig.get().thirdPartyBaseUrl);
        }
        if (home == null && !homeLoading) loadHome(false);
        if (!stateRestored) {
            stateRestored = true;
            if (savedValid) {
                if (savedPage == Page.PLAYLIST && savedPlaylistId > 0) {
                    page = Page.PLAYLIST;
                    playlistBack = savedPlaylistBack == Page.PLAYLIST ? Page.HOME : savedPlaylistBack;
                    scroll = targetScroll = savedScroll;
                    sidebarScroll = sidebarTargetScroll = savedSidebarScroll;
                    navScroll = navTargetScroll = savedNavScroll;
                    sidebarDividerShift = savedDividerShift;
                    pendingMainScroll = savedScroll;
                    pendingSidebarScroll = savedSidebarScroll;
                    pendingNavScroll = savedNavScroll;
                    pageChangedAt = Util.getMillis() - 1000;
                    openPlaylist(savedPlaylistId);
                } else if (savedPage != Page.PLAYLIST) {
                    page = savedPage;
                    scroll = targetScroll = savedScroll;
                    sidebarScroll = sidebarTargetScroll = savedSidebarScroll;
                    navScroll = navTargetScroll = savedNavScroll;
                    sidebarDividerShift = savedDividerShift;
                    pendingMainScroll = savedScroll;
                    pendingSidebarScroll = savedSidebarScroll;
                    pendingNavScroll = savedNavScroll;
                    pageChangedAt = Util.getMillis() - 1000;
                }
            }
        }
        openAt = Util.getMillis();
    }

    private EditBox field(String hint, int maxLength, boolean password) {
        EditBox box = new EditBox(font, 0, 0, 200, 20, Component.literal(hint));
        box.setBordered(false);
        box.setMaxLength(maxLength);
        box.setHint(Component.literal(hint));
        box.setTextColor(0xFFFFFFFF);
        box.setCanLoseFocus(true);
        if (password) {
            box.setFormatter((value, cursor) -> Component.literal("•".repeat(value.length())).getVisualOrderText());
        }
        return box;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            this.renderPanorama(graphics, partialTick);
        }
    }

    private void applyMenuBlur(float partialTick) {
        // Kept as a no-op transition point; Gaussian panel blur is handled by GlassSnapshot.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        renderBackground(graphics, mouseX, mouseY, partialTick);
        GlassSnapshot.captureSharp();
        GlassSnapshot.captureBlur();
        CoverCache.get().tick();
        GlassTexture.noise();
        hits.clear();
        long now = Util.getMillis();
        float delta = lastFrameAt == 0 ? 0.016f : Math.min(0.1f, (now - lastFrameAt) / 1000f);
        lastFrameAt = now;

        Layout layout = layout();
        lastLayout = layout;
        drawSidebar(graphics, layout, now);
        drawMain(graphics, layout, now, delta);
        if (layout.rightW() > 0) drawNowPlaying(graphics, layout, now);
        drawFloatingControls(graphics, layout);
        if (loginOpen) drawLogin(graphics, layout, now);
        if (lyricsOpen) drawLyrics(graphics, layout, now);
        drawToast(graphics, now);

        // The page content scrolls smoothly and is clamped after its height is known.
        scroll += (targetScroll - scroll) * Math.min(1f, delta * 12f);
        if (Math.abs(targetScroll - scroll) < 0.1f) scroll = targetScroll;
        sidebarScroll += (sidebarTargetScroll - sidebarScroll) * Math.min(1f, delta * 14f);
        if (Math.abs(sidebarTargetScroll - sidebarScroll) < 0.1f) sidebarScroll = sidebarTargetScroll;
        applyPendingScrolls();
        saveState();
    }

    private void applyPendingScrolls() {
        // Data arrives a frame or two after reopening. Keep the saved values separately, otherwise
        // the empty loading state would clamp them back to zero.
        if (pendingMainScroll >= 0 && pageContentReady()) {
            targetScroll = pendingMainScroll;
            scroll = pendingMainScroll;
            pendingMainScroll = -1;
        }
        if (pendingSidebarScroll >= 0 && home != null) {
            sidebarTargetScroll = pendingSidebarScroll;
            sidebarScroll = pendingSidebarScroll;
            pendingSidebarScroll = -1;
        }
        if (pendingNavScroll >= 0) {
            navTargetScroll = pendingNavScroll;
            navScroll = pendingNavScroll;
            pendingNavScroll = -1;
        }
    }

    private boolean pageContentReady() {
        return switch (page) {
            case HOME, LIBRARY -> home != null;
            case SEARCH -> !searchLoading;
            case PLAYLIST -> playlistDetail != null;
            case SETTINGS, TOGETHER -> true;
        };
    }

    private Layout layout() {
        float margin = width < 700 ? 8 : 16;
        float gap = 10;
        // Short windows become compact as well: the full sidebar's five nav rows do not fit below
        // roughly 460 logical pixels.
        boolean compact = width < 840 || height < 460;
        float sidebarW = compact ? 68 : Math.max(174, Math.min(236, width * 0.19f));
        boolean showRight = width >= 900 && height >= 480;
        float rightW = showRight ? Math.max(286, Math.min(336, width * 0.235f)) : 0;
        float mainX = margin + sidebarW + gap;
        float mainW = width - margin - mainX - (rightW > 0 ? rightW + gap : 0);
        float rightX = mainX + mainW + gap;
        return new Layout(margin, margin, mainW, height - margin * 2, sidebarW,
                mainX, margin, mainW, height - margin * 2, rightX, rightW);
    }

    private void drawSidebar(GuiGraphics g, Layout l, long now) {
        float x = l.x();
        float y = l.y();
        float w = l.sidebarW();
        float h = l.h();
        Glass.panel(g, x, y, w, h, 28);
        Glass.noise(g, GlassTexture.noise(), x + 4, y + 4, w - 8, h - 8, 0.08f);
        float openEase = easeOutCubic(Math.min(1f, (now - openAt) / 280f));
        g.pose().pushPose();
        g.pose().translate(-(1f - openEase) * 8f, 0, 0);
        if (w < 110) {
            drawCompactSidebar(g, x, y, w, h);
            g.pose().popPose();
            return;
        }

        float px = x + 17;
        int accent = Theme.accent();
        Glass.rounded(g, px, y + 18, 42, 42, 13, accent, Glass.mix(accent, 0xFF000000, 0.38f));
        Icons.draw(g, Icons.Icon.MUSIC, px + 10, y + 28, 22, 0xFFFFFFFF);
        Text.draw(g, font, "Music", px + 53, y + 21, 1.45f, Theme.text());
        Text.draw(g, font, "网易云 · Liquid Glass", px + 54, y + 43, 0.7f, Theme.secondary());
        hit(px, y + 18, 42, 42, () -> switchPage(Page.HOME));

        // Window close control, aligned like a macOS/iOS traffic light.
        float closeX = x + w - 31;
        float closeY = y + 24;
        if (hovered(closeX, closeY, 18, 18)) {
            Glass.circle(g, closeX + 9, closeY + 9, 11, Glass.argb(120, 255, 90, 98));
        }
        Icons.draw(g, Icons.Icon.CLOSE, closeX, closeY, 18, Glass.withAlpha(Theme.text(), 170));
        hit(closeX - 3, closeY - 3, 24, 24, this::onClose);

        float cardY = y + 75;
        drawUserCard(g, px, cardY, w - 34, 55, now);

        float navStart = cardY + 68;
        float navHeight = 39f * 5f;
        float dividerBase = navStart + navHeight + 15;
        dividerMinShift = -(navHeight - 78);
        dividerMaxShift = Math.max(dividerMinShift, (y + h - 58) - (dividerBase + 31));
        sidebarDividerShift = Math.max(dividerMinShift, Math.min(dividerMaxShift, sidebarDividerShift));
        float dividerY = dividerBase + sidebarDividerShift;
        lastDividerY = dividerY;
        navClipTop = navStart;
        navClipBottom = dividerY;
        navMaxScroll = Math.max(0, navHeight - (dividerY - navStart));
        navTargetScroll = Math.max(0, Math.min(navMaxScroll, navTargetScroll));
        navScroll = Math.max(0, Math.min(navMaxScroll, navScroll));
        long navNow = Util.getMillis();
        float navDelta = lastNavFrame == 0 ? 0.016f : Math.min(0.1f, (navNow - lastNavFrame) / 1000f);
        lastNavFrame = navNow;
        // Use the same smoothing as the playlist column so both parts of the sidebar move at one
        // unified speed.
        navScroll += (navTargetScroll - navScroll) * Math.min(1f, navDelta * 14f);
        if (Math.abs(navTargetScroll - navScroll) < 0.1f) navScroll = navTargetScroll;

        g.enableScissor((int) x, (int) navStart, (int) (x + w), (int) dividerY);
        float navY = navStart - navScroll;
        navY = navItem(g, px, navY, w - 34, Icons.Icon.HOME, "首页", Page.HOME);
        navY = navItem(g, px, navY, w - 34, Icons.Icon.SEARCH, "搜索", Page.SEARCH);
        navY = navItem(g, px, navY, w - 34, Icons.Icon.LIBRARY, "音乐库", Page.LIBRARY);
        navY = navItem(g, px, navY, w - 34, Icons.Icon.SETTINGS, "设置", Page.SETTINGS);
        navItem(g, px, navY, w - 34, Icons.Icon.TOGETHER, "一起听", Page.TOGETHER);
        g.disableScissor();

        if (navMaxScroll > 0.5f) {
            float trackH = Math.max(16, dividerY - navStart - 4);
            float thumbH = Math.max(14, trackH * (dividerY - navStart) / navHeight);
            float thumbY = navStart + 2 + (trackH - thumbH) * (navScroll / navMaxScroll);
            navBarTrackTop = navStart + 2;
            navBarTrackHeight = trackH;
            Glass.rounded(g, x + w - 9, navStart + 2, 2.5f, trackH, 1.25f,
                    Glass.withAlpha(0xFFFFFFFF, 12));
            Glass.rounded(g, x + w - 9, thumbY, 2.5f, thumbH, 1.25f,
                    Glass.withAlpha(0xFFFFFFFF, navBarDragging ? 120 : 74));
            hit(x + w - 14, navStart, 12, dividerY - navStart, () -> {
                navBarDragging = true;
                navBarDragStartY = mouseY;
                navBarDragStartScroll = navScroll;
            });
        }

        Glass.line(g, px, dividerY, px + w - 34, dividerY, 1f, Theme.faint());
        for (int i = -1; i <= 1; i++) {
            Glass.circle(g, px + (w - 34) / 2f + i * 5, dividerY, 1.4f,
                    dividerDragging ? Theme.accent() : Theme.muted());
        }
        hit(px, dividerY - 8, w - 34, 18, () -> {
            dividerDragging = true;
            dividerDragStartY = mouseY;
            dividerDragStartShift = sidebarDividerShift;
        });
        navY = dividerY + 13;
        Text.draw(g, font, "我的歌单", px + 2, navY, 0.76f, Theme.secondary());
        navY += 18;

        List<Models.Playlist> lists = new ArrayList<>();
        if (home != null) {
            lists.addAll(home.userPlaylists());
            if (lists.isEmpty()) lists.addAll(home.recommended());
        }
        float listTop = navY;
        float listBottom = y + h - 58;
        sidebarMaxScroll = Math.max(0, lists.size() * 32 - (listBottom - listTop));
        sidebarTargetScroll = Math.max(0, Math.min(sidebarMaxScroll, sidebarTargetScroll));
        sidebarScroll = Math.max(0, Math.min(sidebarMaxScroll, sidebarScroll));
        g.enableScissor((int) x, (int) listTop, (int) (x + w), (int) listBottom);
        for (int i = 0; i < lists.size(); i++) {
            Models.Playlist playlist = lists.get(i);
            float rowY = listTop + i * 32 - sidebarScroll;
            if (rowY + 30 < listTop || rowY > listBottom) continue;
            boolean selectedRow = page == Page.PLAYLIST && requestedPlaylistId == playlist.id();
            boolean rowHovered = hovered(px - 5, rowY, w - 24, 30);
            if (selectedRow || rowHovered) {
                Glass.rounded(g, px - 5, rowY, w - 24, 30, 9,
                        selectedRow ? Glass.withAlpha(Theme.accent(), 30) : Glass.withAlpha(0xFFFFFFFF, 20),
                        selectedRow ? Glass.withAlpha(Theme.accent(), 12) : Glass.withAlpha(0xFFFFFFFF, 8));
                if (selectedRow) {
                    Glass.rounded(g, px - 5, rowY + 7, 3, 16, 1.5f, Theme.accent());
                }
            }
            cover(g, playlist.coverUrl(), px + 1, rowY + 4, 23, 7);
            Text.draw(g, font, Text.ellipsize(font, playlist.name(), w - 82, 0.82f),
                    px + 31, rowY + 7, 0.82f, selectedRow ? Theme.accent() : Theme.text());
            Text.draw(g, font, playlist.trackCount() > 0 ? playlist.trackCount() + " 首" : "歌单",
                    px + 31, rowY + 18, 0.64f, Theme.muted());
            hit(px - 5, rowY, w - 24, 30, () -> openPlaylist(playlist.id()));
        }
        g.disableScissor();
        if (sidebarMaxScroll > 0.5f) {
            float trackH = listBottom - listTop - 4;
            float thumbH = Math.max(22, trackH * ((listBottom - listTop) / (lists.size() * 32f)));
            float thumbY = listTop + 2 + (trackH - thumbH) * (sidebarScroll / sidebarMaxScroll);
            Glass.rounded(g, x + w - 7, listTop + 2, 2.5f, trackH, 1.25f,
                    Glass.withAlpha(0xFFFFFFFF, 12));
            Glass.rounded(g, x + w - 7, thumbY, 2.5f, thumbH, 1.25f,
                    Glass.withAlpha(0xFFFFFFFF, 74));
        }

        float chipY = y + h - 39;
        Glass.rounded(g, px, chipY, w - 34, 25, 12, Glass.withAlpha(0xFFFFFFFF, 17),
                Glass.withAlpha(0xFFFFFFFF, 5));
        Glass.circle(g, px + 12, chipY + 12.5f, 3.2f, Theme.success());
        String backend = api.backend();
        Text.draw(g, font, Text.ellipsize(font, backend, w - 72, 0.68f), px + 21, chipY + 8, 0.68f,
                Theme.secondary());
        hit(px, chipY, w - 34, 25, () -> switchPage(Page.SETTINGS));
        g.pose().popPose();
    }

    private void drawCompactSidebar(GuiGraphics g, float x, float y, float w, float h) {
        float iconX = x + 14;
        Glass.rounded(g, iconX, y + 16, 40, 40, 13, Theme.accent(), Glass.mix(Theme.accent(), 0xFF000000, 0.35f));
        Icons.draw(g, Icons.Icon.MUSIC, iconX + 10, y + 26, 20, 0xFFFFFFFF);
        hit(iconX, y + 16, 40, 40, () -> switchPage(Page.HOME));

        float closeX = x + w - 22;
        Glass.circle(g, closeX + 7, y + 23, 11, Glass.withAlpha(0xFFFFFFFF, 10));
        Icons.draw(g, Icons.Icon.CLOSE, closeX, y + 16, 14, Theme.secondary());
        hit(closeX - 5, y + 11, 24, 24, this::onClose);

        Models.UserProfile profile = api.profile();
        Glass.circle(g, x + w / 2f, y + 92, 22, Glass.withAlpha(Theme.accent(), 40));
        if (api.isLoggedIn() && profile != null) {
            avatar(g, profile.avatarUrl(), x + w / 2f - 19, y + 73, 38);
            hit(x + 10, y + 68, w - 20, 48, () -> {
                page = Page.SETTINGS;
                pageChangedAt = Util.getMillis();
            });
        } else {
            Icons.draw(g, Icons.Icon.USER, x + w / 2f - 11, y + 81, 22, 0xFFFFFFFF);
            hit(x + 10, y + 68, w - 20, 48, this::openLogin);
        }

        float navY = y + 134;
        float navBottom = h < 300 ? y + h - 12 : y + h - 50;
        float step = Math.max(18f, Math.min(44f, (navBottom - navY) / 5f));
        navY = compactNav(g, x, navY, w, step, Icons.Icon.HOME, Page.HOME);
        navY = compactNav(g, x, navY, w, step, Icons.Icon.SEARCH, Page.SEARCH);
        navY = compactNav(g, x, navY, w, step, Icons.Icon.LIBRARY, Page.LIBRARY);
        navY = compactNav(g, x, navY, w, step, Icons.Icon.SETTINGS, Page.SETTINGS);
        compactNav(g, x, navY, w, step, Icons.Icon.TOGETHER, Page.TOGETHER);

        if (h >= 300) {
            Glass.circle(g, x + w / 2f, y + h - 30, 3.2f, Theme.success());
            hit(x + 10, y + h - 46, w - 20, 32, () -> switchPage(Page.SETTINGS));
        }
    }

    private float compactNav(GuiGraphics g, float x, float y, float w, float step,
                             Icons.Icon icon, Page target) {
        boolean selected = page == target;
        float h = Math.max(14f, step - 6f);
        boolean hovered = hovered(x + 9, y, w - 18, h);
        if (selected || hovered) {
            Glass.rounded(g, x + 9, y, w - 18, h, 10,
                    selected ? Glass.withAlpha(Theme.accent(), 40) : Glass.withAlpha(0xFFFFFFFF, 14),
                    selected ? Glass.withAlpha(Theme.accent(), 10) : 0);
        }
        float iconSize = Math.max(12f, Math.min(21f, step - 5f));
        Icons.draw(g, icon, x + w / 2f - iconSize / 2f, y + (h - iconSize) / 2f, iconSize,
                selected ? Theme.accent() : Theme.secondary());
        hit(x + 9, y, w - 18, h, () -> switchPage(target));
        return y + step;
    }

    private float compactNavAction(GuiGraphics g, float x, float y, float w, Icons.Icon icon, Runnable action) {
        boolean hovered = hovered(x + 9, y, w - 18, 38);
        if (hovered) {
            Glass.rounded(g, x + 9, y, w - 18, 38, 11, Glass.withAlpha(0xFFFFFFFF, 14), 0);
        }
        Icons.draw(g, icon, x + w / 2f - 11, y + 9, 21, hovered ? Theme.text() : Theme.secondary());
        hit(x + 9, y, w - 18, 38, action);
        return y + 44;
    }

    private void drawUserCard(GuiGraphics g, float x, float y, float w, float h, long now) {
        boolean logged = api.isLoggedIn();
        boolean hovered = hovered(x, y, w, h);
        Glass.rounded(g, x, y, w, h, 14, Glass.withAlpha(0xFFFFFFFF, hovered ? 25 : 15),
                Glass.withAlpha(0xFFFFFFFF, 4));
        Models.UserProfile profile = api.profile();
        if (logged && profile != null) {
            avatar(g, profile.avatarUrl(), x + 9, y + 10, 34);
            Text.draw(g, font, Text.ellipsize(font, profile.displayName(), w - 92, 0.9f),
                    x + 51, y + 12, 0.9f, Theme.text());
            Text.draw(g, font, profile.vipType() > 0 ? "VIP · 已登录" : "已登录网易云",
                    x + 51, y + 28, 0.64f, profile.vipType() > 0 ? 0xFFFFD36E : Theme.secondary());
            hit(x, y, w, h, () -> {
                page = Page.SETTINGS;
                pageChangedAt = Util.getMillis();
            });
        } else {
            Glass.circle(g, x + 26, y + h / 2f, 16, Glass.withAlpha(Theme.accent(), 42));
            Icons.draw(g, Icons.Icon.USER, x + 15, y + h / 2f - 10, 21, 0xFFFFFFFF);
            Text.draw(g, font, "登录网易云", x + 50, y + 12, 0.83f, Theme.text());
            Text.draw(g, font, "同步歌单与每日推荐", x + 50, y + 27, 0.6f, Theme.muted());
            hit(x, y, w, h, this::openLogin);
        }
    }

    private float navItem(GuiGraphics g, float x, float y, float w, Icons.Icon icon, String label, Page target) {
        boolean selected = page == target;
        boolean hovered = hovered(x - 3, y, w + 6, 34);
        if (selected || hovered) {
            Glass.rounded(g, x - 3, y, w + 6, 34, 10,
                    selected ? Glass.withAlpha(Theme.accent(), 34) : Glass.withAlpha(0xFFFFFFFF, 12),
                    selected ? Glass.withAlpha(Theme.accent(), 12) : 0);
            if (selected) {
                Glass.rounded(g, x - 3, y + 9, 3, 16, 1.5f, Theme.accent());
            }
        }
        Icons.draw(g, icon, x + 10, y + 8, 18, selected ? Theme.accent() : Theme.secondary());
        Text.draw(g, font, label, x + 38, y + 12, 0.9f, selected ? Theme.text() : Theme.secondary());
        if (y + 34 > navClipTop && y < navClipBottom) {
            hit(x - 3, y, w + 6, 34, () -> switchPage(target));
        }
        return y + 39;
    }

    private float navAction(GuiGraphics g, float x, float y, float w, Icons.Icon icon, String label,
                            Runnable action) {
        boolean hovered = hovered(x - 3, y, w + 6, 34);
        if (hovered) {
            Glass.rounded(g, x - 3, y, w + 6, 34, 10,
                    Glass.withAlpha(0xFFFFFFFF, 12), 0);
        }
        Icons.draw(g, icon, x + 10, y + 8, 18, hovered ? Theme.text() : Theme.secondary());
        Text.draw(g, font, label, x + 38, y + 12, 0.9f, hovered ? Theme.text() : Theme.secondary());
        hit(x - 3, y, w + 6, 34, action);
        return y + 39;
    }

    private void drawMain(GuiGraphics g, Layout l, long now, float delta) {
        float x = l.mainX();
        float y = l.mainY();
        float w = l.mainW();
        float h = l.mainH();
        Glass.panel(g, x, y, w, h, 30);
        Glass.noise(g, GlassTexture.noise(), x + 4, y + 4, w - 8, h - 8, 0.06f);

        float topH = 72;
        String title = pageTitle();
        String subtitle = pageSubtitle();
        boolean showSearch = w >= 330;
        float searchW = showSearch ? Math.max(120, Math.min(250, w * 0.34f)) : 0;
        float titleLimit = showSearch ? Math.max(100, w - searchW - 72) : Math.max(80, w - 48);
        Text.draw(g, font, Text.ellipsize(font, title, titleLimit, 1.35f), x + 24, y + 18, 1.35f, Theme.text());
        Text.draw(g, font, Text.ellipsize(font, subtitle, titleLimit, 0.7f), x + 25, y + 42, 0.7f, Theme.muted());

        if (showSearch) {
            float sx = x + w - 24 - searchW;
            float sy = y + 18;
            Glass.rounded(g, sx, sy, searchW, 34, 17,
                    Glass.withAlpha(0xFFFFFFFF, focusedField == searchBox ? 28 : 17),
                    Glass.withAlpha(0xFFFFFFFF, 7));
            Icons.draw(g, Icons.Icon.SEARCH, sx + 11, sy + 9, 16, Theme.secondary());
            drawField(g, searchBox, sx + 31, sy + 7, searchW - 40, 20, "搜索歌曲、歌手或歌单");
            if (focusedField == searchBox) {
                Glass.roundedOutline(g, sx + 0.5f, sy + 0.5f, searchW - 1, 33, 17, 1f,
                        Glass.withAlpha(Theme.accent(), 150));
            }
            hit(sx, sy, searchW, 34, () -> focusField(searchBox));
        }

        if (page == Page.PLAYLIST) {
            Glass.circle(g, x + 29, y + 61, 10, Glass.withAlpha(0xFFFFFFFF, 14));
            Icons.draw(g, Icons.Icon.BACK, x + 23, y + 55, 13, Theme.secondary());
            hit(x + 16, y + 48, 28, 28, this::back);
        }

        float vx = x + 24;
        float vy = y + topH + 4;
        float vw = w - 48;
        float vh = h - topH - 22 - (l.rightW() == 0 ? 54 : 0);
        viewportTop = vy;
        viewportBottom = vy + vh;
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
        g.enableScissor((int) x + 1, (int) (y + topH), (int) (x + w) - 1, (int) (y + h) - 1);
        contentRendering = true;
        float pageEase = easeOutCubic(Math.min(1f, (now - pageChangedAt) / 240f));
        g.pose().pushPose();
        g.pose().translate(0, (1f - pageEase) * 9f, 0);
        float contentHeight = switch (page) {
            case HOME -> drawHome(g, vx, vy - scroll, vw, now);
            case SEARCH -> drawSearch(g, vx, vy - scroll, vw, now);
            case LIBRARY -> drawLibrary(g, vx, vy - scroll, vw, now);
            case SETTINGS -> drawSettings(g, vx, vy - scroll, vw, now);
            case TOGETHER -> drawTogetherPage(g, vx, vy - scroll, vw, now);
            case PLAYLIST -> drawPlaylist(g, vx, vy - scroll, vw, now);
        };
        g.pose().popPose();
        contentRendering = false;
        g.disableScissor();
        maxScroll = Math.max(0, contentHeight - vh);
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
        if (scroll > maxScroll) scroll = maxScroll;

        if (maxScroll > 0.5f) {
            float trackH = vh - 8;
            float thumbH = Math.max(28, trackH * (vh / (vh + maxScroll)));
            float thumbY = vy + 4 + (trackH - thumbH) * (scroll / maxScroll);
            Glass.rounded(g, x + w - 8, vy + 4, 3, trackH, 1.5f, Glass.withAlpha(0xFFFFFFFF, 12));
            Glass.rounded(g, x + w - 8, thumbY, 3, thumbH, 1.5f, Glass.withAlpha(0xFFFFFFFF, 75));
        }
        g.fillGradient((int) x + 20, (int) (y + h - 24), (int) (x + w) - 20, (int) (y + h) - 2,
                0x00000000, 0x44090B12);
    }

    private String pageTitle() {
        return switch (page) {
            case HOME -> greeting();
            case SEARCH -> "搜索";
            case LIBRARY -> "音乐库";
            case SETTINGS -> "设置";
            case TOGETHER -> "一起听";
            case PLAYLIST -> playlistDetail == null ? "歌单" : playlistDetail.playlist().name();
        };
    }

    private String pageSubtitle() {
        return switch (page) {
            case HOME -> "为你准备的今日声音";
            case SEARCH -> "找到任何想听的歌";
            case LIBRARY -> "收藏、每日推荐与你的歌单";
            case SETTINGS -> "外观、接口与账户";
            case TOGETHER -> "和同服玩家同步播放";
            case PLAYLIST -> playlistDetail == null ? "" : playlistDetail.playlist().subtitle();
        };
    }

    private String greeting() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 6) return "夜深了";
        if (hour < 12) return "早上好";
        if (hour < 18) return "下午好";
        return "晚上好";
    }

    // ------------------------------------------------------------------ home

    private float drawHome(GuiGraphics g, float x, float y, float w, long now) {
        float yy = y;
        if (home == null) {
            if (homeLoading) return drawLoading(g, x, yy, w, 300);
            if (!homeError.isBlank()) return drawError(g, x, yy, w, homeError);
            return yy - y + 10;
        }
        List<Models.Playlist> recommended = home.recommended();
        if (!recommended.isEmpty()) {
            drawHero(g, x, yy, w, recommended.get(0), now);
            yy += 198;
        }

        yy = sectionHeader(g, "为你推荐", "根据你的口味挑选", x, yy, w);
        int cols = w >= 660 ? 4 : w >= 470 ? 3 : 2;
        float gap = 14;
        float cardW = (w - gap * (cols - 1)) / cols;
        float coverH = Math.min(148, Math.max(88, cardW));
        int count = Math.min(recommended.size(), 8);
        int rows = (count + cols - 1) / cols;
        for (int i = 0; i < count; i++) {
            int row = i / cols;
            int col = i % cols;
            drawPlaylistCard(g, x + col * (cardW + gap), yy + row * (coverH + 50), cardW, coverH,
                    recommended.get(i), now);
        }
        yy += rows * (coverH + 50) + 16;

        if (!home.toplists().isEmpty()) {
            yy = sectionHeader(g, "排行榜", "此刻大家都在听", x, yy, w);
            int topCount = w < 360 ? 1 : Math.min(3, home.toplists().size());
            float topW = (w - 12 * (topCount - 1)) / Math.max(1, topCount);
            for (int i = 0; i < topCount; i++) {
                drawToplistCard(g, x + i * (topW + 12), yy, topW, home.toplists().get(i));
            }
            yy += 92;
        }

        List<Models.Song> songs = home.daily().isEmpty() ? home.newSongs() : home.daily();
        String label = home.daily().isEmpty() ? "新歌速递" : "每日推荐";
        yy = sectionHeader(g, label, home.daily().isEmpty() ? "刚刚发行的好音乐" : "根据你的口味生成", x, yy, w);
        int maxSongs = Math.min(6, songs.size());
        for (int i = 0; i < maxSongs; i++) {
            drawSongRow(g, x, yy, w, songs.get(i), i, songs, label);
            yy += 45;
        }
        if (songs.isEmpty()) {
            drawEmptyLine(g, x, yy, w, "暂无歌曲");
            yy += 48;
        }
        return yy - y + 8;
    }

    private void drawHero(GuiGraphics g, float x, float y, float w, Models.Playlist playlist, long now) {
        int coverColor = CoverCache.get().color(playlist.coverUrl());
        int top = Glass.mix(0xFF12151E, coverColor, 0.55f);
        int bottom = Glass.mix(0xFF070A11, coverColor, 0.22f);
        Glass.rounded(g, x, y, w, 188, 24, top, bottom);

        float art = Math.min(148, Math.max(72, w * 0.38f));
        float ax = x + w - art - 22;
        float ay = y + 20;
        cover(g, playlist.coverUrl(), ax, ay, art, 20);
        Glass.roundedOutline(g, ax + 0.5f, ay + 0.5f, art - 1, art - 1, 20, 1f,
                Glass.withAlpha(0xFFFFFFFF, 45));

        float textW = Math.max(80, w - art - 58);
        float tx = x + 26;
        Glass.rounded(g, tx, y + 25, 82, 21, 10, Glass.withAlpha(0xFFFFFFFF, 28),
                Glass.withAlpha(0xFFFFFFFF, 10));
        Text.draw(g, font, "编辑精选", tx + 12, y + 31, 0.68f, 0xFFFFFFFF);
        float titleScale = 1.3f;
        Text.draw(g, font, Text.ellipsize(font, heroTitle(playlist.name()), textW, titleScale),
                tx, y + 57, titleScale, 0xFFFFFFFF);
        List<String> description = Text.wrap(font, playlist.description(), textW, 0.73f);
        float dy = y + 92;
        for (int i = 0; i < Math.min(2, description.size()); i++) {
            Text.draw(g, font, description.get(i), tx, dy, 0.73f, Glass.withAlpha(0xFFFFFFFF, 175));
            dy += 13;
        }
        Text.draw(g, font, playlist.subtitle(), tx, y + 126, 0.7f, Glass.withAlpha(0xFFFFFFFF, 155));

        float buttonY = y + 145;
        hit(x, y, w, 188, () -> openPlaylist(playlist.id()));
        int playColor = hovered(tx, buttonY, 92, 29)
                ? Glass.mix(Theme.accent(), 0xFFFFFFFF, 0.14f) : Theme.accent();
        Glass.rounded(g, tx, buttonY, 92, 29, 8, playColor, playColor);
        Icons.draw(g, Icons.Icon.PLAY, tx + 14, buttonY + 7, 15, 0xFFFFFFFF);
        Text.draw(g, font, "播放歌单", tx + 35, buttonY + 10, 0.72f, 0xFFFFFFFF);
        hit(tx, buttonY, 92, 29, () -> playPlaylist(playlist.id()));
    }

    private float sectionHeader(GuiGraphics g, String title, String subtitle, float x, float y, float w) {
        Text.draw(g, font, title, x, y, 1.1f, Theme.text());
        Text.draw(g, font, subtitle, x, y + 16, 0.66f, Theme.muted());
        return y + 34;
    }

    private static String heroTitle(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("^[\\s.·•]+", "").replaceAll("[\\s.·•]+$", "");
        return cleaned.isBlank() ? value.strip() : cleaned;
    }

    private void drawPlaylistCard(GuiGraphics g, float x, float y, float w, float coverSize,
                                  Models.Playlist playlist, long now) {
        boolean hovered = hovered(x, y, w, coverSize + 44);
        cover(g, playlist.coverUrl(), x, y, coverSize, 17);
        Glass.roundedOutline(g, x + 0.5f, y + 0.5f, coverSize - 1, coverSize - 1, 17, 1f,
                Glass.withAlpha(0xFFFFFFFF, hovered ? 90 : 30));
        if (hovered) {
            Glass.circle(g, x + coverSize - 25, y + coverSize - 25, 17, 0xD90A0C12);
            Icons.draw(g, Icons.Icon.PLAY, x + coverSize - 33, y + coverSize - 33, 16, 0xFFFFFFFF);
        }
        Text.draw(g, font, Text.ellipsize(font, playlist.name(), w, 0.85f), x, y + coverSize + 9,
                0.85f, Theme.text());
        Text.draw(g, font, Text.ellipsize(font, playlist.subtitle(), w, 0.64f), x, y + coverSize + 23,
                0.64f, Theme.muted());
        hit(x, y, w, coverSize + 44, () -> openPlaylist(playlist.id()));
    }

    private void drawToplistCard(GuiGraphics g, float x, float y, float w, Models.Playlist playlist) {
        boolean hovered = hovered(x, y, w, 80);
        Glass.rounded(g, x, y, w, 80, 16, Glass.withAlpha(0xFFFFFFFF, hovered ? 24 : 14),
                Glass.withAlpha(0xFFFFFFFF, 5));
        cover(g, playlist.coverUrl(), x + 9, y + 9, 62, 12);
        float tx = x + 80;
        Text.draw(g, font, Text.ellipsize(font, playlist.name(), w - 90, 0.83f), tx, y + 20, 0.83f,
                Theme.text());
        Text.draw(g, font, playlist.updateFrequency().isBlank() ? "实时更新" : playlist.updateFrequency(),
                tx, y + 37, 0.62f, Theme.muted());
        Text.draw(g, font, playlist.trackCount() > 0 ? playlist.trackCount() + " 首" : "榜单",
                tx, y + 53, 0.62f, Theme.secondary());
        hit(x, y, w, 80, () -> openPlaylist(playlist.id()));
    }

    private void drawSongRow(GuiGraphics g, float x, float y, float w, Models.Song song, int index,
                             List<Models.Song> context, String title) {
        float h = 40;
        boolean hovered = hovered(x, y, w, h);
        boolean current = player.current() != null && player.current().id() == song.id();
        if (hovered || current) {
            Glass.rounded(g, x - 6, y, w + 12, h, 11, Glass.withAlpha(0xFFFFFFFF, hovered ? 19 : 10),
                    Glass.withAlpha(0xFFFFFFFF, 3));
        }
        cover(g, song.coverUrl(), x + 2, y + 4, 30, 8);
        if (current && player.state() == MusicPlayer.Playback.PLAYING) {
            Icons.equalizer(g, x + 2, y + 4, 30, 30, Theme.accent(), Util.getMillis());
        }
        float tx = x + 42;
        Text.draw(g, font, Text.ellipsize(font, song.name(), w - 150, 0.86f), tx, y + 6, 0.86f,
                current ? Theme.accent() : Theme.text());
        Text.draw(g, font, Text.ellipsize(font, song.subtitle(), w - 150, 0.63f), tx, y + 21, 0.63f,
                Theme.muted());
        Text.draw(g, font, song.durationText(), x + w - 54, y + 14, 0.64f, Theme.muted());
        if (hovered) {
            Icons.draw(g, Icons.Icon.PLAY, x + w - 28, y + 10, 17, 0xFFFFFFFF);
        }
        hit(x, y, w, h, () -> player.playSong(song, context, title));
    }

    private float drawLoading(GuiGraphics g, float x, float y, float w, float h) {
        for (int i = 0; i < 3; i++) {
            float cardW = (w - 28) / 3f;
            Glass.rounded(g, x + i * (cardW + 14), y, cardW, 140, 18,
                    Glass.withAlpha(0xFFFFFFFF, 15), Glass.withAlpha(0xFFFFFFFF, 5));
            Glass.rounded(g, x + i * (cardW + 14) + 12, y + 110, cardW * 0.6f, 8, 4,
                    Glass.withAlpha(0xFFFFFFFF, 18));
        }
        Icons.equalizer(g, x + w / 2f - 22, y + h / 2f - 10, 44, 20, Theme.accent(), Util.getMillis());
        return Math.max(140, h);
    }

    private float drawError(GuiGraphics g, float x, float y, float w, String message) {
        Glass.rounded(g, x, y, w, 120, 18, Glass.withAlpha(0xFFFF5A67, 24),
                Glass.withAlpha(0xFFFF5A67, 8));
        Icons.draw(g, Icons.Icon.CLOUD, x + 20, y + 24, 32, Theme.danger());
        Text.draw(g, font, "加载失败", x + 64, y + 27, 1.0f, Theme.text());
        for (int i = 0; i < 2; i++) {
            List<String> lines = Text.wrap(font, message, w - 96, 0.68f);
            if (i < lines.size()) Text.draw(g, font, lines.get(i), x + 64, y + 47 + i * 13, 0.68f, Theme.secondary());
        }
        Glass.glassButton(g, x + 64, y + 78, 64, 24, 12, false, hovered(x + 64, y + 78, 64, 24));
        Text.centered(g, font, "重试", x + 96, y + 84, 0.7f, Theme.text());
        hit(x + 64, y + 78, 64, 24, () -> loadHome(true));
        return 140;
    }

    private void drawEmptyLine(GuiGraphics g, float x, float y, float w, String text) {
        Text.draw(g, font, text, x, y, 0.72f, Theme.muted());
    }

    // ------------------------------------------------------------------ search

    private float drawSearch(GuiGraphics g, float x, float y, float w, long now) {
        float yy = y;
        if (lastQuery.isBlank()) {
            Text.draw(g, font, "探索", x, yy, 1.05f, Theme.text());
            yy += 24;
            String[] chips = {"周杰伦", "林俊杰", "陈奕迅", "五月天", "邓紫棋", "米津玄師", "薛之谦", "毛不易"};
            float cx = x;
            float cy = yy;
            for (String chip : chips) {
                float cw = font.width(chip) + 28;
                if (cx + cw > x + w) {
                    cx = x;
                    cy += 34;
                }
                boolean hovered = hovered(cx, cy, cw, 26);
                Glass.rounded(g, cx, cy, cw, 26, 13,
                        Glass.withAlpha(0xFFFFFFFF, hovered ? 32 : 17),
                        Glass.withAlpha(0xFFFFFFFF, 5));
                Text.draw(g, font, chip, cx + 14, cy + 9, 0.75f, Theme.text());
                hit(cx, cy, cw, 26, () -> runSearch(chip));
                cx += cw + 8;
            }
            yy = cy + 48;
            if (home != null && !home.newSongs().isEmpty()) {
                yy = sectionHeader(g, "最近上新", "从新歌开始探索", x, yy, w);
                int count = Math.min(5, home.newSongs().size());
                for (int i = 0; i < count; i++) {
                    drawSongRow(g, x, yy, w, home.newSongs().get(i), i, home.newSongs(), "新歌速递");
                    yy += 45;
                }
            }
            return yy - y + 10;
        }

        if (searchLoading) return drawLoading(g, x, yy, w, 260);
        if (!searchError.isBlank()) return drawError(g, x, yy, w, searchError);
        if (searchResult == null) return yy - y;
        Text.draw(g, font, "“" + lastQuery + "”", x, yy, 1.1f, Theme.text());
        Text.draw(g, font, "找到 " + searchResult.total() + " 首歌曲", x + font.width(lastQuery) * 1.1f + 32,
                yy + 5, 0.65f, Theme.muted());
        yy += 26;
        if (searchResult.songs().isEmpty()) {
            Icons.draw(g, Icons.Icon.SEARCH, x + w / 2f - 18, yy + 30, 36, Theme.muted());
            Text.centered(g, font, "没有找到相关歌曲", x + w / 2f, yy + 76, 0.8f, Theme.secondary());
            return yy - y + 120;
        }
        int count = searchResult.songs().size();
        for (int i = 0; i < count; i++) {
            drawSongRow(g, x, yy, w, searchResult.songs().get(i), i, searchResult.songs(), "搜索：" + lastQuery);
            yy += 45;
        }
        return yy - y + 8;
    }

    // ------------------------------------------------------------------ library

    private float drawLibrary(GuiGraphics g, float x, float y, float w, long now) {
        float yy = y;
        if (!api.isLoggedIn()) {
            Glass.rounded(g, x, yy, w, 150, 22,
                    Glass.withAlpha(Theme.accent(), 30), Glass.withAlpha(Theme.purple(), 16));
            Glass.circle(g, x + 65, yy + 75, 38, Glass.withAlpha(0xFFFFFFFF, 12));
            Icons.draw(g, Icons.Icon.USER, x + 43, yy + 53, 44, Theme.accent());
            Text.draw(g, font, "登录后，音乐库会属于你", x + 122, yy + 45, 1.15f, Theme.text());
            List<String> lines = Text.wrap(font,
                    "同步网易云歌单、每日推荐与喜欢的音乐。支持扫码、手机号或浏览器 Cookie。",
                    w - 156, 0.68f);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                Text.draw(g, font, lines.get(i), x + 122, yy + 68 + i * 13, 0.68f, Theme.secondary());
            }
            Glass.glassButton(g, x + 122, yy + 104, 92, 28, 14, true, hovered(x + 122, yy + 104, 92, 28));
            Text.centered(g, font, "登录账号", x + 168, yy + 111, 0.7f, 0xFFFFFFFF);
            hit(x + 122, yy + 104, 92, 28, this::openLogin);
            yy += 175;
        } else {
            Models.UserProfile profile = api.profile();
            Glass.rounded(g, x, yy, w, 88, 20, Glass.withAlpha(0xFFFFFFFF, 18),
                    Glass.withAlpha(0xFFFFFFFF, 6));
            if (profile != null) {
                avatar(g, profile.avatarUrl(), x + 16, yy + 16, 56);
                Text.draw(g, font, profile.displayName(), x + 86, yy + 19, 1.15f, Theme.text());
                Text.draw(g, font, profile.signature().isBlank() ? "欢迎回来" : profile.signature(),
                        x + 86, yy + 41, 0.65f, Theme.secondary());
                Text.draw(g, font, "Lv." + profile.level() + (profile.vipType() > 0 ? "  ·  VIP" : ""),
                        x + 86, yy + 58, 0.62f, profile.vipType() > 0 ? 0xFFFFD36E : Theme.muted());
            }
            Glass.glassButton(g, x + w - 88, yy + 29, 72, 26, 13, false, hovered(x + w - 88, yy + 29, 72, 26));
            Text.centered(g, font, "退出", x + w - 52, yy + 36, 0.68f, Theme.secondary());
            hit(x + w - 88, yy + 29, 72, 26, this::logout);
            yy += 106;
        }

        if (home == null) {
            if (homeLoading) return yy - y + drawLoading(g, x, yy, w, 240);
            if (!homeError.isBlank()) return yy - y + drawError(g, x, yy, w, homeError);
            return yy - y + 10;
        }

        if (!home.daily().isEmpty()) {
            yy = sectionHeader(g, "每日推荐", "每天六点更新", x, yy, w);
            int count = Math.min(5, home.daily().size());
            for (int i = 0; i < count; i++) {
                drawSongRow(g, x, yy, w, home.daily().get(i), i, home.daily(), "每日推荐");
                yy += 45;
            }
            yy += 10;
        }

        if (!home.userPlaylists().isEmpty()) {
            yy = sectionHeader(g, "我的歌单", api.profile() == null ? "" : api.profile().displayName() + " 的歌单",
                    x, yy, w);
            int cols = w >= 660 ? 4 : w >= 470 ? 3 : 2;
            float gap = 14;
            float cardW = (w - gap * (cols - 1)) / cols;
            float coverH = Math.min(148, cardW);
            List<Models.Playlist> lists = home.userPlaylists();
            for (int i = 0; i < Math.min(8, lists.size()); i++) {
                int row = i / cols;
                int col = i % cols;
                drawPlaylistCard(g, x + col * (cardW + gap), yy + row * (coverH + 50), cardW, coverH,
                        lists.get(i), now);
            }
            yy += ((Math.min(8, lists.size()) + cols - 1) / cols) * (coverH + 50) + 10;
        }
        return yy - y + 10;
    }

    // ------------------------------------------------------------------ playlist

    private float drawPlaylist(GuiGraphics g, float x, float y, float w, long now) {
        float yy = y;
        if (playlistLoading) return drawLoading(g, x, yy, w, 280);
        if (!playlistError.isBlank()) return drawError(g, x, yy, w, playlistError);
        if (playlistDetail == null) return yy - y;
        Models.Playlist playlist = playlistDetail.playlist();
        Glass.rounded(g, x, yy, w, 174, 22, Glass.withAlpha(0xFFFFFFFF, 16),
                Glass.withAlpha(0xFFFFFFFF, 4));
        float art = 132;
        cover(g, playlist.coverUrl(), x + 20, yy + 21, art, 18);
        Glass.roundedOutline(g, x + 20.5f, yy + 21.5f, art - 1, art - 1, 18, 1f,
                Glass.withAlpha(0xFFFFFFFF, 50));
        float tx = x + 174;
        Text.draw(g, font, Text.ellipsize(font, playlist.name(), w - 200, 1.55f), tx, yy + 30, 1.55f, Theme.text());
        Text.draw(g, font, playlist.subtitle(), tx, yy + 58, 0.72f, Theme.secondary());
        List<String> description = Text.wrap(font, playlist.description(), w - 200, 0.68f);
        for (int i = 0; i < Math.min(2, description.size()); i++) {
            Text.draw(g, font, description.get(i), tx, yy + 79 + i * 13, 0.68f, Theme.muted());
        }
        Glass.glassButton(g, tx, yy + 119, 96, 30, 15, true, hovered(tx, yy + 119, 96, 30));
        Icons.draw(g, Icons.Icon.PLAY, tx + 14, yy + 126, 16, 0xFFFFFFFF);
        Text.draw(g, font, "播放全部", tx + 36, yy + 129, 0.72f, 0xFFFFFFFF);
        hit(tx, yy + 119, 96, 30, () -> player.playPlaylist(playlistDetail, 0));
        Glass.glassButton(g, tx + 106, yy + 119, 78, 30, 15, false, hovered(tx + 106, yy + 119, 78, 30));
        Icons.draw(g, Icons.Icon.SHUFFLE, tx + 118, yy + 126, 16, Theme.text());
        Text.draw(g, font, "随机", tx + 139, yy + 129, 0.72f, Theme.text());
        hit(tx + 106, yy + 119, 78, 30, () -> {
            if (!player.shuffle()) player.toggleShuffle();
            player.playPlaylist(playlistDetail, (int) (Math.random() * Math.max(1, playlistDetail.tracks().size())));
        });
        yy += 194;

        List<Models.Song> tracks = playlistDetail.tracks();
        Text.draw(g, font, "歌曲", x + 6, yy, 0.66f, Theme.muted());
        Text.draw(g, font, "时长", x + w - 48, yy, 0.66f, Theme.muted());
        Glass.line(g, x, yy + 15, x + w, yy + 15, 1f, Theme.faint());
        yy += 23;
        int visibleStart = Math.max(0, (int) ((scroll - 200) / 38) - 2);
        int visibleEnd = Math.min(tracks.size(), visibleStart + 30);
        for (int i = visibleStart; i < visibleEnd; i++) {
            Models.Song song = tracks.get(i);
            float ry = yy + i * 38;
            if (!visible(ry, 34)) continue;
            boolean hovered = hovered(x, ry, w, 34);
            boolean current = player.current() != null && player.current().id() == song.id();
            if (hovered || current) {
                Glass.rounded(g, x - 6, ry, w + 12, 34, 9,
                        Glass.withAlpha(0xFFFFFFFF, hovered ? 18 : 9), 0);
            }
            String number = String.valueOf(i + 1);
            Text.draw(g, font, number, x + 5, ry + 11, 0.66f, current ? Theme.accent() : Theme.muted());
            Text.draw(g, font, Text.ellipsize(font, song.name(), w - 150, 0.82f), x + 34, ry + 5, 0.82f,
                    current ? Theme.accent() : Theme.text());
            Text.draw(g, font, Text.ellipsize(font, song.artist(), w - 150, 0.62f), x + 34, ry + 19,
                    0.62f, Theme.muted());
            Text.draw(g, font, song.durationText(), x + w - 46, ry + 10, 0.64f, Theme.muted());
            if (hovered) Icons.draw(g, Icons.Icon.PLAY, x + w - 23, ry + 9, 15, 0xFFFFFFFF);
            hit(x, ry, w, 34, () -> player.playSong(song, tracks, playlist.name()));
        }
        return 194 + 23 + tracks.size() * 38 + 12;
    }

    // ------------------------------------------------------------------ now playing

    private void drawNowPlaying(GuiGraphics g, Layout l, long now) {
        float x = l.rightX();
        float y = l.y();
        float w = l.rightW();
        float h = l.h();
        Glass.panel(g, x, y, w, h, 28);
        Glass.noise(g, GlassTexture.noise(), x + 4, y + 4, w - 8, h - 8, 0.06f);
        float openEase = easeOutCubic(Math.min(1f, (now - openAt) / 280f));
        g.pose().pushPose();
        g.pose().translate((1f - openEase) * 10f, 0, 0);
        Models.Song song = player.current();
        float pad = 20;
        boolean empty = song == null;
        float art = empty ? 84 : Math.min(w - pad * 2, Math.min(252, h * 0.37f));
        float ax = x + (w - art) / 2f;
        float ay = y + 24;
        if (song != null) {
            int color = CoverCache.get().color(song.coverUrl());
            Glass.circle(g, ax + art / 2, ay + art / 2, art * 0.45f, Glass.withAlpha(color, 55));
            cover(g, song.coverUrl(), ax, ay, art, 22);
            Glass.roundedOutline(g, ax + 0.5f, ay + 0.5f, art - 1, art - 1, 22, 1f,
                    Glass.withAlpha(0xFFFFFFFF, 45));
        } else {
            Glass.rounded(g, ax, ay, art, art, 20,
                    Glass.withAlpha(0xFFFFFFFF, 12), Glass.withAlpha(0xFFFFFFFF, 5));
            Glass.roundedOutline(g, ax + 0.5f, ay + 0.5f, art - 1, art - 1, 20, 1f,
                    Glass.withAlpha(0xFFFFFFFF, 22));
            Icons.draw(g, Icons.Icon.MUSIC, ax + art * 0.31f, ay + art * 0.31f, art * 0.38f,
                    Glass.withAlpha(0xFFFFFFFF, 80));
        }

        float ty = ay + art + 18;
        Text.draw(g, font, song == null ? "未在播放" : Text.ellipsize(font, song.name(), w - 40, 1.15f),
                x + pad, ty, 1.15f, Theme.text());
        String subtitle = song == null ? "选择一首歌，让世界安静下来" : Text.ellipsize(font, song.artist(), w - 40, 0.7f);
        Text.draw(g, font, subtitle, x + pad, ty + 22, 0.7f, Theme.secondary());
        if (song != null && !song.album().isBlank()) {
            Text.draw(g, font, Text.ellipsize(font, song.album(), w - 40, 0.62f),
                    x + pad, ty + 36, 0.62f, Theme.muted());
        }
        if (player.state() == MusicPlayer.Playback.RESOLVING) {
            Text.draw(g, font, "正在获取音源…", x + pad, ty + 54, 0.66f, Theme.accent());
        } else if (player.state() == MusicPlayer.Playback.ERROR) {
            Text.draw(g, font, Text.ellipsize(font, player.message(), w - 40, 0.66f),
                    x + pad, ty + 54, 0.66f, Theme.danger());
        }

        float progressY = ty + 64;
        float fraction = seeking ? (float) seekFraction : (float) player.progress();
        float trackW = w - pad * 2;
        Glass.rounded(g, x + pad, progressY, trackW, 5, 2.5f, Glass.withAlpha(0xFFFFFFFF, 25));
        if (fraction > 0.001f) {
            Glass.rounded(g, x + pad, progressY, trackW * fraction, 5, 2.5f, Theme.accent());
            Glass.circle(g, x + pad + trackW * fraction, progressY + 2.5f, 4f, 0xFFFFFFFF);
        }
        Text.draw(g, font, time(player.positionSeconds()), x + pad, progressY + 10, 0.58f, Theme.muted());
        Text.draw(g, font, time(player.durationSeconds()), x + w - pad - 24, progressY + 10, 0.58f, Theme.muted());
        hit(x + pad, progressY - 8, trackW, 24, () -> beginSeek(x + pad, trackW, progressY));

        float controlsY = progressY + 42;
        float center = x + w / 2f;
        iconButton(g, center - 68, controlsY, 40, Icons.Icon.PREV, () -> player.previous(), 21);
        Glass.circle(g, center, controlsY + 20, 25, Glass.withAlpha(Theme.accent(), 235));
        Glass.circle(g, center, controlsY + 20, 25, Glass.withAlpha(0xFFFFFFFF, 15));
        if (player.state() == MusicPlayer.Playback.RESOLVING) {
            spinner(g, center, controlsY + 20, 8, 0xFFFFFFFF);
        } else {
            Icons.draw(g, player.state() == MusicPlayer.Playback.PLAYING ? Icons.Icon.PAUSE : Icons.Icon.PLAY,
                    center - 11, controlsY + 9, 22, 0xFFFFFFFF);
        }
        hit(center - 26, controlsY - 6, 52, 52, player::toggle);
        iconButton(g, center + 28, controlsY, 40, Icons.Icon.NEXT, () -> player.next(), 21);

        float sideY = controlsY + 11.5f;
        Icons.draw(g, Icons.Icon.SHUFFLE, x + pad, sideY, 17,
                player.shuffle() ? Theme.accent() : Theme.muted());
        hit(x + pad - 4, sideY - 4, 25, 25, player::toggleShuffle);
        Icons.draw(g, player.repeatOne() ? Icons.Icon.REPEAT_ONE : Icons.Icon.REPEAT, x + w - pad - 17, sideY, 17,
                player.repeatOne() || player.repeatAll() ? Theme.accent() : Theme.muted());
        hit(x + w - pad - 21, sideY - 4, 25, 25, player::cycleRepeat);

        float volumeY = controlsY + 62;
        Icons.draw(g, Icons.Icon.VOLUME, x + pad, volumeY - 1, 17, Theme.secondary());
        float volX = x + pad + 26;
        float volW = w - pad * 2 - 26;
        Glass.rounded(g, volX, volumeY + 7, volW, 4, 2, Glass.withAlpha(0xFFFFFFFF, 22));
        float vol = player.volume() / 100f;
        Glass.rounded(g, volX, volumeY + 7, volW * vol, 4, 2, Glass.withAlpha(0xFFFFFFFF, 150));
        Glass.circle(g, volX + volW * vol, volumeY + 9, 3.5f, 0xFFFFFFFF);
        hit(volX, volumeY, volW, 20, () -> {
            drag = new Drag(volX, volW, 0, 100, value -> {
                ModConfig.get().volume = value;
                player.setVolume(value, false);
            }, () -> ModConfig.get().save());
            updateDrag(mouseX);
        });

        float footerTop = y + h - 49;
        float lyricsTop = volumeY + 32;
        boolean showNext = lyricsTop + 30 <= footerTop - 4;
        if (!showNext) {
            lyricsTop = Math.max(volumeY + 22, footerTop - 18);
        }
        if (lyricsTop + (showNext ? 30 : 12) > footerTop - 2) {
            lyricsTop = footerTop - (showNext ? 30 : 14);
        }
        if (lyricsTop - 8 > volumeY + 22) {
            Glass.line(g, x + pad, lyricsTop - 8, x + w - pad, lyricsTop - 8, 1f, Theme.faint());
        }
        Models.Lyrics lyrics = player.lyrics();
        int active = lyrics.indexAt(player.positionSeconds());
        String line = active >= 0 ? lyrics.lines().get(active).text() : "暂无歌词";
        String next = active + 1 < lyrics.lines().size() ? lyrics.lines().get(active + 1).text() : "";
        Text.draw(g, font, Text.ellipsize(font, line, w - pad * 2 - (showNext ? 0 : 36), 0.78f),
                x + pad, lyricsTop, 0.78f, active >= 0 ? Theme.text() : Theme.muted());
        if (showNext) {
            Text.draw(g, font, Text.ellipsize(font, next, w - pad * 2, 0.66f), x + pad, lyricsTop + 17,
                    0.66f, Theme.muted());
        }
        Text.draw(g, font, "歌词", x + w - pad - 28, lyricsTop + (showNext ? 34 : 0), 0.62f, Theme.accent());
        hit(x + pad, lyricsTop - 2, w - pad * 2, showNext ? 34 : 16, () -> {
            if (player.current() != null) openLyrics();
        });

        Glass.line(g, x + pad, footerTop, x + w - pad, footerTop, 1f, Theme.faint());
        String queueText = player.contextTitle().isBlank() ? "未加入队列" : player.contextTitle();
        String queueIndex = player.queue().isEmpty() ? "" : (player.index() + 1) + " / " + player.queue().size();
        float queueIndexW = font.width(queueIndex);
        float queueTitleW = w - pad * 2 - queueIndexW - 8;
        Text.draw(g, font, Text.ellipsize(font, queueText, queueTitleW, 0.62f), x + pad, footerTop + 12, 0.62f,
                Theme.muted());
        if (!queueIndex.isBlank()) {
            Text.draw(g, font, queueIndex, x + w - pad - queueIndexW, footerTop + 12, 0.62f, Theme.muted());
        }
        g.pose().popPose();
    }

    private static float easeOutCubic(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }

    private void beginSeek(float x, float w, float y) {
        seeking = true;
        drag = new Drag(x, w, 0, 1000, value -> {
            seekFraction = value / 1000.0;
        }, () -> {
            player.seek(seekFraction);
            seeking = false;
        });
        updateDrag(mouseX);
    }

    private void iconButton(GuiGraphics g, float x, float y, float size, Icons.Icon icon, Runnable action,
                            float iconSize) {
        float cx = x + size / 2f;
        float cy = y + size / 2f;
        boolean hovered = hovered(x, y, size, size);
        Glass.circle(g, cx, cy, size / 2f, Glass.withAlpha(0xFFFFFFFF, hovered ? 28 : 12));
        Icons.draw(g, icon, cx - iconSize / 2f, cy - iconSize / 2f, iconSize, Theme.text());
        hit(x, y, size, size, action);
    }

    private void drawFloatingControls(GuiGraphics g, Layout l) {
        if (l.rightW() > 0) return;
        Models.Song song = player.current();
        if (song == null) return;
        float x = l.mainX() + 16;
        float w = Math.max(120, l.mainW() - 32);
        float h = 46;
        float y = l.y() + l.h() - h - 12;
        Glass.panel(g, x, y, w, h, 18, Theme.accent(), 0.92f, true);

        float coverSize = 28;
        float coverY = y + (h - coverSize) / 2f;
        cover(g, song.coverUrl(), x + 8, coverY, coverSize, 8);

        // Keep the three transport controls inside the pill even at very small GUI widths.
        float control = w >= 360 ? 28 : 24;
        float gap = 4;
        int count = w >= 280 ? 3 : 2;
        float controlsW = count * control + (count - 1) * gap;
        float right = x + w - 8;
        float nextX = right - control;
        float playX = nextX - control - gap;
        float prevX = playX - control - gap;
        float textW = Math.max(24, w - 8 - (coverSize + 8) - 6 - controlsW - 6);
        float textX = x + 8 + coverSize + 8;
        Text.draw(g, font, Text.ellipsize(font, song.name(), textW, 0.78f), textX, y + 7, 0.78f, 0xFFFFFFFF);
        Text.draw(g, font, Text.ellipsize(font, song.artist(), textW, 0.62f), textX, y + 21, 0.62f,
                Glass.withAlpha(0xFFFFFFFF, 170));

        float progress = seeking ? (float) seekFraction : (float) player.progress();
        float barX = textX;
        float barW = Math.max(24, textW);
        Glass.rounded(g, barX, y + h - 8, barW, 3, 1.5f, Glass.withAlpha(0xFFFFFFFF, 26));
        if (progress > 0.001f) {
            Glass.rounded(g, barX, y + h - 8, barW * progress, 3, 1.5f, Theme.accent());
        }
        hit(barX, y + h - 15, barW, 18, () -> beginSeek(barX, barW, y + h - 8));

        if (count == 3) {
            Icons.draw(g, Icons.Icon.PREV, prevX + 4, y + (h - 16) / 2f, 16, Theme.text());
            hit(prevX, y + 6, control, h - 12, player::previous);
        }
        Icons.draw(g, Icons.Icon.NEXT, nextX + 4, y + (h - 16) / 2f, 16, Theme.text());
        hit(nextX, y + 6, control, h - 12, player::next);

        float playCx = playX + control / 2f;
        float playCy = y + h / 2f;
        float radius = control / 2f + 2;
        Glass.circle(g, playCx, playCy, radius, Glass.withAlpha(Theme.accent(), 240));
        Icons.draw(g, player.state() == MusicPlayer.Playback.PLAYING ? Icons.Icon.PAUSE : Icons.Icon.PLAY,
                playCx - 8, playCy - 8, 16, 0xFFFFFFFF);
        hit(playX, y + 6, control, h - 12, player::toggle);
    }

    // ------------------------------------------------------------------ helpers

    public void openTogether() {
        if (page != Page.TOGETHER) switchPage(Page.TOGETHER);
    }

    public void toggleTogether() {
        switchPage(page == Page.TOGETHER ? Page.HOME : Page.TOGETHER);
    }

    private float drawTogetherPage(GuiGraphics g, float x, float y, float w, long now) {
        float yy = y;
        Glass.rounded(g, x, yy, w, 62, 16,
                Glass.withAlpha(0xFFFFFFFF, 14), Glass.withAlpha(0xFFFFFFFF, 4));
        Icons.draw(g, Icons.Icon.TOGETHER, x + 18, yy + 18, 24, Theme.accent());
        Text.draw(g, font, "和同服玩家一起听", x + 52, yy + 14, 0.9f, Theme.text());
        Text.draw(g, font, "需要服务器安装同一版本的 iOS Music",
                x + 52, yy + 33, 0.62f, Theme.muted());
        yy += 76;

        TogetherMessage invite = TogetherClient.invite();
        TogetherClient.Room room = TogetherClient.room();
        if (invite != null) {
            Glass.rounded(g, x, yy, w, 112, 16,
                    Glass.withAlpha(Theme.accent(), 24), Glass.withAlpha(Theme.accent(), 8));
            Text.draw(g, font, Text.ellipsize(font, invite.fromName + " 邀请你一起听", w - 40, 0.9f),
                    x + 18, yy + 16, 0.9f, Theme.text());
            Text.draw(g, font, "接受后会跟随对方的播放进度", x + 18, yy + 35, 0.62f, Theme.secondary());
            float inviteW = Math.max(72, (w - 46) / 2f);
            togetherButton(g, x + 18, yy + 62, inviteW, 30, "接受", true, TogetherClient::acceptInvite);
            togetherButton(g, x + 28 + inviteW, yy + 62, inviteW, 30, "拒绝", false,
                    TogetherClient::declineInvite);
            yy += 126;
        }

        if (room != null) {
            Glass.rounded(g, x, yy, w, 76 + room.members().size() * 24, 16,
                    Glass.withAlpha(0xFFFFFFFF, 14), Glass.withAlpha(0xFFFFFFFF, 4));
            Text.draw(g, font, "一起听房间", x + 18, yy + 15, 0.82f, Theme.secondary());
            Text.draw(g, font, "房主 · " + room.hostName(), x + 18, yy + 37, 0.76f, Theme.text());
            Text.draw(g, font, room.host() ? "你正在同步播放" : "正在跟随房主播放",
                    x + 18, yy + 55, 0.64f, room.host() ? Theme.accent() : Theme.secondary());
            float my = yy + 80;
            for (String member : room.members()) {
                Glass.circle(g, x + 24, my + 7, 4.5f, Theme.accent());
                Text.draw(g, font, Text.ellipsize(font, member, w - 90, 0.72f),
                        x + 36, my, 0.72f, Theme.text());
                my += 24;
            }
            togetherButton(g, x, yy + 76 + room.members().size() * 24 - 38, 112, 28,
                    "退出房间", false, TogetherClient::leaveRoom);
            yy += 90 + room.members().size() * 24;
        } else {
            Text.draw(g, font, "同服安装了 iOS Music 的玩家", x, yy, 0.72f, Theme.secondary());
            yy += 22;
            String self = minecraft.player == null ? "" : minecraft.player.getUUID().toString();
            int shown = 0;
            for (TogetherMessage.Peer peer : TogetherClient.peers()) {
                if (peer.uuid.equals(self)) continue;
                Glass.rounded(g, x, yy, w, 44, 12,
                        Glass.withAlpha(0xFFFFFFFF, 12), Glass.withAlpha(0xFFFFFFFF, 4));
                Glass.circle(g, x + 20, yy + 22, 6, Theme.accent());
                Text.draw(g, font, Text.ellipsize(font, peer.name, w - 140, 0.8f),
                        x + 34, yy + 14, 0.8f, Theme.text());
                Text.draw(g, font, "已安装模组", x + 34, yy + 27, 0.58f, Theme.faint());
                togetherButton(g, x + w - 86, yy + 8, 72, 28, "邀请", true,
                        () -> TogetherClient.invite(peer.uuid, peer.name));
                yy += 52;
                shown++;
            }
            if (shown == 0) {
                Text.draw(g, font, "没有检测到其他安装此模组的玩家", x, yy + 6, 0.72f, Theme.muted());
                Text.draw(g, font, "服务器安装同一版本 iOS Music 后，这里会列出其他玩家",
                        x, yy + 24, 0.6f, Theme.faint());
                yy += 50;
            }
        }

        String notice = TogetherClient.notice();
        if (!notice.isBlank()) {
            yy += 8;
            Text.draw(g, font, Text.ellipsize(font, notice, w, 0.64f), x, yy, 0.64f, Theme.accent());
            yy += 18;
        }
        return yy - y + 14;
    }

    private void togetherButton(GuiGraphics g, float x, float y, float w, float h, String label,
                                boolean primary, Runnable action) {
        boolean hovered = hovered(x, y, w, h);
        Glass.glassButton(g, x, y, w, h, h / 2f, primary, hovered);
        Text.centered(g, font, label, x + w / 2f, y + h / 2f - 6, 0.68f,
                primary ? 0xFFFFFFFF : Theme.text());
        hit(x, y, w, h, action);
    }

    private void cover(GuiGraphics g, String url, float x, float y, float size, float radius) {
        ResourceLocation texture = CoverCache.get().texture(url, false);
        if (texture != null) {
            net.minecraft.client.renderer.texture.AbstractTexture image =
                    minecraft.getTextureManager().getTexture(texture);
            if (image != null) image.setFilter(true, false);
            g.blit(texture, (int) x, (int) y, 0, 0, (int) size, (int) size, (int) size, (int) size);
        } else {
            int color = CoverCache.get().color(url);
            Glass.rounded(g, x, y, size, size, radius,
                    Glass.mix(0xFF222733, color, 0.55f), Glass.mix(0xFF0E1118, color, 0.25f));
            Icons.draw(g, Icons.Icon.MUSIC, x + size * 0.31f, y + size * 0.31f, size * 0.38f,
                    Glass.withAlpha(0xFFFFFFFF, 120));
        }
    }

    private void avatar(GuiGraphics g, String url, float x, float y, float size) {
        ResourceLocation texture = CoverCache.get().texture(url, true);
        Glass.circle(g, x + size / 2f, y + size / 2f, size / 2f + 1, Glass.withAlpha(0xFFFFFFFF, 28));
        if (texture != null) {
            net.minecraft.client.renderer.texture.AbstractTexture image =
                    minecraft.getTextureManager().getTexture(texture);
            if (image != null) image.setFilter(true, false);
            g.blit(texture, (int) x, (int) y, 0, 0, (int) size, (int) size, (int) size, (int) size);
        } else {
            Glass.circle(g, x + size / 2f, y + size / 2f, size / 2f,
                    Glass.withAlpha(Theme.accent(), 90));
            Icons.draw(g, Icons.Icon.USER, x + size * 0.22f, y + size * 0.22f, size * 0.56f, 0xFFFFFFFF);
        }
    }

    private void drawField(GuiGraphics g, EditBox box, float x, float y, float w, float h, String hint) {
        box.setVisible(true);
        box.setFocused(focusedField == box);
        box.setX((int) (x + 2));
        // An unbordered EditBox draws its text at getY(), not vertically centered. Offset the box
        // itself so the custom TTF sits on the visual centre line of the glass field.
        box.setY((int) (y + (h - 10) / 2f + 1f));
        box.setWidth((int) Math.max(10, w - 4));
        box.setHeight((int) h);
        box.setHint(Component.literal(hint));
        box.setTextColor(Theme.text());
        box.render(g, mouseX, mouseY, 0);
    }

    private void drawScrollFade(GuiGraphics g, float x, float y, float w, float h) {
        g.fillGradient((int) x, (int) y, (int) (x + w), (int) (y + h), 0, 0);
    }

    private boolean hovered(float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private boolean visible(float y, float h) {
        return y + h >= viewportTop && y <= viewportBottom;
    }

    private void hit(float x, float y, float w, float h, Runnable action) {
        if (action == null) return;
        if (contentRendering && !visible(y, h)) return;
        hits.add(new Hit(x, y, w, h, action));
    }

    private static String time(double seconds) {
        if (seconds <= 0 || Double.isNaN(seconds)) return "0:00";
        int total = (int) Math.floor(seconds);
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    private String friendly(Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause() : throwable;
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("connection refused") || lower.contains("connect")) {
            return "无法连接接口服务。请确认第三方 API 已启动，或在设置中切换到官方直连。";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) return "接口响应超时，请稍后重试。";
        if (lower.contains("unknownhost") || lower.contains("nodename")) return "无法解析接口地址，请检查网络。";
        return message;
    }

    // ------------------------------------------------------------------ data loading

    private void loadHome(boolean force) {
        if (homeLoading && !force) return;
        homeLoading = true;
        homeError = "";
        long token = ++homeToken;
        api.home().whenComplete((data, error) -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                if (token != homeToken) return;
                homeLoading = false;
                if (error != null) {
                    homeError = friendly(error);
                    return;
                }
                boolean firstLoad = home == null;
                home = data;
                homeError = "";
                if (firstLoad) {
                    scroll = 0;
                    targetScroll = 0;
                    maxScroll = 0;
                }
            });
        });
    }

    private void openPlaylist(long id) {
        requestedPlaylistId = id;
        if (page != Page.PLAYLIST) playlistBack = page;
        page = Page.PLAYLIST;
        pageChangedAt = Util.getMillis();
        scroll = 0;
        targetScroll = 0;
        maxScroll = 0;
        playlistLoading = true;
        playlistError = "";
        playlistDetail = null;
        long token = ++playlistToken;
        api.playlist(id).whenComplete((detail, error) -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                if (token != playlistToken) return;
                playlistLoading = false;
                if (error != null) {
                    playlistError = friendly(error);
                    return;
                }
                playlistDetail = detail;
            });
        });
    }

    private void playPlaylist(long id) {
        if (playlistDetail != null && playlistDetail.playlist().id() == id && !playlistDetail.tracks().isEmpty()) {
            player.playPlaylist(playlistDetail, 0);
            return;
        }
        toast("正在载入歌单…");
        api.playlist(id).whenComplete((detail, error) -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                if (error != null) {
                    toast(friendly(error));
                    return;
                }
                if (detail != null && !detail.tracks().isEmpty()) {
                    player.playPlaylist(detail, 0);
                } else {
                    toast("这个歌单暂时没有可播放的歌曲");
                }
            });
        });
    }

    private void runSearch(String query) {
        String value = query == null ? "" : query.trim();
        if (value.isEmpty()) return;
        page = Page.SEARCH;
        pageChangedAt = Util.getMillis();
        lastQuery = value;
        searchBox.setValue(value);
        searchLoading = true;
        searchError = "";
        searchResult = null;
        scroll = 0;
        targetScroll = 0;
        int token = ++searchToken;
        api.search(value, 30, 0).whenComplete((result, error) -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                if (token != searchToken) return;
                searchLoading = false;
                if (error != null) {
                    searchError = friendly(error);
                    return;
                }
                searchResult = result;
            });
        });
    }

    private int searchToken;

    // ------------------------------------------------------------------ navigation

    private void switchPage(Page target) {
        if (target == Page.SEARCH) {
            // Keep the current query; focusing the field is a natural entry point.
            focusField(searchBox);
        } else {
            defocus();
        }
        if (target == Page.SETTINGS && focusedField != apiUrlBox) {
            apiUrlBox.setValue(ModConfig.get().thirdPartyBaseUrl);
        }
        if (page != target) {
            page = target;
            pageChangedAt = Util.getMillis();
            scroll = 0;
            targetScroll = 0;
            maxScroll = 0;
            pendingMainScroll = -1;
        }
    }

    private void back() {
        if (page == Page.PLAYLIST) {
            page = playlistBack == Page.PLAYLIST ? Page.HOME : playlistBack;
            pageChangedAt = Util.getMillis();
            scroll = 0;
            targetScroll = 0;
            maxScroll = 0;
        } else {
            switchPage(Page.HOME);
        }
    }

    private void focusField(EditBox field) {
        if (field == null) return;
        if (focusedField != null && focusedField != field) focusedField.setFocused(false);
        focusedField = field;
        field.setFocused(true);
    }

    private void defocus() {
        if (focusedField != null) focusedField.setFocused(false);
        focusedField = null;
    }

    private void onSubmitField() {
        if (focusedField == searchBox) {
            runSearch(searchBox.getValue());
        } else if (focusedField == phoneBox || focusedField == passwordBox) {
            submitPhoneLogin();
        } else if (focusedField == cookieBox) {
            submitCookieLogin();
        } else if (focusedField == apiUrlBox) {
            applyApiUrl();
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        this.mouseX = (int) mx;
        this.mouseY = (int) my;
        if (button != 0) return false;
        if (drag != null) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(mx, my)) {
                hit.action().run();
                playClick();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dividerDragging) {
            dividerDragging = false;
            return true;
        }
        if (navBarDragging) {
            navBarDragging = false;
            return true;
        }
        if (drag != null) {
            Drag current = drag;
            drag = null;
            if (current.release != null) current.release.run();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        this.mouseX = (int) mx;
        this.mouseY = (int) my;
        if (dividerDragging) {
            float delta = (float) my - dividerDragStartY;
            sidebarDividerShift = Math.max(dividerMinShift,
                    Math.min(dividerMaxShift, dividerDragStartShift + delta));
            return true;
        }
        if (navBarDragging) {
            float delta = (float) my - navBarDragStartY;
            float track = Math.max(1f, navBarTrackHeight);
            float shift = delta / track * navMaxScroll;
            navScroll = Math.max(0, Math.min(navMaxScroll, navBarDragStartScroll + shift));
            navTargetScroll = navScroll;
            return true;
        }
        if (drag != null) {
            updateDrag(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    private void updateDrag(double mx) {
        if (drag == null) return;
        float fraction = (float) ((mx - drag.x) / drag.w);
        fraction = Math.max(0, Math.min(1, fraction));
        int value = Math.round(drag.min + (drag.max - drag.min) * fraction);
        drag.setter.accept(value);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (lyricsOpen) {
            lyricsManualUntil = Util.getMillis() + 5000;
            lyricsTargetScroll -= (float) scrollY * 42f;
            return true;
        }
        if (loginOpen) return true;
        Layout layout = lastLayout;
        if (layout != null && layout.sidebarW() >= 110 && mx >= layout.x() && mx <= layout.x() + layout.sidebarW()
                && my >= layout.y() && my <= layout.y() + layout.h()) {
            if (my < lastDividerY && navMaxScroll > 0.5f) {
                navTargetScroll = Math.max(0, Math.min(navMaxScroll,
                        navTargetScroll - (float) scrollY * 24f));
            } else {
                sidebarTargetScroll = Math.max(0, Math.min(sidebarMaxScroll,
                        sidebarTargetScroll - (float) scrollY * 32f));
            }
            return true;
        }
        if (layout != null && mx >= layout.mainX() && mx <= layout.mainX() + layout.mainW()
                && my >= layout.mainY() && my <= layout.mainY() + layout.mainH()) {
            targetScroll -= (float) scrollY * 48f;
            targetScroll = Math.max(0, Math.min(maxScroll, targetScroll));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focusedField != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                defocus();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                onSubmitField();
                return true;
            }
            if (focusedField.keyPressed(keyCode, scanCode, modifiers)) return true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (loginOpen) closeLogin();
            else if (lyricsOpen) closeLyrics();
            else onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown()) {
            switchPage(Page.SEARCH);
            focusField(searchBox);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            player.toggle();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            player.seekSeconds(player.positionSeconds() - 5);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            player.seekSeconds(player.positionSeconds() + 5);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_L) {
            if (player.current() != null) {
                if (lyricsOpen) closeLyrics();
                else openLyrics();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (page == Page.SEARCH) runSearch(searchBox.getValue());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (focusedField != null && focusedField.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    private void playClick() {
        // A tiny optional sound would require a registered sound event; the visual response is enough.
    }

    @Override
    public void onClose() {
        saveState();
        ModConfig.get().save();
        releaseQr();
        super.onClose();
    }

    @Override
    public void removed() {
        saveState();
        super.removed();
    }

    private void saveState() {
        savedPage = page;
        if (page == Page.PLAYLIST) {
            savedPlaylistId = requestedPlaylistId;
            savedPlaylistBack = playlistBack;
        }
        savedScroll = scroll;
        savedSidebarScroll = sidebarScroll;
        savedNavScroll = navScroll;
        savedDividerShift = sidebarDividerShift;
        savedValid = true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void toast(String message) {
        toastText = message == null ? "" : message;
        toastAt = Util.getMillis();
    }

    private void drawToast(GuiGraphics g, long now) {
        if (toastText.isBlank() || now - toastAt > 3600) return;
        float age = (now - toastAt) / 1000f;
        float alpha = age < 0.2f ? age / 0.2f : age > 3.1f ? Math.max(0, (3.6f - age) / 0.5f) : 1f;
        float width = Math.min(520, font.width(toastText) + 56);
        float x = (this.width - width) / 2f;
        float y = 18 - (1 - alpha) * 8;
        Glass.rounded(g, x, y, width, 32, 16, Glass.argb((int) (205 * alpha), 12, 14, 21),
                Glass.argb((int) (230 * alpha), 8, 9, 14));
        Glass.circle(g, x + 16, y + 16, 3.2f, Glass.withAlpha(Theme.accent(), (int) (255 * alpha)));
        Text.draw(g, font, Text.ellipsize(font, toastText, width - 38, 0.7f), x + 27, y + 11, 0.7f,
                Glass.argb((int) (255 * alpha), 255, 255, 255));
    }

    // ------------------------------------------------------------------ stubs filled below

    private void openLogin() {
        loginOpen = true;
        loginTab = LoginTab.QR;
        loginError = "";
        loginBusy = false;
        qrStatus = "";
        startQrLogin();
    }

    private void closeLogin() {
        loginOpen = false;
        loginError = "";
        loginBusy = false;
        qrBusy = false;
        qrKey = "";
        qrGeneration++;
        releaseQr();
        defocus();
    }

    private void drawLogin(GuiGraphics g, Layout l, long now) {
        g.fill(0, 0, width, height, 0x9906090F);
        hit(0, 0, width, height, this::closeLogin);
        float pw = Math.min(610, width - 48);
        float ph = Math.min(438, height - 40);
        float px = (width - pw) / 2f;
        float py = (height - ph) / 2f;
        Glass.rounded(g, px, py, pw, ph, 28, 0xEE090C13, 0xFA070A10);
        hit(px, py, pw, ph, () -> {
        });
        Text.draw(g, font, "登录网易云音乐", px + 30, py + 25, 1.32f, Theme.text());
        Text.draw(g, font, "当前后端 · " + api.backend(), px + 31, py + 47, 0.64f, Theme.secondary());
        float closeX = px + pw - 43;
        float closeY = py + 22;
        Glass.circle(g, closeX + 10, closeY + 10, 14, Glass.withAlpha(0xFFFFFFFF, 14));
        Icons.draw(g, Icons.Icon.CLOSE, closeX + 2, closeY + 2, 16, Theme.secondary());
        hit(closeX, closeY, 20, 20, this::closeLogin);

        LoginTab[] tabs = {LoginTab.QR, LoginTab.PHONE, LoginTab.COOKIE};
        String[] labels = {"扫码登录", "手机号", "Cookie"};
        Glass.rounded(g, px + 26, py + 66, 302, 36, 18, Glass.withAlpha(0x000000, 80),
                Glass.withAlpha(0x000000, 28));
        Glass.roundedOutline(g, px + 26.5f, py + 66.5f, 301, 35, 18, 1f, Theme.faint());
        float tabX = px + 30;
        for (int i = 0; i < tabs.length; i++) {
            LoginTab tab = tabs[i];
            float tw = 92;
            boolean selected = loginTab == tab;
            if (selected) {
                Glass.rounded(g, tabX, py + 70, tw, 28, 14, Glass.withAlpha(Theme.accent(), 230),
                        Glass.withAlpha(Theme.accent(), 160));
            }
            Text.centered(g, font, labels[i], tabX + tw / 2f, py + 79, 0.7f,
                    selected ? 0xFFFFFFFF : Theme.secondary());
            hit(tabX, py + 70, tw, 28, () -> {
                loginTab = tab;
                loginError = "";
                if (tab == LoginTab.QR) {
                    if (qrTexture == null && !qrBusy) startQrLogin();
                } else if (tab == LoginTab.PHONE) {
                    focusField(phoneBox);
                } else {
                    focusField(cookieBox);
                }
            });
            tabX += tw + 8;
        }

        float contentY = py + 116;
        if (loginTab == LoginTab.QR) {
            float qs = Math.min(220, ph - 206);
            float qx = px + 32;
            float qy = contentY + 4;
            Glass.rounded(g, qx - 8, qy - 8, qs + 16, qs + 16, 20, Glass.withAlpha(0xFFFFFFFF, 18),
                    Glass.withAlpha(0xFFFFFFFF, 6));
            if (qrTexture != null) {
                g.blit(qrTexture, (int) qx, (int) qy, 0, 0, (int) qs, (int) qs, (int) qs, (int) qs);
            } else {
                Glass.rounded(g, qx, qy, qs, qs, 16, 0xFFF8F9FC, 0xFFF0F2F7);
                if (qrBusy) {
                    spinner(g, qx + qs / 2f, qy + qs / 2f, 20, Theme.accent());
                } else {
                    Icons.draw(g, Icons.Icon.REFRESH, qx + qs / 2f - 16, qy + qs / 2f - 16, 32, Theme.muted());
                }
            }
            float rx = qx + qs + 34;
            float rw = px + pw - rx - 30;
            Text.draw(g, font, "用网易云音乐 App 扫码", rx, contentY + 10, 0.9f, Theme.text());
            Text.draw(g, font, "1  打开手机 App，点击左上角扫一扫", rx, contentY + 36, 0.62f, Theme.secondary());
            Text.draw(g, font, "2  扫描左侧二维码", rx, contentY + 52, 0.62f, Theme.secondary());
            Text.draw(g, font, "3  在手机上确认登录", rx, contentY + 68, 0.62f, Theme.secondary());
            Text.draw(g, font, Text.ellipsize(font, qrStatus.isBlank() ? "正在准备二维码…" : qrStatus,
                    rw, 0.68f), rx, contentY + 102, 0.68f,
                    qrStatus.contains("失败") || qrStatus.contains("过期") ? Theme.danger() : Theme.accent());
            if (!loginError.isBlank()) {
                List<String> lines = Text.wrap(font, loginError, rw, 0.61f);
                for (int i = 0; i < Math.min(2, lines.size()); i++) {
                    Text.draw(g, font, lines.get(i), rx, contentY + 126 + i * 12, 0.61f, Theme.danger());
                }
            }
            Glass.glassButton(g, rx, contentY + 160, 84, 28, 14, false,
                    hovered(rx, contentY + 160, 84, 28));
            Text.centered(g, font, "刷新二维码", rx + 42, contentY + 167, 0.64f, Theme.text());
            hit(rx, contentY + 160, 84, 28, this::startQrLogin);
        } else if (loginTab == LoginTab.PHONE) {
            float fw = Math.min(330, pw - 60);
            Text.draw(g, font, "手机号", px + 32, contentY + 2, 0.64f, Theme.secondary());
            Glass.rounded(g, px + 30, contentY + 16, fw, 36, 12, Glass.withAlpha(0x000000, 34),
                    Glass.withAlpha(0x000000, 12));
            drawField(g, phoneBox, px + 40, contentY + 24, fw - 20, 20, "手机号");
            hit(px + 30, contentY + 16, fw, 36, () -> focusField(phoneBox));
            Text.draw(g, font, "密码", px + 32, contentY + 68, 0.64f, Theme.secondary());
            Glass.rounded(g, px + 30, contentY + 82, fw, 36, 12, Glass.withAlpha(0x000000, 34),
                    Glass.withAlpha(0x000000, 12));
            drawField(g, passwordBox, px + 40, contentY + 90, fw - 20, 20, "密码");
            hit(px + 30, contentY + 82, fw, 36, () -> focusField(passwordBox));
            Glass.glassButton(g, px + 30, contentY + 140, fw, 34, 16, true,
                    hovered(px + 30, contentY + 140, fw, 34));
            Text.centered(g, font, loginBusy ? "登录中…" : "登录", px + 30 + fw / 2f, contentY + 152, 0.75f,
                    0xFFFFFFFF);
            hit(px + 30, contentY + 140, fw, 34, this::submitPhoneLogin);
            Text.draw(g, font, "密码通过加密接口发送，不会保存在本地。", px + 32, contentY + 190, 0.61f, Theme.muted());
            if (!loginError.isBlank()) {
                Text.draw(g, font, Text.ellipsize(font, loginError, fw, 0.64f), px + 32,
                        contentY + 210, 0.64f, Theme.danger());
            }
        } else {
            float fw = pw - 64;
            Text.draw(g, font, "浏览器 Cookie", px + 32, contentY + 2, 0.64f, Theme.secondary());
            Glass.rounded(g, px + 30, contentY + 16, fw, 38, 12, Glass.withAlpha(0x000000, 34),
                    Glass.withAlpha(0x000000, 12));
            drawField(g, cookieBox, px + 40, contentY + 25, fw - 20, 20,
                    "MUSIC_U=...; __csrf=...");
            hit(px + 30, contentY + 16, fw, 38, () -> focusField(cookieBox));
            List<String> help = Text.wrap(font,
                    "在浏览器登录 music.163.com，从开发者工具复制完整 Cookie。也可以只粘贴包含 MUSIC_U 的片段。"
                            + "Cookie 会明文保存在本地 config/iosmusic/session.json。",
                    fw, 0.62f);
            float hy = contentY + 64;
            for (String line : help) {
                Text.draw(g, font, line, px + 32, hy, 0.62f, Theme.muted());
                hy += 12;
            }
            Glass.glassButton(g, px + 30, contentY + 140, 100, 34, 16, true,
                    hovered(px + 30, contentY + 140, 100, 34));
            Text.centered(g, font, loginBusy ? "验证中…" : "导入 Cookie", px + 80, contentY + 152, 0.7f,
                    0xFFFFFFFF);
            hit(px + 30, contentY + 140, 100, 34, this::submitCookieLogin);
            if (!loginError.isBlank()) {
                Text.draw(g, font, Text.ellipsize(font, loginError, fw, 0.64f), px + 32,
                        contentY + 190, 0.64f, Theme.danger());
            }
        }
        Text.draw(g, font, "登录信息仅在本机使用 · 可随时退出", px + 30, py + ph - 28, 0.6f, Theme.muted());
    }

    private void spinner(GuiGraphics g, float cx, float cy, float radius, int color) {
        float angle = (Util.getMillis() % 1200) / 1200f * 360f;
        Glass.arc(g, cx, cy, radius, 3f, angle, 105, color);
    }

    private void startQrLogin() {
        if (!loginOpen || qrBusy) return;
        qrBusy = true;
        loginError = "";
        qrStatus = "正在获取二维码…";
        qrKey = "";
        long generation = ++qrGeneration;
        releaseQr();
        api.qrCreate().whenComplete((session, error) -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                if (!loginOpen || generation != qrGeneration || loginTab != LoginTab.QR) return;
                qrBusy = false;
                if (error != null) {
                    qrStatus = "二维码获取失败";
                    loginError = friendly(error);
                    return;
                }
                qrKey = session.key();
                qrTexture = QrCode.create(session.url());
                if (qrTexture == null) {
                    qrStatus = "二维码生成失败";
                    return;
                }
                qrStatus = "等待扫码";
                pollQr(generation);
            });
        });
    }

    private void pollQr(long generation) {
        if (!loginOpen || loginTab != LoginTab.QR || generation != qrGeneration || qrKey.isBlank() || qrPolling) {
            return;
        }
        qrPolling = true;
        java.util.concurrent.CompletableFuture.delayedExecutor(2, java.util.concurrent.TimeUnit.SECONDS)
                .execute(() -> {
                    if (!loginOpen || loginTab != LoginTab.QR || generation != qrGeneration) {
                        qrPolling = false;
                        return;
                    }
                    api.qrCheck(qrKey).whenComplete((status, error) -> {
                        if (minecraft == null) return;
                        minecraft.execute(() -> {
                            if (!loginOpen || loginTab != LoginTab.QR || generation != qrGeneration) {
                                qrPolling = false;
                                return;
                            }
                            qrPolling = false;
                            if (error != null) {
                                qrStatus = "网络波动，重试中…";
                                pollQr(generation);
                                return;
                            }
                            int code = status.code();
                            if (code == 800) {
                                qrStatus = "二维码已过期，请刷新";
                            } else if (code == 801) {
                                qrStatus = "等待扫码";
                                pollQr(generation);
                            } else if (code == 802) {
                                qrStatus = "已扫码，请在手机上确认";
                                pollQr(generation);
                            } else if (code == 803) {
                                if (status.profile() != null) {
                                    loginSuccess(status.profile());
                                } else {
                                    api.loginStatus().whenComplete((profile, profileError) -> {
                                        if (minecraft == null) return;
                                        minecraft.execute(() -> {
                                            if (profile != null) loginSuccess(profile);
                                            else loginError = "登录成功但读取账号失败，请重试";
                                        });
                                    });
                                }
                            } else {
                                qrStatus = status.message().isBlank() ? "等待扫码" : status.message();
                                pollQr(generation);
                            }
                        });
                    });
                });
    }

    private void submitPhoneLogin() {
        if (loginBusy) return;
        String phone = phoneBox.getValue().trim();
        String password = passwordBox.getValue();
        if (phone.length() < 6 || password.length() < 1) {
            loginError = "请输入有效的手机号和密码";
            return;
        }
        loginBusy = true;
        loginError = "";
        api.loginCellphone(phone, password, "86").whenComplete((result, error) -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                loginBusy = false;
                if (error != null) {
                    loginError = friendly(error);
                    return;
                }
                if (!result.success()) {
                    loginError = result.message();
                    return;
                }
                loginSuccess(result.profile());
            });
        });
    }

    private void submitCookieLogin() {
        if (loginBusy) return;
        String cookie = cookieBox.getValue().trim();
        if (cookie.isEmpty()) {
            loginError = "请粘贴 Cookie";
            return;
        }
        loginBusy = true;
        loginError = "";
        api.loginCookie(cookie).whenComplete((result, error) -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                loginBusy = false;
                if (error != null) {
                    loginError = friendly(error);
                    return;
                }
                if (!result.success()) {
                    loginError = result.message();
                    return;
                }
                loginSuccess(result.profile());
            });
        });
    }

    private void loginSuccess(Models.UserProfile profile) {
        closeLogin();
        page = Page.LIBRARY;
        pageChangedAt = Util.getMillis();
        toast(profile == null ? "登录成功" : "欢迎回来，" + profile.displayName());
        home = null;
        loadHome(true);
    }

    private void releaseQr() {
        if (qrTexture != null && minecraft != null) {
            try {
                minecraft.getTextureManager().release(qrTexture);
            } catch (Exception ignored) {
            }
        }
        qrTexture = null;
    }

    private void drawLyrics(GuiGraphics g, Layout l, long now) {
        ModConfig config = ModConfig.get();
        hit(0, 0, width, height, this::closeLyrics);
        float margin = width < 800 ? 18 : 44;
        float px = margin;
        float py = margin;
        float pw = width - margin * 2;
        float ph = height - margin * 2;
        int tint = Glass.argb((int) (config.lyricsOpacity * 2.55f), 9, 11, 17);
        if (config.lyricsGlass) {
            GlassSnapshot.captureLyricsBlur(config.lyricsBlur);
            boolean glass = GlassRefraction.drawLyrics(g, px, py, pw, ph, 30, tint);
            if (!glass) Glass.rounded(g, px, py, pw, ph, 30, tint, tint);
        } else {
            // Liquid-glass refraction is off, but the Gaussian blur is still available.
            GlassSnapshot.captureLyricsBlur(config.lyricsBlur);
            boolean frosted = GlassRefraction.drawLyricsFrosted(g, px, py, pw, ph, 30, tint);
            if (!frosted) Glass.rounded(g, px, py, pw, ph, 30, tint, tint);
        }
        hit(px, py, pw, ph, () -> {
        });
        Models.Song song = player.current();
        Text.draw(g, font, "歌词", px + 32, py + 25, 1.1f, Theme.text());
        String songLine = song == null ? "未在播放" : song.name() + "  ·  " + song.artist();
        Text.draw(g, font, Text.ellipsize(font, songLine, pw - 150, 0.68f), px + 32, py + 46, 0.68f,
                Theme.secondary());
        float cx = px + pw - 42;
        float cy = py + 23;
        Glass.circle(g, cx + 9, cy + 9, 14, Glass.withAlpha(0xFFFFFFFF, 14));
        Icons.draw(g, Icons.Icon.CLOSE, cx + 1, cy + 1, 16, Theme.secondary());
        hit(cx - 4, cy - 4, 26, 26, this::closeLyrics);

        float art = Math.min(170, ph * 0.3f);
        float ax = px + 34;
        float ay = py + 92;
        if (song != null) {
            cover(g, song.coverUrl(), ax, ay, art, 20);
            Glass.roundedOutline(g, ax + 0.5f, ay + 0.5f, art - 1, art - 1, 20, 1f,
                    Glass.withAlpha(0xFFFFFFFF, 40));
            Text.draw(g, font, Text.ellipsize(font, song.name(), art, 0.95f), ax, ay + art + 14, 0.95f,
                    Theme.text());
            Text.draw(g, font, Text.ellipsize(font, song.artist(), art, 0.66f), ax, ay + art + 31, 0.66f,
                    Theme.secondary());
        }

        float lx = px + 34 + art + 38;
        float ly = py + 92;
        float lw = px + pw - 34 - lx;
        float lh = ph - 132;
        if (lw < 160) {
            lx = px + 34;
            lw = pw - 68;
            ly = ay + art + 62;
            lh = py + ph - 40 - ly;
        }
        Glass.rounded(g, lx, ly, lw, lh, 18, Glass.withAlpha(0x000000, 22),
                Glass.withAlpha(0x000000, 8));
        Models.Lyrics lyrics = player.lyrics();
        if (lyrics.lines().isEmpty()) {
            Icons.equalizer(g, lx + lw / 2f - 26, ly + lh / 2f - 14, 52, 28, Theme.accent(), now);
            Text.centered(g, font, song == null ? "选择一首歌开始" : "暂无歌词", lx + lw / 2f,
                    ly + lh / 2f + 24, 0.8f, Theme.secondary());
        } else {
            float lineH = 38;
            int active = lyrics.indexAt(player.positionSeconds());
            float max = Math.max(0, lyrics.lines().size() * lineH - lh + 50);
            if (now > lyricsManualUntil) {
                lyricsTargetScroll = active * lineH - lh * 0.42f;
            }
            lyricsTargetScroll = Math.max(0, Math.min(max, lyricsTargetScroll));
            lyricsScroll += (lyricsTargetScroll - lyricsScroll) * 0.14f;
            g.enableScissor((int) lx + 1, (int) ly + 1, (int) (lx + lw) - 1, (int) (ly + lh) - 1);
            for (int i = 0; i < lyrics.lines().size(); i++) {
                Models.LyricLine line = lyrics.lines().get(i);
                float rowY = ly + 32 + i * lineH - lyricsScroll;
                if (rowY < ly - lineH || rowY > ly + lh) continue;
                boolean isActive = i == active;
                if (isActive) {
                    Glass.rounded(g, lx + 12, rowY - 7, lw - 24, lineH - 4, 12,
                            Glass.withAlpha(Theme.accent(), 32), Glass.withAlpha(Theme.accent(), 6));
                    Glass.rounded(g, lx + 12, rowY + 4, 3, 18, 1.5f, Theme.accent());
                }
                int color = isActive ? Theme.text() : Theme.secondary();
                float scale = isActive ? 1.25f : 0.92f;
                Text.draw(g, font, Text.ellipsize(font, line.text(), lw - 64, scale), lx + 28, rowY, scale, color);
                if (!line.translation().isBlank()) {
                    Text.draw(g, font, Text.ellipsize(font, line.translation(), lw - 64, 0.66f),
                            lx + 28, rowY + (isActive ? 22 : 17), 0.66f,
                            isActive ? Theme.secondary() : Theme.muted());
                }
            }
            g.disableScissor();
        }
        Text.draw(g, font, "滚轮浏览 · L 关闭", px + pw - 116, py + ph - 24, 0.58f, Theme.muted());
    }

    private void logout() {
        api.logout().whenComplete((ignored, error) -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                home = null;
                loadHome(true);
                toast("已退出登录");
            });
        });
    }

    private void applyApiUrl() {
        String value = apiUrlBox.getValue().trim();
        if (value.isEmpty()) {
            toast("请输入第三方 API 地址");
            return;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        ModConfig config = ModConfig.get();
        config.thirdPartyBaseUrl = value;
        config.save();
        defocus();
        api.setMode("THIRD_PARTY");
        player.onApiChanged();
        home = null;
        loadHome(true);
        toast("正在连接第三方 API");
    }

    private float drawSettings(GuiGraphics g, float x, float y, float w, long now) {
        float yy = y;
        Models.UserProfile profile = api.profile();
        Glass.rounded(g, x, yy, w, 76, 18, Glass.withAlpha(0xFFFFFFFF, 16),
                Glass.withAlpha(0xFFFFFFFF, 5));
        if (api.isLoggedIn() && profile != null) {
            avatar(g, profile.avatarUrl(), x + 14, yy + 14, 44);
            Text.draw(g, font, profile.displayName(), x + 70, yy + 15, 1.0f, Theme.text());
            Text.draw(g, font, "已登录 · " + api.backend(), x + 70, yy + 34, 0.64f, Theme.secondary());
            Glass.glassButton(g, x + w - 86, yy + 24, 68, 27, 13, false, hovered(x + w - 86, yy + 24, 68, 27));
            Text.centered(g, font, "退出登录", x + w - 52, yy + 31, 0.66f, Theme.secondary());
            hit(x + w - 86, yy + 24, 68, 27, this::logout);
        } else {
            Glass.circle(g, x + 38, yy + 38, 22, Glass.withAlpha(Theme.accent(), 42));
            Icons.draw(g, Icons.Icon.USER, x + 27, yy + 27, 23, 0xFFFFFFFF);
            Text.draw(g, font, "登录网易云音乐", x + 70, yy + 17, 0.95f, Theme.text());
            Text.draw(g, font, "扫码、手机号或 Cookie 登录", x + 70, yy + 36, 0.63f, Theme.secondary());
            Glass.glassButton(g, x + w - 86, yy + 24, 68, 27, 13, true, hovered(x + w - 86, yy + 24, 68, 27));
            Text.centered(g, font, "登录", x + w - 52, yy + 31, 0.68f, 0xFFFFFFFF);
            hit(x + w - 86, yy + 24, 68, 27, this::openLogin);
        }
        yy += 94;

        yy = settingsSection(g, "接口", x, yy, w);
        ModConfig config = ModConfig.get();
        String mode = config.apiMode;
        String[] modes = {"AUTO", "OFFICIAL", "THIRD_PARTY"};
        String[] labels = {"自动", "官方直连", "第三方 API"};
        Glass.rounded(g, x, yy, w, 38, 14, Glass.withAlpha(0x000000, 38),
                Glass.withAlpha(0x000000, 10));
        float segment = (w - 6) / 3f;
        for (int i = 0; i < 3; i++) {
            float bx = x + 3 + i * segment;
            boolean selected = mode.equals(modes[i]);
            if (selected) {
                Glass.rounded(g, bx, yy + 3, segment - 2, 32, 11, Theme.accent(),
                        Glass.mix(Theme.accent(), 0xFF000000, 0.25f));
            }
            Text.centered(g, font, labels[i], bx + (segment - 2) / 2f, yy + 14, 0.7f,
                    selected ? 0xFFFFFFFF : Theme.secondary());
            int index = i;
            hit(bx, yy, segment, 38, () -> {
                defocus();
                api.setMode(modes[index]);
                player.onApiChanged();
                home = null;
                loadHome(true);
                toast("接口已切换为" + labels[index]);
            });
        }
        yy += 48;
        Text.draw(g, font, "当前连接 · " + api.backend(), x, yy, 0.65f, Theme.secondary());
        yy += 19;

        if (mode.equals("THIRD_PARTY") || mode.equals("AUTO")) {
            Text.draw(g, font, "第三方 API 地址", x, yy, 0.66f, Theme.muted());
            yy += 14;
            float fieldW = w - 84;
            Glass.rounded(g, x, yy, fieldW, 34, 12, Glass.withAlpha(0x000000, 28),
                    Glass.withAlpha(0x000000, 10));
            drawField(g, apiUrlBox, x + 9, yy + 7, fieldW - 18, 20, "http://127.0.0.1:3000");
            hit(x, yy, fieldW, 34, () -> focusField(apiUrlBox));
            Glass.glassButton(g, x + fieldW + 8, yy + 2, 72, 30, 14, false,
                    hovered(x + fieldW + 8, yy + 2, 72, 30));
            Text.centered(g, font, "连接", x + fieldW + 44, yy + 11, 0.7f, Theme.text());
            hit(x + fieldW + 8, yy + 2, 72, 30, this::applyApiUrl);
            yy += 42;
            Text.draw(g, font, "默认端口 3000；也可在 config/iosmusic/config.json 配置请求头。",
                    x, yy, 0.58f, Theme.muted());
            yy += 22;
        }

        yy = settingsSection(g, "外观", x, yy + 2, w);
        yy = settingSlider(g, x, yy, w, "玻璃透明度", "与灵动岛相同的透明度处理；越高越通透",
                config.glassTransparency + "%", config.glassTransparency, 0, 100,
                value -> {
                    config.glassTransparency = value;
                }, config::save);
        yy = settingSlider(g, x, yy, w, "玻璃模糊", "只模糊玻璃面板后的画面，不改变视野边缘",
                String.valueOf(config.blur), config.blur, 0, 10,
                value -> {
                    config.blur = value;
                }, config::save);
        yy = settingSlider(g, x, yy, w, "音量", "独立于游戏声音，使用系统音频输出",
                config.volume + "%", config.volume, 0, 100,
                value -> player.setVolume(value, false), config::save);
        yy = settingToggle(g, x, yy, w, "歌词液体玻璃", "关闭后仍保留高斯模糊，只去掉折射与彩色边缘",
                config.lyricsGlass, () -> {
                    config.lyricsGlass = !config.lyricsGlass;
                    config.save();
                });
        yy = settingSlider(g, x, yy, w, "歌词透明度", "0 为完全透明，100 为完全不透明",
                config.lyricsOpacity + "%", config.lyricsOpacity, 0, 100,
                value -> {
                    config.lyricsOpacity = value;
                }, config::save);
        yy = settingSlider(g, x, yy, w, "歌词模糊", "与全局玻璃使用同款模糊方式，但可单独调节",
                String.valueOf(config.lyricsBlur), config.lyricsBlur, 0, 10,
                value -> {
                    config.lyricsBlur = value;
                }, config::save);
        yy = settingToggle(g, x, yy, w, "游戏内悬浮窗", "顶部显示迷你播放器",
                config.hudEnabled, () -> {
                    config.hudEnabled = !config.hudEnabled;
                    config.save();
                });
        yy = settingToggle(g, x, yy, w, "悬浮窗歌词", "第二行显示当前歌词，替换歌手名",
                config.hudLyrics, () -> {
                    config.hudLyrics = !config.hudLyrics;
                    config.save();
                });
        yy = settingToggle(g, x, yy, w, "悬浮窗翻译", "歌词下方显示翻译；不会加宽灵动岛",
                config.hudTranslation, () -> {
                    config.hudTranslation = !config.hudTranslation;
                    config.save();
                });
        yy = settingToggle(g, x, yy, w, "封面旋转", "播放时旋转灵动岛圆形封面",
                config.hudCoverSpin, () -> {
                    config.hudCoverSpin = !config.hudCoverSpin;
                    config.save();
                });
        yy = settingSlider(g, x, yy, w, "封面旋转速度", "0 为停止，数值越大转得越快",
                config.hudCoverSpinSpeed + "°/s", config.hudCoverSpinSpeed, 0, 120,
                value -> {
                    config.hudCoverSpinSpeed = value;
                }, config::save);
        yy = settingSlider(g, x, yy, w, "悬浮窗上下位置", "关闭自动避让时使用；负值上移，正值下移",
                config.hudOffsetY + "px", config.hudOffsetY, -20, 140,
                value -> {
                    config.hudOffsetY = value;
                }, config::save);
        yy = settingToggle(g, x, yy, w, "自动避开 Jade", "根据 Jade 提示框高度自动下移灵动岛",
                config.hudAutoJade, () -> {
                    config.hudAutoJade = !config.hudAutoJade;
                    config.save();
                });
        yy = settingSlider(g, x, yy, w, "悬浮窗透明度", "0 为完全透明，100 为完全不透明",
                config.hudOpacity + "%", config.hudOpacity, 0, 100,
                value -> {
                    config.hudOpacity = value;
                }, config::save);
        yy = settingSlider(g, x, yy, w, "悬浮窗模糊", "只影响灵动岛后面的画面",
                String.valueOf(config.hudBlur), config.hudBlur, 0, 10,
                value -> {
                    config.hudBlur = value;
                }, config::save);
        yy = settingToggle(g, x, yy, w, "减少动态效果", "关闭环境光晕漂移",
                config.reduceMotion, () -> {
                    config.reduceMotion = !config.reduceMotion;
                    config.save();
                });

        Text.draw(g, font, "强调色", x, yy + 3, 0.72f, Theme.secondary());
        String[] accents = {"#0A84FF", "#9B7BFF", "#FF5E9A", "#2ED6C9", "#FF9F0A", "#34D399"};
        float dotX = x + 86;
        for (String accent : accents) {
            int color = parseColor(accent);
            boolean selected = config.accent.equalsIgnoreCase(accent);
            Glass.circle(g, dotX + 10, yy + 8, selected ? 10 : 8, color);
            if (selected) Glass.ring(g, dotX + 10, yy + 8, 13, 2f, Glass.withAlpha(0xFFFFFFFF, 180));
            hit(dotX - 2, yy - 4, 24, 24, () -> {
                config.accent = accent;
                config.save();
            });
            dotX += 30;
        }
        yy += 30;

        yy = settingsSection(g, "关于", x, yy + 4, w);
        Text.draw(g, font, "iOS Music 1.0.0", x, yy, 0.7f, Theme.text());
        Text.draw(g, font, "NeoForge 1.21.1 · 官方直连与第三方 API 双后端", x, yy + 15, 0.6f, Theme.muted());
        Text.draw(g, font, "登录 Cookie 保存在本地 config/iosmusic/session.json，不会上传。",
                x, yy + 30, 0.6f, Theme.muted());
        Text.draw(g, font, "接口内容版权归网易云音乐及相应权利人所有。", x, yy + 45, 0.6f, Theme.muted());
        yy += 68;
        return yy - y + 8;
    }

    private float settingsSection(GuiGraphics g, String title, float x, float y, float w) {
        Text.draw(g, font, title, x, y, 0.9f, Theme.secondary());
        Glass.line(g, x, y + 16, x + w, y + 16, 1f, Theme.faint());
        return y + 26;
    }

    private float settingSlider(GuiGraphics g, float x, float y, float w, String label, String description,
                                String valueText, int value, int min, int max,
                                IntConsumer setter, Runnable release) {
        Text.draw(g, font, label, x, y, 0.76f, Theme.text());
        float valueW = font.width(valueText);
        Text.draw(g, font, valueText, x + w - valueW, y, 0.67f, Theme.secondary());
        Text.draw(g, font, description, x, y + 13, 0.57f, Theme.muted());
        float trackY = y + 31;
        float trackW = w - 4;
        Glass.rounded(g, x + 2, trackY, trackW, 5, 2.5f, Glass.withAlpha(0xFFFFFFFF, 20));
        float fraction = (value - min) / (float) Math.max(1, max - min);
        if (fraction > 0.002f) {
            Glass.rounded(g, x + 2, trackY, trackW * fraction, 5, 2.5f, Theme.accent());
        }
        Glass.circle(g, x + 2 + trackW * fraction, trackY + 2.5f, 5.2f, 0xF2FFFFFF);
        Glass.circle(g, x + 2 + trackW * fraction, trackY + 2.5f, 7.5f, Glass.withAlpha(Theme.accent(), 45));
        hit(x, y, w, 42, () -> {
            drag = new Drag(x + 2, trackW, min, max, setter, release);
            updateDrag(mouseX);
        });
        return y + 46;
    }

    private float settingToggle(GuiGraphics g, float x, float y, float w, String label, String description,
                                boolean value, Runnable action) {
        Text.draw(g, font, label, x, y + 4, 0.76f, Theme.text());
        Text.draw(g, font, description, x, y + 17, 0.57f, Theme.muted());
        float switchW = 38;
        float sx = x + w - switchW;
        float sy = y + 7;
        Glass.rounded(g, sx, sy, switchW, 22, 11,
                value ? Theme.accent() : Glass.withAlpha(0xFFFFFFFF, 28),
                value ? Glass.mix(Theme.accent(), 0xFF000000, 0.2f) : Glass.withAlpha(0xFFFFFFFF, 12));
        float knobX = value ? sx + switchW - 18 : sx + 4;
        Glass.circle(g, knobX + 7, sy + 11, 8, 0xFFFFFFFF);
        hit(x, y, w, 36, action);
        return y + 42;
    }

    private static int parseColor(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Exception e) {
            return 0xFF0A84FF;
        }
    }

    private void openLyrics() {
        lyricsOpen = true;
        lyricsManualUntil = 0;
        lyricsScroll = 0;
        lyricsTargetScroll = 0;
    }

    private void closeLyrics() {
        lyricsOpen = false;
    }
}
