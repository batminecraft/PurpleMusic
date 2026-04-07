package fr.batmultifonction.purplemusic.playback;

import fr.batmultifonction.purplemusic.audio.AudioEngine;
import fr.batmultifonction.purplemusic.library.Playlist;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Holds the queue and current playback state for one target (a single player,
 * a group of players, or "global"/all players).
 */
public class PlaybackSession {

    public enum TargetType { PLAYER, ALL }

    private final TargetType targetType;
    private final java.util.UUID playerUuid; // null for ALL

    private final Deque<String> queue = new ArrayDeque<>();
    private Playlist activePlaylist;
    private Playlist.Mode mode = Playlist.Mode.SEQUENTIAL;
    private int playlistIndex = -1;
    private String currentTrack;
    private AudioEngine.Handle currentHandle;
    private final Random rng = new Random();

    public PlaybackSession(TargetType type, java.util.UUID uuid) {
        this.targetType = type;
        this.playerUuid = uuid;
    }

    public TargetType targetType() { return targetType; }
    public java.util.UUID playerUuid() { return playerUuid; }

    public Deque<String> queue() { return queue; }
    public Playlist activePlaylist() { return activePlaylist; }
    public Playlist.Mode mode() { return mode; }
    public void setMode(Playlist.Mode mode) { this.mode = mode; }
    public int playlistIndex() { return playlistIndex; }
    public void setPlaylistIndex(int idx) { this.playlistIndex = idx; }
    public String currentTrack() { return currentTrack; }
    public void setCurrentTrack(String t) { this.currentTrack = t; }
    public AudioEngine.Handle currentHandle() { return currentHandle; }
    public void setCurrentHandle(AudioEngine.Handle h) { this.currentHandle = h; }

    public void setActivePlaylist(Playlist pl) {
        this.activePlaylist = pl;
        this.playlistIndex = -1;
        if (pl != null) this.mode = pl.mode();
    }

    public void clearPlaylist() {
        this.activePlaylist = null;
        this.playlistIndex = -1;
    }

    public Random rng() { return rng; }
}
