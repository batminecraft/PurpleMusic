package fr.batmultifonction.purplemusic.api.exception;

/** Thrown when a referenced track does not exist in the music library. */
public class TrackNotFoundException extends PurpleMusicException {
    private final String trackName;

    public TrackNotFoundException(String trackName) {
        super("Track not found: " + trackName);
        this.trackName = trackName;
    }

    public String trackName() { return trackName; }
}
