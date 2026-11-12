package dev.iosmusic.api;

/** An API-level failure with the NetEase business code preserved for useful UI messages. */
public class ApiException extends Exception {
    private final int code;

    public ApiException(int code, String message) {
        super(message == null || message.isBlank() ? "接口错误 (" + code + ")" : message + " (" + code + ")");
        this.code = code;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.code = -1;
    }

    public int code() {
        return code;
    }
}
