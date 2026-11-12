package dev.iosmusic.together;

import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Forge edition currently ships the player/UI only. The together-listening channel is a separate
 * optional networking feature and is intentionally stubbed here rather than half-wired.
 */
public final class TogetherClient {
    private static final List<TogetherMessage.Peer> NO_PEERS = List.of();
    private static volatile String notice = "";
    private static long noticeUntil;

    private TogetherClient() {
    }

    public static void tick(Minecraft minecraft) {
    }

    public static void handle(TogetherMessage message) {
    }

    public static void reset() {
    }

    public static List<TogetherMessage.Peer> peers() {
        return NO_PEERS;
    }

    public static TogetherMessage invite() {
        return null;
    }

    public static Room room() {
        return null;
    }

    public static String notice() {
        return System.currentTimeMillis() < noticeUntil ? notice : "";
    }

    public static boolean consumeOpenRequest() {
        return false;
    }

    public static void invite(String uuid, String name) {
        notice = "Forge 版暂未启用联机一起听";
        noticeUntil = System.currentTimeMillis() + 4000;
    }

    public static void acceptInvite() {
    }

    public static void declineInvite() {
    }

    public static void leaveRoom() {
    }

    public record Room(String hostUuid, String hostName, List<String> members, boolean host) {
    }
}
