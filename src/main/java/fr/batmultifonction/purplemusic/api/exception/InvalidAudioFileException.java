package fr.batmultifonction.purplemusic.api.exception;

/** Thrown when an audio file cannot be opened or decoded. */
public class InvalidAudioFileException extends PurpleMusicException {
    public InvalidAudioFileException(String message) {
        super(message);
    }

    public InvalidAudioFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
