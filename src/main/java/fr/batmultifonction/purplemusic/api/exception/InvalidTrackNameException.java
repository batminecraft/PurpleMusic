package fr.batmultifonction.purplemusic.api.exception;

/**
 * Thrown when a supplied track name is rejected (empty, contains illegal
 * characters, escapes the library root, or uses an unsupported extension).
 */
public class InvalidTrackNameException extends PurpleMusicException {
    public InvalidTrackNameException(String message) {
        super(message);
    }
}
