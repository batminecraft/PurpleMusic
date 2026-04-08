package fr.batmultifonction.purplemusic.audio;

import fr.batmultifonction.purplemusic.PurpleMusic;
import fr.batmultifonction.purplemusic.api.SfxEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Loads short sound-effect files from {@code plugins/PurpleMusic/sfx/} and plays
 * them on demand for {@link SfxEvent} events.
 *
 * <p>The mapping is configured under the {@code sfx:} section of {@code config.yml}.
 * Each event maps to a filename (without folder prefix) inside the {@code sfx/}
 * data folder. SFX as a whole can be disabled with {@code sfx.enabled: false}.</p>
 */
public class SfxManager {

    private final PurpleMusic plugin;
    private final AudioEngine engine;
    private final File folder;
    private final Map<SfxEvent, Path> mappings = new EnumMap<>(SfxEvent.class);
    private volatile boolean enabled = true;
    private volatile float volume = 1.0f;

    private static final List<String> BUNDLED_FILES =
            List.of("play.wav", "pause.wav", "resume.wav", "stop.wav", "skip.wav");

    public SfxManager(PurpleMusic plugin, AudioEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
        this.folder = new File(plugin.getDataFolder(), "sfx");
        if (!folder.exists()) folder.mkdirs();
        copyBundledIfMissing();
    }

    private void copyBundledIfMissing() {
        for (String name : BUNDLED_FILES) {
            File out = new File(folder, name);
            if (out.exists()) continue;
            try (InputStream in = plugin.getResource("sfx/" + name)) {
                if (in == null) continue;
                Files.copy(in, out.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to copy sfx/" + name + ": " + e.getMessage());
            }
        }
    }

    /** Re-reads the {@code sfx:} section of config.yml. */
    public void reload() {
        mappings.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("sfx");
        if (sec == null) {
            enabled = false;
            return;
        }
        enabled = sec.getBoolean("enabled", true);
        double v = sec.getDouble("volume", 1.0d);
        if (Double.isNaN(v) || v < 0d) v = 0d;
        if (v > 1d) v = 1d;
        volume = (float) v;
        for (SfxEvent e : SfxEvent.values()) {
            String name = sec.getString(e.configKey(), null);
            if (name == null || name.isBlank()) continue;
            Path file = folder.toPath().resolve(name).toAbsolutePath().normalize();
            if (!file.startsWith(folder.toPath().toAbsolutePath().normalize())) continue;
            if (!Files.isRegularFile(file)) {
                plugin.getLogger().warning(
                        "SFX file for event " + e + " not found: " + file);
                continue;
            }
            if (!AudioDecoder.isSupported(file.getFileName().toString())) {
                plugin.getLogger().warning(
                        "SFX file for event " + e + " uses an unsupported format: " + file);
                continue;
            }
            mappings.put(e, file);
        }
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Plays the SFX bound to {@code event} for a single player. */
    public void play(SfxEvent event, Player player) {
        if (!enabled || player == null) return;
        Path file = mappings.get(event);
        if (file == null) return;
        engine.playToPlayer(player, file, volume);
    }

    /** Plays the SFX bound to {@code event} for every online player. */
    public void broadcast(SfxEvent event) {
        if (!enabled) return;
        Path file = mappings.get(event);
        if (file == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            engine.playToPlayer(p, file, volume);
        }
    }
}
