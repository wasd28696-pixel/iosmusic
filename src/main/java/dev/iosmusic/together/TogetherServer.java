package dev.iosmusic.together;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional server-side room manager. It only exists when the same jar is installed on the server;
 * the client will simply show no peers on servers without it.
 */
public final class TogetherServer {
    private static final Map<UUID, String> KNOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> PENDING = new HashMap<>();
    private static final Map<UUID, Room> ROOMS = new HashMap<>();
    private static final Map<UUID, UUID> ROOM_OF = new HashMap<>();

    private TogetherServer() {
    }

    public static void handle(ServerPlayer player, TogetherMessage message) {
        if (player == null || message == null) return;
        switch (message.action) {
            case TogetherMessage.HELLO -> {
                KNOWN.put(player.getUUID(), player.getGameProfile().getName());
                broadcastPeers(player.server);
            }
            case TogetherMessage.INVITE -> handleInvite(player, message);
            case TogetherMessage.INVITE_REPLY -> handleReply(player, message);
            case TogetherMessage.ROOM, TogetherMessage.PLAYBACK -> handlePlayback(player, message);
            case TogetherMessage.SYNC -> handleSync(player);
            case TogetherMessage.LEAVE -> leave(player, true);
            default -> {
            }
        }
    }

    private static void handleInvite(ServerPlayer player, TogetherMessage message) {
        UUID target;
        try {
            target = UUID.fromString(message.targetUuid);
        } catch (Exception e) {
            return;
        }
        if (target.equals(player.getUUID()) || !KNOWN.containsKey(target) || ROOM_OF.containsKey(player.getUUID())) return;
        PENDING.put(target, player.getUUID());
        TogetherMessage invite = new TogetherMessage();
        invite.action = TogetherMessage.INVITE;
        invite.fromUuid = player.getUUID().toString();
        invite.fromName = player.getGameProfile().getName();
        send(target, invite);
    }

    private static void handleReply(ServerPlayer player, TogetherMessage message) {
        UUID inviter = PENDING.remove(player.getUUID());
        if (inviter == null) return;
        if (!message.accepted) {
            TogetherMessage declined = new TogetherMessage();
            declined.action = TogetherMessage.INVITE_REPLY;
            declined.accepted = false;
            declined.fromName = player.getGameProfile().getName();
            send(inviter, declined);
            return;
        }
        if (ROOM_OF.containsKey(player.getUUID()) || ROOM_OF.containsKey(inviter)) return;
        Room room = new Room();
        room.host = inviter;
        room.guest = player.getUUID();
        room.hostName = name(inviter);
        room.guestName = player.getGameProfile().getName();
        ROOMS.put(inviter, room);
        ROOM_OF.put(inviter, inviter);
        ROOM_OF.put(room.guest, inviter);
        sendRoom(room);
    }

    private static void handlePlayback(ServerPlayer player, TogetherMessage message) {
        UUID host = ROOM_OF.get(player.getUUID());
        if (host == null) return;
        Room room = ROOMS.get(host);
        if (room == null || !room.host.equals(player.getUUID())) return;
        room.last = message;
        TogetherMessage state = message;
        state.action = TogetherMessage.PLAYBACK;
        send(room.guest, state);
    }

    private static void handleSync(ServerPlayer player) {
        UUID host = ROOM_OF.get(player.getUUID());
        if (host == null) return;
        Room room = ROOMS.get(host);
        if (room == null) return;
        if (room.last != null) send(player.getUUID(), room.last);
    }

    private static void leave(ServerPlayer player, boolean notify) {
        UUID host = ROOM_OF.remove(player.getUUID());
        if (host == null) return;
        Room room = ROOMS.get(host);
        if (room == null) return;
        UUID other = room.host.equals(player.getUUID()) ? room.guest : room.host;
        ROOMS.remove(host);
        ROOM_OF.remove(room.host);
        ROOM_OF.remove(room.guest);
        if (notify && other != null) {
            TogetherMessage left = new TogetherMessage();
            left.action = TogetherMessage.LEAVE;
            if (other.equals(room.host)) {
                left.fromName = room.hostName;
            } else {
                left.fromName = room.guestName;
            }
            send(other, left);
        }
    }

    private static void sendRoom(Room room) {
        TogetherMessage message = new TogetherMessage();
        message.action = TogetherMessage.ROOM;
        message.hostUuid = room.host.toString();
        message.hostName = room.hostName;
        message.members.add(room.hostName);
        message.members.add(room.guestName);
        send(room.host, message);
        send(room.guest, message);
    }

    private static void broadcastPeers(MinecraftServer server) {
        if (server == null) return;
        TogetherMessage message = new TogetherMessage();
        message.action = TogetherMessage.PEERS;
        for (Map.Entry<UUID, String> entry : KNOWN.entrySet()) {
            if (server.getPlayerList().getPlayer(entry.getKey()) != null) {
                message.peers.add(new TogetherMessage.Peer(entry.getKey().toString(), entry.getValue()));
            }
        }
        for (UUID uuid : KNOWN.keySet()) send(uuid, message);
    }

    private static void send(UUID uuid, TogetherMessage message) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(uuid);
        if (player != null) TogetherNetworking.toPlayer(player, message);
    }

    private static String name(UUID uuid) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(uuid);
        return player == null ? "玩家" : player.getGameProfile().getName();
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        KNOWN.remove(uuid);
        PENDING.remove(uuid);
        PENDING.values().removeIf(uuid::equals);
        leave(player, true);
        broadcastPeers(player.server);
    }

    private static final class Room {
        UUID host;
        UUID guest;
        String hostName;
        String guestName;
        TogetherMessage last;
    }
}
