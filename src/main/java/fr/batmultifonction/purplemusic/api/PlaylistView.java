package fr.batmultifonction.purplemusic.api;

import java.util.List;

/**
 * Read-only snapshot of a named playlist returned by the API.
 *
 * <p>This view is immutable: mutating the underlying playlist must go through
 * {@link PurpleMusicAPI#addToPlaylist(String, String)} and friends.</p>
 */
public interface PlaylistView {

    /** The unique playlist name. */
    String name();

    /** Current play mode. */
    PlayMode mode();

    /** Ordered, immutable list of track filenames. */
    List<String> tracks();

    /** Convenience: number of tracks. */
    default int size() { return tracks().size(); }

    /** Convenience: {@code tracks().isEmpty()}. */
    default boolean isEmpty() { return tracks().isEmpty(); }
}
