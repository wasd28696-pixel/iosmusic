package dev.iosmusic.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NetEase "weapi" request encryption. Implemented from the public algorithm used by the
 * official web client: two rounds of AES-128-CBC plus RSA of the per-request secret.
 */
public final class WeApi {
    private static final Gson GSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
    private static final String IV = "0102030405060708";
    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String EAPI_KEY = "e82ckenh8dichen8";
    private static final String PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB";

    private WeApi() {
    }

    public static Map<String, String> encrypt(JsonObject payload) {
        String text = GSON.toJson(payload);
        String secret = randomSecret();
        String params = aesCbc(text, PRESET_KEY);
        params = aesCbc(params, secret);
        String encSecKey = rsa(secret);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("params", params);
        form.put("encSecKey", encSecKey);
        return form;
    }

    public static Map<String, String> eapi(String path, JsonObject payload) {
        String text = GSON.toJson(payload);
        String message = "nobody" + path + "use" + text + "md5forencrypt";
        String digest = md5(message);
        String data = path + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest;
        Map<String, String> form = new LinkedHashMap<>();
        form.put("params", aesEcbHex(data, EAPI_KEY));
        return form;
    }

    public static String md5(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    private static String randomSecret() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return sb.toString();
    }

    private static String aesCbc(String text, String key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8)));
            return Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("AES encryption failed", e);
        }
    }

    private static String aesEcbHex(String text, String key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"));
            byte[] out = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("EAPI encryption failed", e);
        }
    }

    private static String rsa(String secret) {
        try {
            byte[] der = Base64.getDecoder().decode(PUBLIC_KEY);
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            String reversed = new StringBuilder(secret).reverse().toString();
            byte[] out = cipher.doFinal(reversed.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(256);
            for (byte b : out) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("RSA encryption failed", e);
        }
    }

    public static String formEncode(Map<String, String> form) {
        StringBuilder sb = new StringBuilder();
        for (var e : form.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    static String weirdHex(BigInteger value) {
        String hex = value.toString(16);
        return "0".repeat(Math.max(0, 256 - hex.length())) + hex;
    }
}
