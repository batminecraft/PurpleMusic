package fr.batmultifonction.purplemusic.library;

import fr.batmultifonction.purplemusic.PurpleMusic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads, persists, and serves named playlists. Stored in playlists.yml.
 */
public class PlaylistManager {

    private final PurpleMusic plugin;
    private final File file;
    private final Map<String, Playlist> playlists = new LinkedHashMap<>();

    public PlaylistManager(PurpleMusic plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playlists.yml");
    }

    public void load() {
        playlists.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("playlists");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) continue;
            Playlist pl = new Playlist(name);
            pl.setMode(Playlist.Mode.parse(sec.getString("mode", "SEQUENTIAL")));
            for (String t : sec.getStringList("tracks")) pl.add(t);
            playlists.put(name.toLowerCase(), pl);
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Playlist pl : playlists.values()) {
            String base = "playlists." + pl.name();
            cfg.set(base + ".mode", pl.mode().name());
            cfg.set(base + ".tracks", pl.tracks());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save playlists.yml: " + e.getMessage());
        }
    }

    public Playlist get(String name) {
        if (name == null) return null;
        return playlists.get(name.toLowerCase());
    }

    public Playlist create(String name) {
        Playlist pl = new Playlist(name);
        playlists.put(name.toLowerCase(), pl);
        save();
        return pl;
    }

    public boolean delete(String name) {
        if (playlists.remove(name.toLowerCase()) != null) {
            save();
            return true;
        }
        return false;
    }

    public Collection<Playlist> all() { return playlists.values(); }

    public List<String> names() { return playlists.values().stream().map(Playlist::name).toList(); }
}
