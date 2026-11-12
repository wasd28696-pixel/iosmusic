package dev.iosmusic.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Streaming MP3 playback through Java Sound. A private SourceDataLine is used instead of
 * Minecraft's sound engine so arbitrary internet streams can be played without registering
 * resource-pack sounds at runtime.
 */
public final class AudioEngine {
    public enum State {IDLE, PLAYING, PAUSED, ENDED, ERROR}

    public interface Listener {
        void onEnded();

        void onError(String message);
    }

    private static final Logger LOG = Logger.getLogger("iOSMusic");

    private final AtomicLong generation = new AtomicLong();
    private volatile State state = State.IDLE;
    private volatile Path file;
    private volatile boolean paused;
    private volatile float volume = 0.72f;
    private volatile double baseSeconds;
    private volatile double lineBaseSeconds;
    private volatile int sampleRate = 44100;
    private volatile int channels = 2;
    private volatile SourceDataLine line;
    private volatile Thread worker;
    private volatile Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public State state() {
        return state;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
    }

    public double positionSeconds() {
        SourceDataLine current = line;
        if (current == null) return baseSeconds;
        return lineBaseSeconds + current.getLongFramePosition() / (double) Math.max(1, sampleRate);
    }

    public void play(Path mp3, double startSeconds) {
        stopInternal();
        if (mp3 == null || !Files.isRegularFile(mp3)) {
            fail("音频文件不存在");
            return;
        }
        this.file = mp3;
        this.paused = false;
        this.baseSeconds = Math.max(0, startSeconds);
        this.state = State.PLAYING;
        long gen = generation.incrementAndGet();
        Thread thread = new Thread(() -> run(gen, mp3, Math.max(0, startSeconds), false), "iOSMusic-Audio");
        thread.setDaemon(true);
        this.worker = thread;
        thread.start();
    }

    public void playPaused(Path mp3, double startSeconds) {
        stopInternal();
        if (mp3 == null || !Files.isRegularFile(mp3)) {
            fail("音频文件不存在");
            return;
        }
        this.file = mp3;
        this.paused = true;
        this.baseSeconds = Math.max(0, startSeconds);
        this.state = State.PAUSED;
        long gen = generation.incrementAndGet();
        Thread thread = new Thread(() -> run(gen, mp3, Math.max(0, startSeconds), true), "iOSMusic-Audio");
        thread.setDaemon(true);
        this.worker = thread;
        thread.start();
    }

    public void togglePause() {
        if (state == State.PLAYING) {
            paused = true;
            SourceDataLine current = line;
            if (current != null) current.stop();
            state = State.PAUSED;
        } else if (state == State.PAUSED) {
            paused = false;
            SourceDataLine current = line;
            if (current != null) current.start();
            state = State.PLAYING;
        }
    }

    public void pause() {
        if (state == State.PLAYING) togglePause();
    }

    public void resume() {
        if (state == State.PAUSED) togglePause();
    }

    public void seek(double seconds) {
        Path current = file;
        if (current == null) return;
        boolean wasPaused = paused;
        if (wasPaused) playPaused(current, seconds);
        else play(current, seconds);
    }

    public void stop() {
        stopInternal();
        state = State.IDLE;
        baseSeconds = 0;
        paused = false;
    }

    private void stopInternal() {
        generation.incrementAndGet();
        paused = false;
        SourceDataLine current = line;
        line = null;
        if (current != null) {
            try {
                current.stop();
                current.flush();
                current.close();
            } catch (Exception ignored) {
            }
        }
        Thread thread = worker;
        worker = null;
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
    }

