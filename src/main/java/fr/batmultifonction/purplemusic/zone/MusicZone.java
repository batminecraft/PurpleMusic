package fr.batmultifonction.purplemusic.zone;

import fr.batmultifonction.purplemusic.audio.AudioEngine;
import fr.batmultifonction.purplemusic.library.Playlist;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * A music zone: a sphere centered at a location, playing a track or playlist for
 * everyone within the radius. Schedule is either CONTINUOUS (loop forever) or
 * INTERVAL (replay every N seconds).
 */
public class MusicZone {

    public enum Schedule { CONTINUOUS, INTERVAL }
    public enum SourceType { TRACK, PLAYLIST }

    private final String name;
    private final UUID worldId;
    private final double x, y, z;
    private final float radius;
    private final SourceType sourceType;
    private final String source;          // track filename or playlist name
    private Playlist.Mode playlistMode;   // optional override for playlists
    private final Schedule schedule;
    private final long intervalSeconds;   // only used when schedule == INTERVAL
    private float volume;                 // 0.0..1.0 multiplier applied on top of the global music-volume

    // Runtime state - not serialized
    private transient AudioEngine.Handle currentHandle;
    private transient int playlistIndex = -1;
    private transient long nextStartEpochSec;

    public MusicZone(String name, UUID worldId, double x, double y, double z, float radius,
                     SourceType sourceType, String source, Playlist.Mode mode,
                     Schedule schedule, long intervalSeconds, float volume) {
        this.name = name;
        this.worldId = worldId;
        this.x = x; this.y = y; this.z = z;
        this.radius = radius;
        this.sourceType = sourceType;
        this.source = source;
        this.playlistMode = mode;
        this.schedule = schedule;
        this.intervalSeconds = intervalSeconds;
        this.volume = clampVolume(volume);
    }

    public static float clampVolume(float v) {
        if (Float.isNaN(v) || v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    public String name() { return name; }
    public UUID worldId() { return worldId; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float radius() { return radius; }
    public SourceType sourceType() { return sourceType; }
    public String source() { return source; }
    public Playlist.Mode playlistMode() { return playlistMode; }
    public void setPlaylistMode(Playlist.Mode mode) { this.playlistMode = mode; }
    public Schedule schedule() { return schedule; }
    public long intervalSeconds() { return intervalSeconds; }
    public float volume() { return volume; }
    public void setVolume(float volume) { this.volume = clampVolume(volume); }

    public Location locationIn(World world) { return new Location(world, x, y, z); }

    public AudioEngine.Handle currentHandle() { return currentHandle; }
    public void setCurrentHandle(AudioEngine.Handle h) { this.currentHandle = h; }
    public int playlistIndex() { return playlistIndex; }
    public void setPlaylistIndex(int i) { this.playlistIndex = i; }
    public long nextStartEpochSec() { return nextStartEpochSec; }
    public void setNextStartEpochSec(long t) { this.nextStartEpochSec = t; }
}
