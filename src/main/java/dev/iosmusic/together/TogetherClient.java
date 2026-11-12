package dev.iosmusic.together;

import dev.iosmusic.MusicPlayer;
import dev.iosmusic.api.Models;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.List;
import java.util.UUID;

/** Client half of the together-listening feature. */
public final class TogetherClient {
    private static volatile List<TogetherMessage.Peer> peers = List.of();
    private static volatile TogetherMessage invite;
    private static volatile Room room;
    private static volatile boolean openRequested;
    private static volatile String notice = "";
    private static long noticeUntil;

    private static int helloTicks = 10;
    private static int broadcastTicks = 0;
    private static String pendingSongId = "";
    private static double pendingSeek = -1;

    private TogetherClient() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            reset();
            return;
        }
        if (--helloTicks <= 0) {
            TogetherMessage hello = new TogetherMessage();
            hello.action = TogetherMessage.HELLO;
            hello.fromName = minecraft.player.getGameProfile().getName();
            send(hello);
            helloTicks = 200;
        }
        if (room != null && room.host) {
            if (--broadcastTicks <= 0) {
                broadcastPlayback();
                broadcastTicks = 20;
            }
        }
        applyPendingSeek();
    }

    public static void handle(TogetherMessage message) {
        if (message == null) return;
        switch (message.action) {
            case TogetherMessage.PEERS -> peers = List.copyOf(message.peers);
            case TogetherMessage.INVITE -> {
                invite = message;
                openRequested = true;
            }
            case TogetherMessage.INVITE_REPLY -> {
                if (!message.accepted) {
                    notice = message.fromName + " 拒绝了一起听邀请";
                    noticeUntil = System.currentTimeMillis() + 5000;
                }
            }
            case TogetherMessage.ROOM -> {
                UUID self = selfUuid();
                room = new Room(message.hostUuid, message.hostName, List.copyOf(message.members),
                        self != null && self.toString().equals(message.hostUuid));
                invite = null;
                if (!room.host) {
                    TogetherMessage sync = new TogetherMessage();
                    sync.action = TogetherMessage.SYNC;
                    send(sync);
                }
            }
            case TogetherMessage.PLAYBACK -> applyRemote(message);
            case TogetherMessage.LEAVE -> {
                notice = (message.fromName == null || message.fromName.isBlank() ? "对方" : message.fromName)
                        + " 离开了一起听";
                noticeUntil = System.currentTimeMillis() + 5000;
                room = null;
            }
            default -> {
            }
        }
    }

    public static void reset() {
        peers = List.of();
        invite = null;
        room = null;
        pendingSongId = "";
        pendingSeek = -1;
    }

    public static List<TogetherMessage.Peer> peers() {
        return peers;
    }

    public static TogetherMessage invite() {
        return invite;
    }

    public static Room room() {
        return room;
    }

    public static String notice() {
        return System.currentTimeMillis() < noticeUntil ? notice : "";
    }

    public static boolean consumeOpenRequest() {
        if (!openRequested) return false;
        openRequested = false;
        return true;
    }

    public static void invite(String uuid, String name) {
        TogetherMessage message = new TogetherMessage();
        message.action = TogetherMessage.INVITE;
        message.targetUuid = uuid;
        send(message);
        notice = "已向 " + name + " 发送一起听邀请";
        noticeUntil = System.currentTimeMillis() + 4000;
    }

    public static void acceptInvite() {
        TogetherMessage message = new TogetherMessage();
        message.action = TogetherMessage.INVITE_REPLY;
        message.accepted = true;
        message.fromUuid = invite == null ? "" : invite.fromUuid;
        send(message);
        invite = null;
    }

    public static void declineInvite() {
        TogetherMessage message = new TogetherMessage();
        message.action = TogetherMessage.INVITE_REPLY;
        message.accepted = false;
        send(message);
        invite = null;
    }

    public static void leaveRoom() {
        TogetherMessage message = new TogetherMessage();
        message.action = TogetherMessage.LEAVE;
        send(message);
        room = null;
    }

    private static void broadcastPlayback() {
        MusicPlayer player = MusicPlayer.get();
        Models.Song song = player.current();
        if (song == null) return;
        TogetherMessage message = new TogetherMessage();
        message.action = TogetherMessage.PLAYBACK;
        message.songId = Long.toString(song.id());
        message.songName = song.name();
        message.artist = song.artist();
        message.album = song.album();
        message.cover = song.coverUrl();
        message.durationMs = song.durationMs();
        message.position = player.positionSeconds();
        message.playing = player.state() == MusicPlayer.Playback.PLAYING;
        send(message);
    }

    private static void send(TogetherMessage message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null) return;
        if (!NetworkRegistry.hasChannel(minecraft.getConnection().getConnection(),
                ConnectionProtocol.PLAY, TogetherPayload.TYPE.id())) {
            return;
        }
        TogetherNetworking.toServer(message);
    }

    private static void applyRemote(TogetherMessage message) {
        long id;
        try {
            id = Long.parseLong(message.songId);
        } catch (Exception e) {
            return;
        }
        MusicPlayer player = MusicPlayer.get();
        Models.Song current = player.current();
        if (current == null || current.id() != id) {
            Models.Song song = new Models.Song(id, message.songName, message.artist, message.album, 0,
                    message.cover, message.durationMs, 0, false);
            player.playSong(song, List.of(song), "一起听");
            pendingSongId = Long.toString(id);
            pendingSeek = message.position;
            return;
        }
        if (pendingSongId.equals(Long.toString(id)) && pendingSeek >= 0) {
            if (player.state() == MusicPlayer.Playback.PLAYING) {
                player.seekSeconds(pendingSeek);
                pendingSeek = -1;
                pendingSongId = "";
            }
        } else if (Math.abs(player.positionSeconds() - message.position) > 1.8) {
            player.seekSeconds(message.position);
        }
        boolean playing = player.state() == MusicPlayer.Playback.PLAYING;
        if (message.playing != playing
                && (player.state() == MusicPlayer.Playback.PLAYING || player.state() == MusicPlayer.Playback.PAUSED)) {
            player.toggle();
        }
    }

    private static void applyPendingSeek() {
        if (pendingSeek < 0) return;
        MusicPlayer player = MusicPlayer.get();
        Models.Song current = player.current();
        if (current != null && pendingSongId.equals(Long.toString(current.id()))
                && player.state() == MusicPlayer.Playback.PLAYING) {
            player.seekSeconds(pendingSeek);
            pendingSeek = -1;
            pendingSongId = "";
        }
    }

    private static UUID selfUuid() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.player == null ? null : minecraft.player.getUUID();
    }

    public record Room(String hostUuid, String hostName, List<String> members, boolean host) {
    }
}
