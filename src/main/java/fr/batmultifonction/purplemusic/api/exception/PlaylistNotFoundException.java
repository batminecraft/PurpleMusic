package fr.batmultifonction.purplemusic.api.exception;

/** Thrown when a referenced playlist does not exist. */
public class PlaylistNotFoundException extends PurpleMusicException {
    private final String playlistName;

    public PlaylistNotFoundException(String playlistName) {
        super("Playlist not found: " + playlistName);
        this.playlistName = playlistName;
    }

    public String playlistName() { return playlistName; }
}
