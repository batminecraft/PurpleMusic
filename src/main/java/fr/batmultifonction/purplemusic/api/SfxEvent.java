package fr.batmultifonction.purplemusic.api;

/**
 * The set of events that can play a sound effect through PurpleMusic.
 *
 * <p>Each entry maps to a key under {@code sfx:} in {@code config.yml} whose value
 * is the name of an audio file located inside {@code plugins/PurpleMusic/sfx/}.</p>
 */
public enum SfxEvent {
    /** Played when a track or playlist starts. */
    PLAY("play"),
    /** Played when playback is paused. */
    PAUSE("pause"),
    /** Played when playback resumes from a pause. */
    RESUME("resume"),
    /** Played when playback is fully stopped (not skipped). */
    STOP("stop"),
    /** Played when the current track is skipped. */
    SKIP("skip");

    private final String configKey;

    SfxEvent(String configKey) {
        this.configKey = configKey;
    }

    /** YAML key used inside the {@code sfx:} section of config.yml. */
    public String configKey() {
        return configKey;
    }
}
