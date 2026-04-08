package fr.batmultifonction.purplemusic.api;

import fr.batmultifonction.purplemusic.api.exception.InvalidAudioFileException;
import fr.batmultifonction.purplemusic.api.exception.InvalidTrackNameException;
import fr.batmultifonction.purplemusic.api.exception.InvalidVolumeException;
import fr.batmultifonction.purplemusic.api.exception.PlaylistAlreadyExistsException;
import fr.batmultifonction.purplemusic.api.exception.PlaylistNotFoundException;
import fr.batmultifonction.purplemusic.api.exception.TrackAlreadyExistsException;
import fr.batmultifonction.purplemusic.api.exception.TrackNotFoundException;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Public, stable entry point of the PurpleMusic plugin.
 *
 * <p>Obtain an instance via {@link PurpleMusicAPIProvider#get()} once the plugin
 * is enabled. Every method on this interface is thread-safe unless explicitly
 * documented otherwise: heavy lifting (decoding, networking, file IO) is dispatched
 * onto the plugin's worker pool, so callers may invoke them from any thread.</p>
 *
 * <p>The API exposes three concerns:</p>
 * <ul>
 *     <li><b>Library management</b> — list, add, delete and rename audio files
 *     stored in {@code plugins/PurpleMusic/musicdata/}.</li>
 *     <li><b>Playlist management</b> — create, mutate and delete named playlists
 *     persisted to {@code playlists.yml}.</li>
 *     <li><b>Playback</b> — start arbitrary tracks for one player, a group, every
 *     online player, or at a fixed location with any radius and volume, optionally
 *     repeated a fixed number of times.</li>
 * </ul>
 *
 * <p>All failure modes are reported through dedicated checked exceptions (see
 * the {@code fr.batmultifonction.purplemusic.api.exception} package), making it
 * impossible to silently swallow misconfiguration.</p>
 */
public interface PurpleMusicAPI {

    // ----------------------------------------------------------------------
    //                              LIBRARY
    // ----------------------------------------------------------------------

    /** Returns the absolute path of the {@code musicdata} folder. */
    Path libraryRoot();

    /**
     * Lists every audio file currently stored in the library, returned as
     * paths relative to {@link #libraryRoot()} using forward slashes.
     */
    List<String> listTracks();

    /** {@code true} if the given track name resolves to a valid, supported audio file. */
    boolean trackExists(String name);

    /**
     * Resolves a track name into its on-disk path.
     *
     * @throws TrackNotFoundException if no matching file exists.
     */
    Path getTrackPath(String name) throws TrackNotFoundException;

    /**
     * Imports a new audio file into the library.
     *
     * @param name destination filename (relative to the library root). Must end
     *             with a supported extension ({@code .wav}, {@code .mp3}, {@code .flac}).
     * @param data raw bytes of the audio file. The stream is fully consumed but
     *             not closed.
     * @throws InvalidTrackNameException   if the name is empty, malformed or
     *                                     escapes the library root.
     * @throws TrackAlreadyExistsException if a file with the same name already exists.
     * @throws InvalidAudioFileException   if the file cannot be decoded.
     */
    void addTrack(String name, InputStream data)
            throws InvalidTrackNameException, TrackAlreadyExistsException, InvalidAudioFileException;

    /**
     * Removes a track from the library on disk.
     *
     * @throws TrackNotFoundException if no such track exists.
     */
    void deleteTrack(String name) throws TrackNotFoundException;

    /**
     * Renames an existing track. The new name must use a supported extension and
     * must not collide with another file.
     *
     * @throws TrackNotFoundException      if {@code oldName} does not exist.
     * @throws TrackAlreadyExistsException if {@code newName} already exists.
     * @throws InvalidTrackNameException   if {@code newName} is malformed.
     */
    void renameTrack(String oldName, String newName)
            throws TrackNotFoundException, TrackAlreadyExistsException, InvalidTrackNameException;

    // ----------------------------------------------------------------------
    //                              PLAYLISTS
    // ----------------------------------------------------------------------

    /** Returns the names of every playlist currently registered. */
    List<String> listPlaylists();

    /** {@code true} if a playlist with the given name exists. */
    boolean playlistExists(String name);

    /**
     * Returns a read-only snapshot of the named playlist.
     *
     * @throws PlaylistNotFoundException if no playlist matches.
     */
    PlaylistView getPlaylist(String name) throws PlaylistNotFoundException;

    /**
     * Creates a new playlist with the given mode.
     *
     * @throws PlaylistAlreadyExistsException if a playlist with the same name exists.
     */
    void createPlaylist(String name, PlayMode mode) throws PlaylistAlreadyExistsException;

    /**
     * Deletes a playlist.
     *
     * @throws PlaylistNotFoundException if no such playlist exists.
     */
    void deletePlaylist(String name) throws PlaylistNotFoundException;

    /**
     * Appends a track to a playlist.
     *
     * @throws PlaylistNotFoundException if no such playlist exists.
     * @throws TrackNotFoundException    if the referenced track is not in the library.
     */
    void addToPlaylist(String playlist, String track)
            throws PlaylistNotFoundException, TrackNotFoundException;

    /**
     * Removes a track from a playlist.
     *
     * @throws PlaylistNotFoundException if no such playlist exists.
     * @throws TrackNotFoundException    if the playlist does not contain the given track.
     */
    void removeFromPlaylist(String playlist, String track)
            throws PlaylistNotFoundException, TrackNotFoundException;

    /**
     * Changes the play mode of a playlist.
     *
     * @throws PlaylistNotFoundException if no such playlist exists.
     */
    void setPlaylistMode(String playlist, PlayMode mode) throws PlaylistNotFoundException;

    // ----------------------------------------------------------------------
    //                              PLAYBACK
    // ----------------------------------------------------------------------

    /**
     * Plays a library track to a single player as a static, always-audible channel.
     *
     * @param volume linear gain on top of the global music volume, range {@code [0,1]}.
     * @return a handle controlling the playback.
     * @throws TrackNotFoundException if the track does not exist.
     * @throws InvalidVolumeException if {@code volume} is out of range or NaN.
     */
    PlaybackHandle playToPlayer(Player player, String track, float volume)
            throws TrackNotFoundException, InvalidVolumeException;

    /**
     * Plays a library track to a group of players, one channel per listener.
     *
     * @throws TrackNotFoundException if the track does not exist.
     * @throws InvalidVolumeException if {@code volume} is out of range or NaN.
     */
    PlaybackHandle playToPlayers(Collection<Player> players, String track, float volume)
            throws TrackNotFoundException, InvalidVolumeException;

    /**
     * Plays a library track to every online player.
     *
     * @throws TrackNotFoundException if the track does not exist.
     * @throws InvalidVolumeException if {@code volume} is out of range or NaN.
     */
    PlaybackHandle playToAll(String track, float volume)
            throws TrackNotFoundException, InvalidVolumeException;

    /**
     * Plays a library track at a fixed location, audible to anyone within {@code radius}
     * blocks.
     *
     * @throws TrackNotFoundException if the track does not exist.
     * @throws InvalidVolumeException if {@code volume} is out of range or NaN.
     */
    PlaybackHandle playAt(Location location, float radius, String track, float volume)
            throws TrackNotFoundException, InvalidVolumeException;

    /**
     * Plays an arbitrary audio file (anywhere on the filesystem) at a fixed
     * location. The file must use a supported format (wav/mp3/flac) and must be
     * decodable. The path is not required to live inside {@code musicdata/}.
     *
     * @throws InvalidAudioFileException if the file is missing, unreadable or unsupported.
     * @throws InvalidVolumeException    if {@code volume} is out of range or NaN.
     */
    PlaybackHandle playFile(Location location, float radius, Path file, float volume)
            throws InvalidAudioFileException, InvalidVolumeException;

    /**
     * Plays a track {@code times} times in a row at the given location. A value of
     * {@code 0} means &laquo;loop forever until stopped&raquo;.
     *
     * @throws TrackNotFoundException if the track does not exist.
     * @throws InvalidVolumeException if {@code volume} is out of range or NaN.
     * @throws IllegalArgumentException if {@code times} is negative.
     */
    PlaybackHandle playRepeated(Location location, float radius, String track,
                                float volume, int times)
            throws TrackNotFoundException, InvalidVolumeException;

    // ----------------------------------------------------------------------
    //                                SFX
    // ----------------------------------------------------------------------

    /**
     * Plays the configured sound effect for the given event to a single player.
     * No-op if SFX are disabled in the config or if no file is mapped for the event.
     */
    void playSfx(SfxEvent event, Player player);

    /** Plays a sound effect to every online player. */
    void broadcastSfx(SfxEvent event);

    /** Globally toggles SFX playback at runtime (does not persist). */
    void setSfxEnabled(boolean enabled);

    /** {@code true} if SFX are currently enabled. */
    boolean isSfxEnabled();
}
