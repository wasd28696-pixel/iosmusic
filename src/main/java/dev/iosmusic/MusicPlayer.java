package dev.iosmusic;

import dev.iosmusic.api.ApiClient;
import dev.iosmusic.api.Models;
import dev.iosmusic.audio.AudioCache;
import dev.iosmusic.audio.AudioEngine;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Owns the queue, resolves songs and drives the audio engine. All public mutators run on the client thread. */
public final class MusicPlayer {
    public enum Playback {IDLE, RESOLVING, PLAYING, PAUSED, ERROR, ENDED}

    private static final MusicPlayer INSTANCE = new MusicPlayer();
    private static final ExecutorService RESOLVER = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "iOSMusic-Resolver");
            thread.setDaemon(true);
            return thread;
        }
    });

    private final AudioEngine audio = new AudioEngine();
    private final AtomicLong generation = new AtomicLong();
    private final List<Models.Song> queue = new ArrayList<>();
    private volatile Models.Song current;
    private volatile Playback state = Playback.IDLE;
    private volatile String message = "";
    private volatile String contextTitle = "";
    private volatile int index = -1;
    private volatile boolean shuffle;
    private volatile boolean repeatOne;
    private volatile boolean repeatAll = true;
    private volatile Models.Lyrics lyrics = Models.Lyrics.empty();
    private volatile long lyricSongId;
    private volatile Path currentPath;
    private volatile Consumer<String> toast = text -> {
    };

    private MusicPlayer() {
        ModConfig config = ModConfig.get();
        audio.setVolume(config.volume / 100f);
        audio.setListener(new AudioEngine.Listener() {
            @Override
            public void onEnded() {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft == null) return;
                minecraft.execute(MusicPlayer.this::handleEnded);
            }

            @Override
            public void onError(String error) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft == null) return;
                minecraft.execute(() -> {
                    state = Playback.ERROR;
                    message = error;
                    toast.accept(error);
                });
            }
        });
    }

    public static MusicPlayer get() {
        return INSTANCE;
    }

    public void setToast(Consumer<String> toast) {
        this.toast = toast == null ? text -> {
        } : toast;
    }

    public Models.Song current() {
        return current;
    }

    public List<Models.Song> queue() {
        return Collections.unmodifiableList(queue);
    }

    public int index() {
        return index;
    }

    public String contextTitle() {
        return contextTitle;
    }

    public Playback state() {
        return state;
    }

    public String message() {
        return message;
    }

    public Models.Lyrics lyrics() {
        return lyrics;
    }

    public boolean shuffle() {
        return shuffle;
    }

    public boolean repeatOne() {
        return repeatOne;
    }

    public boolean repeatAll() {
        return repeatAll;
    }

    public double positionSeconds() {
        double position = audio.positionSeconds();
        double duration = durationSeconds();
        if (duration > 0) position = Math.min(position, duration);
        return Math.max(0, position);
    }

    public double durationSeconds() {
        Models.Song song = current;
        return song == null ? 0 : Math.max(0, song.durationMs() / 1000.0);
    }

    public double progress() {
        double duration = durationSeconds();
        return duration <= 0 ? 0 : Math.max(0, Math.min(1, positionSeconds() / duration));
    }

    public int volume() {
        return ModConfig.get().volume;
    }

    public void setVolume(int value) {
        setVolume(value, true);
    }

    public void setVolume(int value, boolean persist) {
        int clamped = Math.max(0, Math.min(100, value));
        ModConfig.get().volume = clamped;
        if (persist) ModConfig.get().save();
        audio.setVolume(clamped / 100f);
    }

    public void playSong(Models.Song song, List<Models.Song> context, String title) {
        if (song == null) return;
        List<Models.Song> songs = new ArrayList<>(context == null ? List.of() : context);
        if (songs.isEmpty()) songs.add(song);
        int found = -1;
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id() == song.id()) {
                found = i;
                break;
            }
        }
        if (found < 0) {
            songs.add(0, song);
            found = 0;
        }
        queue.clear();
        queue.addAll(songs);
        index = found;
        current = queue.get(found);
        contextTitle = title == null ? "正在播放" : title;
        startCurrent();
    }

    public void playPlaylist(Models.PlaylistDetail detail, int startIndex) {
        if (detail == null || detail.tracks().isEmpty()) {
            toast.accept("这个歌单暂时没有可播放的歌曲");
            return;
        }
        int start = Math.max(0, Math.min(detail.tracks().size() - 1, startIndex));
        playSong(detail.tracks().get(start), detail.tracks(), detail.playlist().name());
    }

    public void playQueue(List<Models.Song> songs, String title, int startIndex) {
        if (songs == null || songs.isEmpty()) return;
        int start = Math.max(0, Math.min(songs.size() - 1, startIndex));
        playSong(songs.get(start), songs, title);
    }

    public void toggle() {
        switch (state) {
            case PLAYING, PAUSED -> {
                audio.togglePause();
                state = state == Playback.PLAYING ? Playback.PAUSED : Playback.PLAYING;
            }
            case RESOLVING -> {
            }
            case IDLE, ERROR, ENDED -> {
                if (current != null) startCurrent();
                else if (!queue.isEmpty()) {
                    index = 0;
                    current = queue.get(0);
                    startCurrent();
                }
            }
        }
    }

    public void next() {
        advance(false, true);
    }

    public void previous() {
        if (queue.isEmpty()) return;
        if (positionSeconds() > 3.0) {
            seek(0);
            return;
        }
        int target = index - 1;
        if (target < 0) target = queue.size() - 1;
        index = target;
        current = queue.get(target);
        startCurrent();
    }

    public void seek(double fraction) {
        double duration = durationSeconds();
        if (duration <= 0) return;
        double target = Math.max(0, Math.min(0.995, fraction)) * duration;
        if (state == Playback.PLAYING || state == Playback.PAUSED) {
            audio.seek(target);
        }
    }

    public void seekSeconds(double seconds) {
        double duration = durationSeconds();
        if (duration <= 0) return;
        if (state == Playback.PLAYING || state == Playback.PAUSED) {
            audio.seek(Math.max(0, Math.min(duration - 0.5, seconds)));
        }
    }

    public void toggleShuffle() {
        shuffle = !shuffle;
    }

    public void cycleRepeat() {
        if (!repeatAll && !repeatOne) {
            repeatAll = true;
            repeatOne = false;
        } else if (repeatAll) {
            repeatAll = false;
            repeatOne = true;
        } else {
            repeatAll = false;
            repeatOne = false;
        }
    }

    public String repeatLabel() {
        if (repeatOne) return "单曲循环";
        if (repeatAll) return "列表循环";
        return "顺序播放";
    }

    public void stop() {
        generation.incrementAndGet();
        audio.stop();
        state = Playback.IDLE;
        message = "";
    }

    public void clear() {
        stop();
        queue.clear();
        current = null;
        index = -1;
        contextTitle = "";
        lyrics = Models.Lyrics.empty();
        lyricSongId = 0;
        currentPath = null;
    }

    public void onApiChanged() {
        clear();
    }

    private void startCurrent() {
        Models.Song song = current;
        if (song == null) return;
        long gen = generation.incrementAndGet();
        state = Playback.RESOLVING;
        message = "正在获取音源…";
        lyrics = Models.Lyrics.empty();
        lyricSongId = song.id();
        loadLyrics(song.id(), gen);
        currentPath = null;
        RESOLVER.submit(() -> resolve(song, gen));
    }

    private void resolve(Models.Song song, long gen) {
        try {
            Models.Song resolved = song;
            if (song.coverUrl() == null || song.coverUrl().isBlank()
                    || song.durationMs() <= 0 || song.album() == null || song.album().isBlank()) {
                try {
                    List<Models.Song> details = ApiClient.get().api().songDetails(List.of(song.id()));
                    if (!details.isEmpty()) resolved = details.get(0);
                } catch (Exception ignored) {
                }
            }
            if (gen != generation.get()) return;
            Models.SongUrl songUrl = ApiClient.get().api().songUrl(song.id());
            if (!songUrl.playable()) {
                failOnClient(gen, songUrl.message().isBlank() ? "没有可用的播放地址" : songUrl.message());
                return;
            }
            if (gen != generation.get()) return;
            Path path = AudioCache.fetch(song.id(), songUrl.url());
            if (gen != generation.get()) return;
            Models.Song finalSong = resolved;
            Minecraft.getInstance().execute(() -> {
                if (gen != generation.get()) return;
                updateCurrent(finalSong);
                currentPath = path;
                state = Playback.PLAYING;
                message = songUrl.trial() ? "当前为试听片段" : "";
                audio.play(path, 0);
                if (songUrl.trial()) toast.accept("当前歌曲为试听片段");
            });
        } catch (Exception e) {
            if (gen != generation.get()) return;
            Throwable cause = e.getCause() == null ? e : e.getCause();
            String detail = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            failOnClient(gen, "播放失败: " + detail);
        }
    }

    private void failOnClient(long gen, String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        minecraft.execute(() -> {
            if (gen != generation.get()) return;
            state = Playback.ERROR;
            message = text;
            toast.accept(text);
        });
    }

    private void updateCurrent(Models.Song updated) {
        if (updated == null) return;
        current = updated;
        int currentIndex = index;
        if (currentIndex >= 0 && currentIndex < queue.size()) queue.set(currentIndex, updated);
    }

    private void loadLyrics(long songId, long gen) {
        RESOLVER.submit(() -> {
            try {
                Models.Lyrics result = ApiClient.get().api().lyrics(songId);
                if (gen != generation.get()) return;
                Minecraft.getInstance().execute(() -> {
                    if (gen != generation.get() || lyricSongId != songId) return;
                    lyrics = result;
                });
            } catch (Exception ignored) {
            }
        });
    }

    private void handleEnded() {
        if (state == Playback.IDLE || state == Playback.ERROR) return;
        if (repeatOne) {
            startCurrent();
            return;
        }
        advance(true, false);
    }

    private void advance(boolean auto, boolean manual) {
        if (queue.isEmpty()) return;
        int target;
        if (shuffle && queue.size() > 1) {
            do {
                target = (int) (Math.random() * queue.size());
            } while (target == index);
        } else {
            target = index + 1;
            if (target >= queue.size()) {
                if (auto && !repeatAll) {
                    state = Playback.ENDED;
                    message = "播放结束";
                    return;
                }
                target = 0;
            }
        }
        index = target;
        current = queue.get(target);
        startCurrent();
    }
}
