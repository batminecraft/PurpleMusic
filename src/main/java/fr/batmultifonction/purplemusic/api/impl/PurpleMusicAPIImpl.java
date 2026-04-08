package fr.batmultifonction.purplemusic.api.impl;

import fr.batmultifonction.purplemusic.PurpleMusic;
import fr.batmultifonction.purplemusic.api.PlayMode;
import fr.batmultifonction.purplemusic.api.PlaybackHandle;
import fr.batmultifonction.purplemusic.api.PlaylistView;
import fr.batmultifonction.purplemusic.api.PurpleMusicAPI;
import fr.batmultifonction.purplemusic.api.SfxEvent;
import fr.batmultifonction.purplemusic.api.exception.InvalidAudioFileException;
import fr.batmultifonction.purplemusic.api.exception.InvalidTrackNameException;
import fr.batmultifonction.purplemusic.api.exception.InvalidVolumeException;
import fr.batmultifonction.purplemusic.api.exception.PlaylistAlreadyExistsException;
import fr.batmultifonction.purplemusic.api.exception.PlaylistNotFoundException;
import fr.batmultifonction.purplemusic.api.exception.TrackAlreadyExistsException;
import fr.batmultifonction.purplemusic.api.exception.TrackNotFoundException;
import fr.batmultifonction.purplemusic.audio.AudioDecoder;
import fr.batmultifonction.purplemusic.audio.AudioEngine;
import fr.batmultifonction.purplemusic.audio.SfxManager;
import fr.batmultifonction.purplemusic.library.MusicLibrary;
import fr.batmultifonction.purplemusic.library.Playlist;
import fr.batmultifonction.purplemusic.library.PlaylistManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of the {@link PurpleMusicAPI}, backed by the running
 * PurpleMusic plugin instance.
 */
public class PurpleMusicAPIImpl implements PurpleMusicAPI {

    private final PurpleMusic plugin;
    private final MusicLibrary library;
    private final PlaylistManager playlists;
    private final AudioEngine engine;
    private final SfxManager sfx;

    public PurpleMusicAPIImpl(PurpleMusic plugin) {
        this.plugin = plugin;
        this.library = plugin.library();
        this.playlists = plugin.playlists();
        this.engine = plugin.audioEngine();
        this.sfx = plugin.sfx();
    }

    // ---------------- LIBRARY ----------------

    @Override public Path libraryRoot() { return library.root(); }

    @Override
    public List<String> listTracks() {
        return Collections.unmodifiableList(library.listTracks());
    }

    @Override
    public boolean trackExists(String name) {
        return library.resolve(name) != null;
    }

    @Override
    public Path getTrackPath(String name) throws TrackNotFoundException {
        Path p = library.resolve(name);
        if (p == null) throw new TrackNotFoundException(name);
        return p;
    }

    @Override
    public void addTrack(String name, InputStream data)
            throws InvalidTrackNameException, TrackAlreadyExistsException, InvalidAudioFileException {
        if (name == null || name.isBlank()) {
            throw new InvalidTrackNameException("Track name must not be empty");
        }
        Path dest = library.resolveDestination(name);
        if (dest == null) {
            throw new InvalidTrackNameException("Invalid track name: " + name);
        }
        if (!AudioDecoder.isSupported(dest.getFileName().toString())) {
            throw new InvalidTrackNameException(
                    "Unsupported audio format (use wav/mp3/flac): " + name);
        }
        if (Files.exists(dest)) {
            throw new TrackAlreadyExistsException(name);
        }
        try {
            library.importFile(name, data);
        } catch (IOException e) {
            throw new InvalidAudioFileException("Failed to import track: " + name, e);
        }
        // Validate decoding so we never keep an unreadable file in the library.
        try {
            AudioDecoder.open(dest).close();
        } catch (Exception e) {
            try { Files.deleteIfExists(dest); } catch (IOException ignored) {}
            throw new InvalidAudioFileException("Audio file could not be decoded: " + name, e);
        }
    }

    @Override
    public void deleteTrack(String name) throws TrackNotFoundException {
        if (library.resolve(name) == null) throw new TrackNotFoundException(name);
        try {
            library.deleteFile(name);
        } catch (IOException e) {
            throw new TrackNotFoundException(name);
        }
    }

