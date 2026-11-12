package dev.iosmusic.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.iosmusic.ModConfig;
import dev.iosmusic.api.Http;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Downloads, crops and uploads album art and avatars with rounded alpha masks. */
public final class CoverCache {
    public static final class Entry {
        public volatile ResourceLocation location;
        public volatile int color = 0xFF3A6EA5;
        public volatile boolean loading = true;
        public volatile boolean failed;
        public volatile boolean started;
    }

    private record Ready(String key, Entry entry, NativeImage image, int color) {
    }

    private static final CoverCache INSTANCE = new CoverCache();
    private static final ExecutorService DOWNLOAD = Executors.newFixedThreadPool(3, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "iOSMusic-Cover-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });
    private static final ConcurrentLinkedQueue<Ready> READY = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private CoverCache() {
    }

    public static CoverCache get() {
        return INSTANCE;
    }

    public Entry entry(String url, boolean avatar) {
        return entry(url, avatar, false);
    }

    public Entry entryCircle(String url) {
        return entry(url, false, true);
    }

    private Entry entry(String url, boolean avatar, boolean circular) {
        if (url == null || url.isBlank()) return null;
        String key = (avatar ? "a:" : circular ? "r:" : "c:") + url;
        Entry entry = ENTRIES.computeIfAbsent(key, k -> new Entry());
        if (!entry.started && !entry.failed) {
            entry.started = true;
            DOWNLOAD.submit(() -> download(key, url, avatar, circular, entry));
        }
        return entry;
    }

    public ResourceLocation texture(String url, boolean avatar) {
        Entry entry = entry(url, avatar);
        return entry == null ? null : entry.location;
    }

    public ResourceLocation textureCircle(String url) {
        Entry entry = entryCircle(url);
        return entry == null ? null : entry.location;
    }

    public int color(String url) {
        Entry entry = ENTRIES.get("c:" + url);
        if (entry != null) return entry.color;
        entry = ENTRIES.get("a:" + url);
        return entry == null ? ModConfig.get().accentColor() : entry.color;
    }

    public void tick() {
        for (int i = 0; i < 3; i++) {
            Ready ready = READY.poll();
            if (ready == null) break;
            try {
                DynamicTexture texture = new DynamicTexture(ready.image());
                texture.setFilter(true, false);
                ResourceLocation id = Minecraft.getInstance().getTextureManager()
                        .register("iosmusic_" + Math.abs(ready.key().hashCode()) + "_" + System.nanoTime(), texture);
                texture.setFilter(true, false);
                ready.entry().location = id;
                ready.entry().color = ready.color();
                ready.entry().loading = false;
                ready.entry().failed = false;
            } catch (Throwable t) {
                try {
                    ready.image().close();
                } catch (Throwable ignored) {
                }
                markFailed(ready.entry());
            }
        }
    }

    private void download(String key, String url, boolean avatar, boolean circular, Entry entry) {
        try {
            byte[] bytes = Http.getBytes(url, Map.of(
                    "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36",
                    "Referer", "https://music.163.com/",
                    "Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"));
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) throw new IllegalArgumentException("unsupported image");
            int size = avatar ? 192 : 512;
            BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = output.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setComposite(AlphaComposite.Src);
            if (avatar || circular) {
                graphics.setClip(new Ellipse2D.Float(0, 0, size, size));
            } else {
                float radius = size * 0.16f;
                graphics.setClip(new RoundRectangle2D.Float(0, 0, size, size, radius * 2, radius * 2));
            }
            int srcW = source.getWidth();
            int srcH = source.getHeight();
            int side = Math.min(srcW, srcH);
            int sx = (srcW - side) / 2;
            int sy = (srcH - side) / 2;
            graphics.drawImage(source, 0, 0, size, size, sx, sy, sx + side, sy + side, null);
            graphics.dispose();

            int color = averageColor(output);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(output, "png", png);
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
            READY.add(new Ready(key, entry, image, color));
        } catch (Throwable t) {
            markFailed(entry);
        }
    }

    private static void markFailed(Entry entry) {
        entry.loading = false;
        entry.failed = true;
    }

    private static int averageColor(BufferedImage image) {
        long r = 0;
        long g = 0;
        long b = 0;
        long count = 0;
        for (int y = 0; y < image.getHeight(); y += 6) {
            for (int x = 0; x < image.getWidth(); x += 6) {
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a < 180) continue;
                int rr = (argb >> 16) & 0xFF;
                int gg = (argb >> 8) & 0xFF;
                int bb = argb & 0xFF;
                int brightness = (rr + gg + bb) / 3;
                if (brightness < 18 || brightness > 245) continue;
                r += rr;
                g += gg;
                b += bb;
                count++;
            }
        }
        if (count == 0) return 0xFF3A6EA5;
        Color color = new Color((int) (r / count), (int) (g / count), (int) (b / count));
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        Color boosted = Color.getHSBColor(hsb[0], Math.min(1f, hsb[1] * 0.9f + 0.1f), Math.max(0.55f, Math.min(0.9f, hsb[2])));
        return 0xFF000000 | boosted.getRGB() & 0xFFFFFF;
    }
}