    private void run(long gen, Path mp3, double startSeconds, boolean initiallyPaused) {
        InputStream raw = null;
        Bitstream bitstream = null;
        SourceDataLine output = null;
        try {
            raw = skipId3(new BufferedInputStream(Files.newInputStream(mp3)));
            bitstream = new Bitstream(raw);
            Decoder decoder = new Decoder();
            Header header = bitstream.readFrame();
            if (header == null) throw new IllegalStateException("没有找到 MP3 音频帧");
            SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            int rate = decoder.getOutputFrequency();
            int ch = decoder.getOutputChannels();
            if (rate <= 0) rate = 44100;
            if (ch <= 0) ch = 2;
            sampleRate = rate;
            channels = ch;
            AudioFormat format = new AudioFormat(rate, 16, ch, true, false);
            long targetSamples = (long) (startSeconds * rate * ch);
            long decodedSamples = 0;
            boolean opened = false;

            while (gen == generation.get()) {
                if (paused) {
                    sleepQuietly(20);
                    continue;
                }
                int length = samples.getBufferLength();
                short[] buffer = samples.getBuffer();
                if (length > 0) {
                    int offset = 0;
                    if (!opened) {
                        long frameEnd = decodedSamples + length;
                        if (frameEnd <= targetSamples) {
                            // Entire frame is before the seek target; nothing is written.
                        } else {
                            if (decodedSamples < targetSamples) {
                                offset = (int) (targetSamples - decodedSamples);
                            }
                            output = openLine(format, rate, ch);
                            line = output;
                            lineBaseSeconds = (decodedSamples + offset) / (double) (rate * ch);
                            baseSeconds = lineBaseSeconds;
                            opened = true;
                            if (paused) {
                                output.stop();
                            } else {
                                output.start();
                            }
                        }
                    }
                    if (opened && !paused) {
                        write(output, buffer, offset, length - offset);
                    }
                    decodedSamples += length;
                }
                bitstream.closeFrame();
                header = bitstream.readFrame();
                if (header == null) break;
                samples = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            }

            if (opened && gen == generation.get()) {
                try {
                    output.drain();
                } catch (Exception ignored) {
                }
                state = State.ENDED;
                Listener current = listener;
                if (current != null) current.onEnded();
            } else if (!opened && gen == generation.get()) {
                state = State.ENDED;
                Listener current = listener;
                if (current != null) current.onEnded();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (gen == generation.get()) {
                LOG.warning("Playback failed: " + e);
                fail("播放失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        } finally {
            if (output != null) {
                try {
                    output.stop();
                    output.close();
                } catch (Exception ignored) {
                }
            }
            if (bitstream != null) {
                try {
                    bitstream.close();
                } catch (Exception ignored) {
                }
            }
            try {
                if (raw != null) raw.close();
            } catch (Exception ignored) {
            }
            if (line == output) line = null;
        }
    }

    private SourceDataLine openLine(AudioFormat format, int rate, int channels) throws Exception {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine output = (SourceDataLine) AudioSystem.getLine(info);
        int bufferSize = Math.max(8192, rate * channels * 2 / 8);
        try {
            output.open(format, bufferSize);
        } catch (Exception e) {
            output.open(format);
        }
        return output;
    }

    private void write(SourceDataLine output, short[] samples, int offset, int count) throws Exception {
        if (count <= 0) return;
        float gain = volume;
        gain = gain * gain;
        byte[] bytes = new byte[count * 2];
        for (int i = 0; i < count; i++) {
            int value = (int) (samples[offset + i] * gain);
            if (value > 32767) value = 32767;
            if (value < -32768) value = -32768;
            bytes[i * 2] = (byte) value;
            bytes[i * 2 + 1] = (byte) (value >> 8);
        }
        output.write(bytes, 0, bytes.length);
    }

    private void fail(String message) {
        state = State.ERROR;
        Listener current = listener;
        if (current != null) current.onError(message);
    }

    private static InputStream skipId3(BufferedInputStream input) throws Exception {
        input.mark(16);
        byte[] header = new byte[10];
        int read = input.read(header);
        if (read >= 10 && header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
            int size = ((header[6] & 0x7F) << 21)
                    | ((header[7] & 0x7F) << 14)
                    | ((header[8] & 0x7F) << 7)
                    | (header[9] & 0x7F);
            boolean footer = (header[5] & 0x10) != 0;
            long toSkip = (long) size + (footer ? 10 : 0);
            while (toSkip > 0) {
                long skipped = input.skip(toSkip);
                if (skipped <= 0) {
                    if (input.read() < 0) break;
                    skipped = 1;
                }
                toSkip -= skipped;
            }
        } else {
            input.reset();
        }
        return input;
    }

    private static void sleepQuietly(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    public void shutdown() {
        stopInternal();
        state = State.IDLE;
    }
}
