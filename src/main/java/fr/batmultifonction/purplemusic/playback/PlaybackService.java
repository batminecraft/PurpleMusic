package fr.batmultifonction.purplemusic.playback;

import fr.batmultifonction.purplemusic.PurpleMusic;
import fr.batmultifonction.purplemusic.audio.AudioEngine;
import fr.batmultifonction.purplemusic.library.Playlist;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-level music playback for players. Holds one {@link PlaybackSession} per target
 * (player UUID, or a single shared "ALL" session) and chains tracks/queues/playlists.
 */
public class PlaybackService {

    public static final UUID ALL = new UUID(0L, 0L);

    private final PurpleMusic plugin;
    private final AudioEngine engine;
    private final Map<UUID, PlaybackSession> sessions = new ConcurrentHashMap<>();

    public PlaybackService(PurpleMusic plugin, AudioEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    private PlaybackSession sessionFor(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(),
                u -> new PlaybackSession(PlaybackSession.TargetType.PLAYER, u));
    }

    private PlaybackSession globalSession() {
        return sessions.computeIfAbsent(ALL,
                u -> new PlaybackSession(PlaybackSession.TargetType.ALL, null));
    }

    // -------- play immediately --------

    /** Stops anything currently playing for this player and starts the given track. */
    public boolean playTrackToPlayer(Player player, String trackName) {
        Path file = plugin.library().resolve(trackName);
        if (file == null) return false;
        PlaybackSession s = sessionFor(player);
        stopSession(s);
        s.setCurrentTrack(trackName);
        s.clearPlaylist();
        AudioEngine.Handle h = engine.playToPlayer(player, file);
        s.setCurrentHandle(h);
        h.onComplete(() -> onTrackEnded(s));
        return true;
    }

    public boolean playTrackToAll(String trackName) {
        Path file = plugin.library().resolve(trackName);
        if (file == null) return false;
        PlaybackSession s = globalSession();
        stopSession(s);
        s.setCurrentTrack(trackName);
        s.clearPlaylist();
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        AudioEngine.Handle h = engine.playToPlayers(new ArrayList<>(players), file);
        s.setCurrentHandle(h);
        h.onComplete(() -> onTrackEnded(s));
        return true;
    }

    public boolean playPlaylistToPlayer(Player player, String playlistName) {
        Playlist pl = plugin.playlists().get(playlistName);
        if (pl == null || pl.isEmpty()) return false;
        PlaybackSession s = sessionFor(player);
        stopSession(s);
        s.setActivePlaylist(pl);
        s.setPlaylistIndex(0);
        return playPlaylistTrack(s, player, null);
    }

    public boolean playPlaylistToAll(String playlistName) {
        Playlist pl = plugin.playlists().get(playlistName);
        if (pl == null || pl.isEmpty()) return false;
        PlaybackSession s = globalSession();
        stopSession(s);
        s.setActivePlaylist(pl);
        s.setPlaylistIndex(0);
        return playPlaylistTrack(s, null, new ArrayList<>(Bukkit.getOnlinePlayers()));
    }

    private boolean playPlaylistTrack(PlaybackSession s, Player single, List<Player> group) {
        Playlist pl = s.activePlaylist();
        int idx = s.playlistIndex();
        if (pl == null || idx < 0 || idx >= pl.size()) return false;
        String track = pl.tracks().get(idx);
        Path file = plugin.library().resolve(track);
        if (file == null) {
            // skip missing
            advancePlaylist(s, single, group);
            return false;
        }
        s.setCurrentTrack(track);
        AudioEngine.Handle h = (single != null)
                ? engine.playToPlayer(single, file)
                : engine.playToPlayers(group != null ? group : new ArrayList<>(Bukkit.getOnlinePlayers()), file);
        s.setCurrentHandle(h);
        final Player f1 = single;
        final List<Player> f2 = group;
        h.onComplete(() -> advancePlaylist(s, f1, f2));
        return true;
    }

    private void advancePlaylist(PlaybackSession s, Player single, List<Player> group) {
        Playlist pl = s.activePlaylist();
        if (pl == null) {
            onTrackEnded(s);
            return;
        }
        int next = pl.nextIndex(s.playlistIndex(), s.rng());
        if (next < 0) {
            s.clearPlaylist();
            onTrackEnded(s);
            return;
        }
        s.setPlaylistIndex(next);
        playPlaylistTrack(s, single, group);
    }

    private void onTrackEnded(PlaybackSession s) {
        s.setCurrentHandle(null);
        s.setCurrentTrack(null);
        // try queue
        if (!s.queue().isEmpty()) {
            String next = s.queue().pollFirst();
            Path file = plugin.library().resolve(next);
            if (file == null) {
                onTrackEnded(s); // skip missing
                return;
            }
            s.setCurrentTrack(next);
            AudioEngine.Handle h;
            if (s.targetType() == PlaybackSession.TargetType.ALL) {
                h = engine.playToPlayers(new ArrayList<>(Bukkit.getOnlinePlayers()), file);
            } else {
                Player p = Bukkit.getPlayer(s.playerUuid());
                if (p == null) return;
                h = engine.playToPlayer(p, file);
            }
            s.setCurrentHandle(h);
            h.onComplete(() -> onTrackEnded(s));
        }
    }

    // -------- queue management --------

    public boolean enqueueForPlayer(Player player, String trackName) {
        if (plugin.library().resolve(trackName) == null) return false;
        sessionFor(player).queue().addLast(trackName);
        return true;
    }

    public boolean enqueueForAll(String trackName) {
        if (plugin.library().resolve(trackName) == null) return false;
        globalSession().queue().addLast(trackName);
        return true;
    }

    public List<String> queueOf(Player player) {
        PlaybackSession s = sessions.get(player.getUniqueId());
        return s == null ? List.of() : new ArrayList<>(s.queue());
    }

    public List<String> queueOfAll() {
        PlaybackSession s = sessions.get(ALL);
        return s == null ? List.of() : new ArrayList<>(s.queue());
    }

    public void clearQueue(Player player) {
        PlaybackSession s = sessions.get(player.getUniqueId());
        if (s != null) s.queue().clear();
    }

    public void clearGlobalQueue() {
        PlaybackSession s = sessions.get(ALL);
        if (s != null) s.queue().clear();
    }

    /** Stops the current track and triggers the next from the queue/playlist. */
    public void skip(Player player) {
        PlaybackSession s = sessions.get(player.getUniqueId());
        if (s != null && s.currentHandle() != null) s.currentHandle().stop();
    }

    public void skipGlobal() {
        PlaybackSession s = sessions.get(ALL);
        if (s != null && s.currentHandle() != null) s.currentHandle().stop();
    }

    // -------- stop --------

    public void stop(Player player) {
        PlaybackSession s = sessions.get(player.getUniqueId());
        if (s != null) {
            s.queue().clear();
            s.clearPlaylist();
            stopSession(s);
        }
    }

    public void stopAll() {
        for (PlaybackSession s : sessions.values()) {
            s.queue().clear();
            s.clearPlaylist();
            stopSession(s);
        }
    }

    public void stopGlobal() {
        PlaybackSession s = sessions.get(ALL);
        if (s != null) {
            s.queue().clear();
            s.clearPlaylist();
            stopSession(s);
        }
    }

    private void stopSession(PlaybackSession s) {
        AudioEngine.Handle h = s.currentHandle();
        if (h != null) h.stop();
        s.setCurrentHandle(null);
        s.setCurrentTrack(null);
    }

    public Collection<PlaybackSession> sessions() { return sessions.values(); }
}
