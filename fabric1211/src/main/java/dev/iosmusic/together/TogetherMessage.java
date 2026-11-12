package dev.iosmusic.together;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/** JSON message shared by the optional server component and the client room UI. */
public final class TogetherMessage {
    private static final Gson GSON = new GsonBuilder().create();

    public static final String HELLO = "hello";
    public static final String PEERS = "peers";
    public static final String INVITE = "invite";
    public static final String INVITE_REPLY = "invite_reply";
    public static final String ROOM = "room";
    public static final String PLAYBACK = "playback";
    public static final String SYNC = "sync";
    public static final String LEAVE = "leave";

    public String action = "";
    public String fromUuid = "";
    public String fromName = "";
    public String targetUuid = "";
    public String hostUuid = "";
    public String hostName = "";
    public List<Peer> peers = new ArrayList<>();
    public List<String> members = new ArrayList<>();
    public boolean accepted;
    public boolean host;

    public String songId = "";
    public String songName = "";
    public String artist = "";
    public String album = "";
    public String cover = "";
    public long durationMs;
    public double position;
    public boolean playing;

    public static final class Peer {
        public String uuid = "";
        public String name = "";

        public Peer() {
        }

        public Peer(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    public String json() {
        return GSON.toJson(this);
    }

    public static TogetherMessage parse(String json) {
        try {
            TogetherMessage message = GSON.fromJson(json, TogetherMessage.class);
            return message == null ? new TogetherMessage() : message;
        } catch (Exception e) {
            return new TogetherMessage();
        }
    }
}
