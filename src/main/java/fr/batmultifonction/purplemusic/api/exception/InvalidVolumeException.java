package fr.batmultifonction.purplemusic.api.exception;

/** Thrown when a supplied volume is NaN or outside the {@code [0,1]} range. */
public class InvalidVolumeException extends PurpleMusicException {
    private final float volume;

    public InvalidVolumeException(float volume) {
        super("Volume must be in [0,1] but was: " + volume);
        this.volume = volume;
    }

    public float volume() { return volume; }
}
