package fr.batmultifonction.purplemusic.api;

/**
 * Handle returned by every playback method on {@link PurpleMusicAPI}.
 *
 * <p>A handle starts in the {@link PlaybackState#PLAYING} state and ends up
 * either {@link PlaybackState#STOPPED} (when the underlying stream finishes or
 * is cancelled) or {@link PlaybackState#FAILED} (when initialisation failed,
 * e.g. SVC was missing).</p>
 */
public interface PlaybackHandle {

    /** Possible states of a {@link PlaybackHandle}. */
    enum PlaybackState {
        /** Audio is currently being streamed. */
        PLAYING,
        /** Playback has finished naturally or was cancelled. */
        STOPPED,
        /** Playback could not start (missing voice chat, decode error...). */
        FAILED
    }

    /** Returns the current state of this handle. */
    PlaybackState state();

    /** Convenience: {@code state() == PlaybackState.PLAYING}. */
    default boolean isPlaying() { return state() == PlaybackState.PLAYING; }

    /** Convenience: {@code state() == PlaybackState.STOPPED}. */
    default boolean isStopped() { return state() == PlaybackState.STOPPED; }

    /** Cancels playback if still running. No-op if already stopped or failed. */
    void stop();

    /**
     * Registers a one-shot callback fired when the handle reaches a terminal
     * state (STOPPED or FAILED). If the handle has already terminated, the
     * callback is invoked immediately.
     */
    void onComplete(Runnable callback);
}
