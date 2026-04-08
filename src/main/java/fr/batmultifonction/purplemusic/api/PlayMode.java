package fr.batmultifonction.purplemusic.api;

/**
 * Public mirror of the internal playlist play modes.
 */
public enum PlayMode {
    /** Plays each track once in declaration order. */
    SEQUENTIAL,
    /** Plays in order, then restarts from the first track. */
    LOOP,
    /** Repeats the same track forever. */
    LOOP_ONE,
    /** Picks a random track every time. */
    SHUFFLE;

    /** Parses a string into a play mode, defaulting to {@link #SEQUENTIAL}. */
    public static PlayMode parse(String s) {
        if (s == null) return SEQUENTIAL;
        try { return PlayMode.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return SEQUENTIAL; }
    }
}
