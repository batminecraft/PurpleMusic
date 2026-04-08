package fr.batmultifonction.purplemusic.api.exception;

/** Thrown when adding or renaming a track would overwrite an existing file. */
public class TrackAlreadyExistsException extends PurpleMusicException {
    private final String trackName;

    public TrackAlreadyExistsException(String trackName) {
        super("Track already exists: " + trackName);
        this.trackName = trackName;
    }

    public String trackName() { return trackName; }
}
