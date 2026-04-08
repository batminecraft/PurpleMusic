package fr.batmultifonction.purplemusic.zone;

import fr.batmultifonction.purplemusic.PurpleMusic;
import fr.batmultifonction.purplemusic.audio.AudioEngine;
import fr.batmultifonction.purplemusic.library.Playlist;
import fr.batmultifonction.purplemusic.library.PlaylistManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Loads, saves, ticks, and serves music zones. Each tick the manager checks every
 * zone and starts/restarts/loops its audio according to its schedule.
 */
public class ZoneManager {

    private final PurpleMusic plugin;
    private final AudioEngine engine;
    private final PlaylistManager playlists;
    private final File file;
    private final Map<String, MusicZone> zones = new LinkedHashMap<>();
    private final Random rng = new Random();
    private ScheduledTask task;

    public ZoneManager(PurpleMusic plugin, AudioEngine engine, PlaylistManager playlists) {
        this.plugin = plugin;
        this.engine = engine;
        this.playlists = playlists;
        this.file = new File(plugin.getDataFolder(), "zones.yml");
    }

    // ---------- persistence ----------

    public void load() {
        zones.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("zones");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) continue;
            try {
                MusicZone z = new MusicZone(
                        name,
                        UUID.fromString(sec.getString("world")),
                        sec.getDouble("x"),
                        sec.getDouble("y"),
                        sec.getDouble("z"),
                        (float) sec.getDouble("radius"),
                        MusicZone.SourceType.valueOf(sec.getString("source-type", "TRACK")),
                        sec.getString("source"),
                        Playlist.Mode.parse(sec.getString("playlist-mode", "SEQUENTIAL")),
                        MusicZone.Schedule.valueOf(sec.getString("schedule", "CONTINUOUS")),
                        sec.getLong("interval-seconds", 0),
                        (float) sec.getDouble("volume", 1.0d));
                zones.put(name.toLowerCase(), z);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load zone '" + name + "': " + e.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (MusicZone z : zones.values()) {
            String base = "zones." + z.name();
            cfg.set(base + ".world", z.worldId().toString());
            cfg.set(base + ".x", z.x());
            cfg.set(base + ".y", z.y());
            cfg.set(base + ".z", z.z());
            cfg.set(base + ".radius", z.radius());
            cfg.set(base + ".source-type", z.sourceType().name());
            cfg.set(base + ".source", z.source());
            cfg.set(base + ".playlist-mode", z.playlistMode().name());
            cfg.set(base + ".schedule", z.schedule().name());
            cfg.set(base + ".interval-seconds", z.intervalSeconds());
            cfg.set(base + ".volume", z.volume());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save zones.yml: " + e.getMessage());
        }
    }

    // ---------- CRUD ----------

    public boolean exists(String name) { return zones.containsKey(name.toLowerCase()); }
    public MusicZone get(String name) { return zones.get(name.toLowerCase()); }
    public Collection<MusicZone> all() { return zones.values(); }

    public MusicZone create(MusicZone zone) {
        zones.put(zone.name().toLowerCase(), zone);
        save();
        return zone;
    }

    public boolean delete(String name) {
        MusicZone z = zones.remove(name.toLowerCase());
        if (z == null) return false;
        if (z.currentHandle() != null) z.currentHandle().stop();
        save();
        return true;
    }

    // ---------- ticking ----------

    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> tick(), 1L, 1L, TimeUnit.SECONDS);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        for (MusicZone z : zones.values()) {
            if (z.currentHandle() != null) z.currentHandle().stop();
        }
    }

    public void reload() {
        stop();
        load();
        start();
    }

    private void tick() {
        long now = System.currentTimeMillis() / 1000L;
        for (MusicZone z : zones.values()) {
            if (z.currentHandle() != null && !z.currentHandle().isStopped()) continue;
            // not currently playing — should we start?
            if (z.schedule() == MusicZone.Schedule.CONTINUOUS) {
                startZone(z);
            } else { // INTERVAL
                if (z.nextStartEpochSec() == 0L) {
                    // first run: schedule immediately
                    startZone(z);
                    z.setNextStartEpochSec(now + Math.max(1, z.intervalSeconds()));
                } else if (now >= z.nextStartEpochSec()) {
                    startZone(z);
                    z.setNextStartEpochSec(now + Math.max(1, z.intervalSeconds()));
                }
            }
        }
    }

    private void startZone(MusicZone z) {
        World world = Bukkit.getWorld(z.worldId());
        if (world == null) return;
        Path file;
        final boolean isPlaylist = z.sourceType() == MusicZone.SourceType.PLAYLIST;
        if (!isPlaylist) {
            file = plugin.library().resolve(z.source());
            if (file == null) return;
            playOnce(z, world, file, false);
        } else {
            Playlist pl = playlists.get(z.source());
            if (pl == null || pl.isEmpty()) return;
            int idx = z.playlistIndex();
            if (idx < 0) idx = 0;
            if (idx >= pl.size()) idx = 0;
            String trackName = pl.tracks().get(idx);
            file = plugin.library().resolve(trackName);
            z.setPlaylistIndex(idx);
            if (file == null) {
                // skip missing entry
                int next = pl.nextIndex(idx, rng);
                z.setPlaylistIndex(next);
                return;
            }
            playOnce(z, world, file, true);
        }
    }

    private void playOnce(MusicZone z, World world, Path file, boolean isPlaylist) {
        AudioEngine.Handle h = engine.playLocational(
                z.locationIn(world), z.radius(), file, z.volume());
        z.setCurrentHandle(h);
        h.onComplete(() -> {
            z.setCurrentHandle(null);
            if (isPlaylist) {
                Playlist pl = playlists.get(z.source());
                if (pl != null && !pl.isEmpty()) {
                    // honor zone-level mode override
                    Playlist.Mode prev = pl.mode();
                    pl.setMode(z.playlistMode());
                    int next = pl.nextIndex(z.playlistIndex(), rng);
                    pl.setMode(prev);
                    z.setPlaylistIndex(next);
                }
            }
            // continuous tracks: tick() will restart it on the next pass
        });
    }
}