    @Override
    public void renameTrack(String oldName, String newName)
            throws TrackNotFoundException, TrackAlreadyExistsException, InvalidTrackNameException {
        if (library.resolve(oldName) == null) throw new TrackNotFoundException(oldName);
        Path dest = library.resolveDestination(newName);
        if (dest == null) throw new InvalidTrackNameException("Invalid new name: " + newName);
        if (!AudioDecoder.isSupported(dest.getFileName().toString())) {
            throw new InvalidTrackNameException("Unsupported audio format: " + newName);
        }
        if (Files.exists(dest)) throw new TrackAlreadyExistsException(newName);
        try {
            library.renameFile(oldName, newName);
        } catch (IOException e) {
            throw new InvalidTrackNameException("Rename failed: " + e.getMessage());
        }
    }

    // ---------------- PLAYLISTS ----------------

    @Override
    public List<String> listPlaylists() {
        return Collections.unmodifiableList(playlists.names());
    }

    @Override
    public boolean playlistExists(String name) {
        return playlists.get(name) != null;
    }

    @Override
    public PlaylistView getPlaylist(String name) throws PlaylistNotFoundException {
        Playlist pl = playlists.get(name);
        if (pl == null) throw new PlaylistNotFoundException(name);
        return new PlaylistViewImpl(pl);
    }

    @Override
    public void createPlaylist(String name, PlayMode mode) throws PlaylistAlreadyExistsException {
        if (playlists.get(name) != null) throw new PlaylistAlreadyExistsException(name);
        Playlist pl = playlists.create(name);
        pl.setMode(toInternalMode(mode));
        playlists.save();
    }

    @Override
    public void deletePlaylist(String name) throws PlaylistNotFoundException {
        if (!playlists.delete(name)) throw new PlaylistNotFoundException(name);
    }

    @Override
    public void addToPlaylist(String playlist, String track)
            throws PlaylistNotFoundException, TrackNotFoundException {
        Playlist pl = playlists.get(playlist);
        if (pl == null) throw new PlaylistNotFoundException(playlist);
        if (library.resolve(track) == null) throw new TrackNotFoundException(track);
        pl.add(track);
        playlists.save();
    }

    @Override
    public void removeFromPlaylist(String playlist, String track)
            throws PlaylistNotFoundException, TrackNotFoundException {
        Playlist pl = playlists.get(playlist);
        if (pl == null) throw new PlaylistNotFoundException(playlist);
        if (!pl.remove(track)) throw new TrackNotFoundException(track);
        playlists.save();
    }

    @Override
    public void setPlaylistMode(String playlist, PlayMode mode) throws PlaylistNotFoundException {
        Playlist pl = playlists.get(playlist);
        if (pl == null) throw new PlaylistNotFoundException(playlist);
        pl.setMode(toInternalMode(mode));
        playlists.save();
    }

    private static Playlist.Mode toInternalMode(PlayMode m) {
        if (m == null) return Playlist.Mode.SEQUENTIAL;
        return switch (m) {
            case SEQUENTIAL -> Playlist.Mode.SEQUENTIAL;
            case LOOP -> Playlist.Mode.LOOP;
            case LOOP_ONE -> Playlist.Mode.LOOP_ONE;
            case SHUFFLE -> Playlist.Mode.SHUFFLE;
        };
    }

    // ---------------- PLAYBACK ----------------

    private static void checkVolume(float v) throws InvalidVolumeException {
        if (Float.isNaN(v) || v < 0f || v > 1f) throw new InvalidVolumeException(v);
    }

    private Path requireTrack(String name) throws TrackNotFoundException {
        Path p = library.resolve(name);
        if (p == null) throw new TrackNotFoundException(name);
        return p;
    }

    @Override
    public PlaybackHandle playToPlayer(Player player, String track, float volume)
            throws TrackNotFoundException, InvalidVolumeException {
        checkVolume(volume);
        Path file = requireTrack(track);
        return wrap(engine.playToPlayer(player, file, volume));
    }

    @Override
    public PlaybackHandle playToPlayers(Collection<Player> players, String track, float volume)
            throws TrackNotFoundException, InvalidVolumeException {
        checkVolume(volume);
        Path file = requireTrack(track);
        return wrap(engine.playToPlayers(new ArrayList<>(players), file, volume));
    }

    @Override
    public PlaybackHandle playToAll(String track, float volume)
            throws TrackNotFoundException, InvalidVolumeException {
        checkVolume(volume);
        Path file = requireTrack(track);
        return wrap(engine.playToPlayers(new ArrayList<>(Bukkit.getOnlinePlayers()), file, volume));
    }

    @Override
    public PlaybackHandle playAt(Location location, float radius, String track, float volume)
            throws TrackNotFoundException, InvalidVolumeException {
        checkVolume(volume);
        Path file = requireTrack(track);
        return wrap(engine.playLocational(location, radius, file, volume));
    }

