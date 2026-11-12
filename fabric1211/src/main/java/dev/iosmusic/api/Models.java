package dev.iosmusic.api;

import java.util.List;

/** Small immutable models shared by the API backends and the UI. */
public final class Models {
    private Models() {
    }

    public record Song(long id, String name, String artist, String album, long albumId,
                       String coverUrl, long durationMs, int fee, boolean trial) {
        public String durationText() {
            long total = Math.max(0, durationMs) / 1000;
            return String.format("%d:%02d", total / 60, total % 60);
        }

        public String subtitle() {
            if (artist == null || artist.isBlank()) return album == null ? "" : album;
            if (album == null || album.isBlank()) return artist;
            return artist + " · " + album;
        }
    }

    public record Playlist(long id, String name, String coverUrl, int trackCount,
                           String description, String creator, String updateFrequency) {
        public String subtitle() {
            String count = trackCount > 0 ? trackCount + " 首" : "歌单";
            if (creator == null || creator.isBlank()) return count;
            return count + " · " + creator;
        }
    }

    public record PlaylistDetail(Playlist playlist, List<Song> tracks) {
    }

    public record UserProfile(long id, String nickname, String avatarUrl, int vipType,
                              String signature, int level) {
        public String displayName() {
            return nickname == null || nickname.isBlank() ? "网易云用户" : nickname;
        }
    }

    public record SearchResult(List<Song> songs, int total) {
    }

    public record HomeData(List<Playlist> recommended, List<Playlist> toplists,
                           List<Song> newSongs, List<Song> daily,
                           List<Playlist> userPlaylists) {
        public static HomeData empty() {
            return new HomeData(List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    public record LyricLine(long timeMs, String text, String translation) {
    }

    public record Lyrics(List<LyricLine> lines, String raw) {
        public static Lyrics empty() {
            return new Lyrics(List.of(), "");
        }

        public int indexAt(double seconds) {
            long ms = (long) (seconds * 1000.0);
            int low = 0;
            int high = lines.size() - 1;
            int answer = -1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (lines.get(mid).timeMs() <= ms) {
                    answer = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            return answer;
        }
    }

    public record SongUrl(String url, String type, long size, int fee, boolean trial, String message) {
        public boolean playable() {
            return url != null && !url.isBlank();
        }
    }

    public record QrSession(String key, String url, String imageDataUrl) {
    }

    public record QrStatus(int code, String message, UserProfile profile) {
    }

    public record AuthResult(boolean success, UserProfile profile, String message) {
    }
}
