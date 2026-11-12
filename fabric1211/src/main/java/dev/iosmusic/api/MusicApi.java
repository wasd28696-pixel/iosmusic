package dev.iosmusic.api;

import java.util.List;

/** Both the direct official client and the community HTTP server implement this. */
public interface MusicApi {
    String backendName();

    Models.HomeData home() throws Exception;

    Models.SearchResult search(String keywords, int limit, int offset) throws Exception;

    Models.PlaylistDetail playlist(long id) throws Exception;

    List<Models.Song> songDetails(List<Long> ids) throws Exception;

    Models.SongUrl songUrl(long id) throws Exception;

    Models.Lyrics lyrics(long id) throws Exception;

    Models.UserProfile loginStatus() throws Exception;

    Models.QrSession qrCreate() throws Exception;

    Models.QrStatus qrCheck(String key) throws Exception;

    Models.AuthResult loginCellphone(String phone, String password, String countryCode) throws Exception;

    Models.AuthResult loginCookie(String cookie) throws Exception;

    void logout() throws Exception;

    /** Applies a previously saved cookie without performing a network request. */
    void restoreCookie(String cookie);

    String exportCookie();
}