    @Override
    public PlaybackHandle playFile(Location location, float radius, Path file, float volume)
            throws InvalidAudioFileException, InvalidVolumeException {
        checkVolume(volume);
        if (file == null || !Files.isRegularFile(file)) {
            throw new InvalidAudioFileException("File is missing: " + file);
        }
        if (!AudioDecoder.isSupported(file.getFileName().toString())) {
            throw new InvalidAudioFileException("Unsupported audio format: " + file);
        }
        try {
            AudioDecoder.open(file).close();
        } catch (Exception e) {
            throw new InvalidAudioFileException("Could not decode " + file, e);
        }
        return wrap(engine.playLocational(location, radius, file, volume));
    }

    @Override
    public PlaybackHandle playRepeated(Location location, float radius, String track,
                                       float volume, int times)
            throws TrackNotFoundException, InvalidVolumeException {
        if (times < 0) throw new IllegalArgumentException("times must be >= 0");
        checkVolume(volume);
        final Path file = requireTrack(track);
        final RepeatingHandle handle = new RepeatingHandle();
        playRepeatedLoop(handle, location, radius, file, volume, times);
        return handle;
    }

    private void playRepeatedLoop(RepeatingHandle handle, Location location, float radius,
                                  Path file, float volume, int remaining) {
        if (handle.isCancelled()) { handle.markStopped(); return; }
        AudioEngine.Handle h = engine.playLocational(location, radius, file, volume);
        handle.bindCurrent(h);
        h.onComplete(() -> {
            int next = remaining == 0 ? 0 : remaining - 1; // 0 means infinite
            if (handle.isCancelled() || (remaining != 0 && next <= 0)) {
                handle.markStopped();
                return;
            }
            playRepeatedLoop(handle, location, radius, file, volume, next);
        });
    }

    private static PlaybackHandle wrap(AudioEngine.Handle h) {
        return new EngineHandleAdapter(h);
    }

    // ---------------- SFX ----------------

    @Override public void playSfx(SfxEvent event, Player player) { sfx.play(event, player); }
    @Override public void broadcastSfx(SfxEvent event) { sfx.broadcast(event); }
    @Override public void setSfxEnabled(boolean enabled) { sfx.setEnabled(enabled); }
    @Override public boolean isSfxEnabled() { return sfx.isEnabled(); }

    // ---------------- helpers ----------------

    /** Wraps an internal {@link Playlist} as an immutable {@link PlaylistView}. */
    private static final class PlaylistViewImpl implements PlaylistView {
        private final String name;
        private final PlayMode mode;
        private final List<String> tracks;
        PlaylistViewImpl(Playlist pl) {
            this.name = pl.name();
            this.mode = PlayMode.parse(pl.mode().name());
            this.tracks = List.copyOf(pl.tracks());
        }
        @Override public String name() { return name; }
        @Override public PlayMode mode() { return mode; }
        @Override public List<String> tracks() { return tracks; }
    }

    /** Adapts an internal {@link AudioEngine.Handle} to a public {@link PlaybackHandle}. */
    private static final class EngineHandleAdapter implements PlaybackHandle {
        private final AudioEngine.Handle handle;
        EngineHandleAdapter(AudioEngine.Handle handle) { this.handle = handle; }
        @Override public PlaybackState state() {
            return handle.isStopped() ? PlaybackState.STOPPED : PlaybackState.PLAYING;
        }
        @Override public void stop() { handle.stop(); }
        @Override public void onComplete(Runnable callback) { handle.onComplete(callback); }
    }

    /** Handle implementation used for repeated playback. */
    private static final class RepeatingHandle implements PlaybackHandle {
        private volatile AudioEngine.Handle current;
        private volatile boolean cancelled;
        private volatile boolean stopped;
        private Runnable onComplete;

        synchronized void bindCurrent(AudioEngine.Handle h) {
            this.current = h;
            if (cancelled) h.stop();
        }

        boolean isCancelled() { return cancelled; }

        synchronized void markStopped() {
            stopped = true;
            if (onComplete != null) {
                Runnable cb = onComplete;
                onComplete = null;
                cb.run();
            }
        }

        @Override
        public PlaybackState state() {
            if (stopped) return PlaybackState.STOPPED;
            return PlaybackState.PLAYING;
        }

        @Override
        public synchronized void stop() {
            cancelled = true;
            if (current != null) current.stop();
        }

        @Override
        public synchronized void onComplete(Runnable callback) {
            if (stopped) callback.run();
            else this.onComplete = callback;
        }
    }
}
