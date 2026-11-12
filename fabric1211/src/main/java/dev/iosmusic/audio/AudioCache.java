package dev.iosmusic.audio;

import dev.iosmusic.ModConfig;
import dev.iosmusic.api.Http;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Downloads MP3 streams once and keeps them in a bounded local cache. */
public final class AudioCache {
    private static final ExecutorService DOWNLOADS = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "iOSMusic-Download");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<Long, Object> LOCKS = new ConcurrentHashMap<>();
    private static volatile boolean cleaned;

    private AudioCache() {
    }

    public static Path directory() {
        return ModConfig.directory().resolve("cache").resolve("audio");
    }

    public static Path fetch(long songId, String url) throws Exception {
        if (url == null || url.isBlank()) throw new IOException("音源地址为空");
        Files.createDirectories(directory());
        sweepOnce();
        Path target = directory().resolve(songId + ".mp3");
        if (isUsable(target)) return target;
        Object lock = LOCKS.computeIfAbsent(songId, id -> new Object());
        synchronized (lock) {
            if (isUsable(target)) return target;
            Path part = directory().resolve(songId + ".part");
            try {
                Files.deleteIfExists(part);
                Http.download(url, part, Map.of(
                        "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36",
                        "Referer", "https://music.163.com/",
                        "Accept", "*/*"));
                if (!isUsable(part)) {
                    throw new IOException("下载的文件不是有效的 MP3");
                }
                try {
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception e) {
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return target;
            } finally {
                try {
                    Files.deleteIfExists(part);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static boolean isUsable(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) < 2048) return false;
            byte[] head = new byte[3];
            try (var in = Files.newInputStream(file)) {
                if (in.read(head) != 3) return false;
            }
            boolean id3 = head[0] == 'I' && head[1] == 'D' && head[2] == '3';
            boolean frame = (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0;
            return id3 || frame;
        } catch (IOException e) {
            return false;
        }
    }

    private static void sweepOnce() {
        if (cleaned) return;
        cleaned = true;
        try (var stream = Files.list(directory())) {
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
            stream.sorted(Comparator.comparingLong(path -> {
                try {
                    return Files.getLastModifiedTime(path).toMillis();
                } catch (IOException e) {
                    return 0L;
                }
            })).forEach(path -> {
                try {
                    if (Files.getLastModifiedTime(path).toMillis() < cutoff
                            || Files.size(path) == 0) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
