package fr.batmultifonction.purplemusic.api.exception;

/** Thrown when creating a playlist that would collide with an existing one. */
public class PlaylistAlreadyExistsException extends PurpleMusicException {
    private final String playlistName;

    public PlaylistAlreadyExistsException(String playlistName) {
        super("Playlist already exists: " + playlistName);
        this.playlistName = playlistName;
    }

    public String playlistName() { return playlistName; }
}
