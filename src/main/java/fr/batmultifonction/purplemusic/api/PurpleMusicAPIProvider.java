package fr.batmultifonction.purplemusic.api;

/**
 * Static holder for the {@link PurpleMusicAPI} singleton.
 *
 * <p>The plugin registers itself here when it is enabled, and unregisters when
 * it is disabled. Third-party plugins should call {@link #get()} only after
 * PurpleMusic has finished its {@code onEnable()}.</p>
 */
public final class PurpleMusicAPIProvider {

    private static volatile PurpleMusicAPI instance;

    private PurpleMusicAPIProvider() {}

    /**
     * Returns the active API instance.
     *
     * @throws IllegalStateException if PurpleMusic is not enabled yet.
     */
    public static PurpleMusicAPI get() {
        PurpleMusicAPI api = instance;
        if (api == null) {
            throw new IllegalStateException(
                    "PurpleMusicAPI is not initialized yet. " +
                            "Make sure PurpleMusic is enabled before accessing the API.");
        }
        return api;
    }

    /** {@code true} once PurpleMusic has registered an implementation. */
    public static boolean isAvailable() {
        return instance != null;
    }

    /** Internal: registers the API implementation. Called by PurpleMusic on enable. */
    public static void register(PurpleMusicAPI api) {
        instance = api;
    }

    /** Internal: clears the API. Called by PurpleMusic on disable. */
    public static void unregister() {
        instance = null;
    }
}
