package fr.batmultifonction.purplemusic.api.exception;

/**
 * Common parent for every checked exception thrown by the PurpleMusic API.
 *
 * <p>Catching this type lets a caller handle every API failure in a single
 * place when fine-grained discrimination is unnecessary.</p>
 */
public class PurpleMusicException extends Exception {

    public PurpleMusicException(String message) {
        super(message);
    }

    public PurpleMusicException(String message, Throwable cause) {
        super(message, cause);
    }
}
