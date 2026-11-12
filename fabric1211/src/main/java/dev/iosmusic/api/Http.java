package dev.iosmusic.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Small HTTP helper shared by both API backends. */
public final class Http {
    private static final Duration TIMEOUT = Duration.ofSeconds(25);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Http() {
    }

    public record Response(int status, String body, Map<String, List<String>> headers) {
    }

    public static String postForm(String url, Map<String, String> form, Map<String, String> headers)
            throws IOException, InterruptedException {
        return postFormFull(url, form, headers).body();
    }

    public static Response postFormFull(String url, Map<String, String> form, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(WeApi.formEncode(form), StandardCharsets.UTF_8));
        applyHeaders(builder, headers);
        HttpResponse<String> response = CLIENT.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        check(response.statusCode(), response.body());
        return new Response(response.statusCode(), response.body(), response.headers().map());
    }

    public static String get(String url, Map<String, String> headers) throws IOException, InterruptedException {
        return getFull(url, headers).body();
    }

    public static Response getFull(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET();
        applyHeaders(builder, headers);
        HttpResponse<String> response = CLIENT.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        check(response.statusCode(), response.body());
        return new Response(response.statusCode(), response.body(), response.headers().map());
    }

    public static byte[] getBytes(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET();
        applyHeaders(builder, headers);
        HttpResponse<byte[]> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " while fetching image");
        }
        return response.body();
    }

    public static void download(String url, Path target, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).GET();
        applyHeaders(builder, headers);
        HttpResponse<Path> response = CLIENT.send(builder.build(),
                HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
            }
            throw new IOException("HTTP " + response.statusCode() + " while downloading media");
        }
    }

    private static void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers != null) {
            for (var header : headers.entrySet()) {
                if (header.getValue() != null && !header.getValue().isEmpty()) {
                    builder.header(header.getKey(), header.getValue());
                }
            }
        }
    }

    private static void check(int status, String body) throws IOException {
        if (status < 200 || status >= 300) {
            String trimmed = body == null ? "" : body.substring(0, Math.min(240, body.length()));
            throw new IOException("HTTP " + status + (trimmed.isEmpty() ? "" : ": " + trimmed));
        }
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String query(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        for (var entry : params.entrySet()) {
            if (entry.getValue() == null) continue;
            joiner.add(encode(entry.getKey()) + "=" + encode(entry.getValue()));
        }
        return joiner.toString();
    }

    public static String appendQuery(String base, Map<String, String> params) {
        String query = query(params);
        if (query.isEmpty()) return base;
        return base + (base.contains("?") ? "&" : "?") + query;
    }

    public static String normalizeBase(String raw) {
        String value = raw == null ? "" : raw.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    /** Any HTTP response, including a 404, proves that a third-party server is listening. */
    public static boolean reachable(String base, int timeoutMs) {
        String value = normalizeBase(base);
        if (value.isBlank()) return false;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Math.max(120, timeoutMs)))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(value))
                    .timeout(Duration.ofMillis(Math.max(120, timeoutMs)))
                    .GET().build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
