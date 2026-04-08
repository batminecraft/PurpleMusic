package fr.batmultifonction.purplemusic.util;

import fr.batmultifonction.purplemusic.PurpleMusic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight forward-only migrator for {@code config.yml}.
 *
 * <p>On every startup the bundled {@code config.yml} (the one inside the jar)
 * is compared against the user's file. Any key that exists in the bundled
 * version but is missing on disk is added with its default value, comments and
 * existing values are preserved as much as YamlConfiguration allows, and the
 * {@code config-version} field is bumped to match the new schema.</p>
 *
 * <p>This makes the plugin self-updating: when a new release introduces a new
 * config option, the user simply restarts and finds the new option already
 * present in their file with sensible defaults.</p>
 */
public final class ConfigMigrator {

    public static final String VERSION_KEY = "config-version";

    private ConfigMigrator() {}

    /**
     * Compares the running config against the bundled defaults and persists any
     * additions. Returns {@code true} if anything was changed.
     */
    public static boolean migrate(PurpleMusic plugin) {
        FileConfiguration current = plugin.getConfig();
        YamlConfiguration bundled = loadBundledDefaults(plugin);
        if (bundled == null) return false;

        int currentVersion = current.getInt(VERSION_KEY, 0);
        int bundledVersion = bundled.getInt(VERSION_KEY, 0);

        boolean changed = copyMissing(bundled, current, "");

        if (currentVersion != bundledVersion) {
            current.set(VERSION_KEY, bundledVersion);
            changed = true;
            plugin.getLogger().info("Migrated config.yml from version "
                    + currentVersion + " to " + bundledVersion + ".");
        }

        if (changed) {
            plugin.saveConfig();
        }
        return changed;
    }

    /** Reads the {@code config.yml} embedded in the plugin jar. */
    private static YamlConfiguration loadBundledDefaults(PurpleMusic plugin) {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return null;
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read bundled config.yml: " + e.getMessage());
            return null;
        }
    }

    /**
     * Recursively copies every key present in {@code source} but missing in
     * {@code target}. Existing values are never overwritten.
     */
    private static boolean copyMissing(ConfigurationSection source,
                                       ConfigurationSection target, String prefix) {
        boolean changed = false;
        for (String key : source.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = source.get(key);
            if (value instanceof ConfigurationSection sub) {
                if (!target.isConfigurationSection(key)) {
                    target.createSection(key);
                    changed = true;
                }
                ConfigurationSection targetSub = target.getConfigurationSection(key);
                if (targetSub != null) {
                    changed |= copyMissing(sub, targetSub, path);
                }
            } else if (!target.contains(key)) {
                target.set(key, value);
                changed = true;
            }
        }
        return changed;
    }
}
