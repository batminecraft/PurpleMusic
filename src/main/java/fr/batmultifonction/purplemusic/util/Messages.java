package fr.batmultifonction.purplemusic.util;

import fr.batmultifonction.purplemusic.PurpleMusic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads localized messages from plugins/PurpleMusic/lang/&lt;code&gt;.yml.
 *
 * <p>On startup any bundled default language file (en.yml, fr.yml) is copied
 * into the lang folder if missing, then the file matching {@code language:} in
 * config.yml is loaded. Server operators can freely drop new language files
 * into the folder.</p>
 *
 * <p>Messages support legacy &amp;-color codes, a {@code %prefix%} placeholder
 * and arbitrary {@code {key}} placeholders filled in at send-time.</p>
 */
public class Messages {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> BUNDLED = List.of("en", "fr");

    private final PurpleMusic plugin;
    private final File langFolder;
    private final Map<String, String> values = new HashMap<>();
    private String prefix = "";
    private String language = "en";

    public Messages(PurpleMusic plugin) {
        this.plugin = plugin;
        this.langFolder = new File(plugin.getDataFolder(), "lang");
    }

    /**
     * Ensures bundled files exist on disk, then loads the language declared in config.yml.
     */
    public void load(String configuredLanguage) {
        if (!langFolder.exists()) langFolder.mkdirs();
        copyBundledIfMissing();

        String lang = (configuredLanguage == null || configuredLanguage.isBlank())
                ? "en" : configuredLanguage.toLowerCase();

        File target = new File(langFolder, lang + ".yml");
        if (!target.exists()) {
            plugin.getLogger().warning(
                    "Language file '" + lang + ".yml' not found, falling back to en.yml");
            lang = "en";
            target = new File(langFolder, "en.yml");
        }
        if (!target.exists()) {
            plugin.getLogger().severe("Default language file missing, messages will be empty.");
            this.language = lang;
            return;
        }

        values.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(target);
        for (String key : cfg.getKeys(false)) {
            Object v = cfg.get(key);
            if (v != null) values.put(key, String.valueOf(v));
        }
        this.prefix = values.getOrDefault("prefix", "");
        this.language = lang;
        plugin.getLogger().info("Loaded language '" + lang + "' (" + values.size() + " keys).");
    }

    private void copyBundledIfMissing() {
        for (String code : BUNDLED) {
            File out = new File(langFolder, code + ".yml");
            if (out.exists()) continue;
            try (InputStream in = plugin.getResource("lang/" + code + ".yml")) {
                if (in == null) continue;
                Files.copy(in, out.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to copy lang/" + code + ".yml: " + e.getMessage());
            }
        }
    }

    public String language() { return language; }

    /** Returns the raw legacy-formatted string for a key, with {@code %prefix%} expanded. */
    public String raw(String key) {
        String v = values.get(key);
        if (v == null) return "<missing:" + key + ">";
        return v.replace("%prefix%", prefix);
    }

    /**
     * Formats a message and sends it to the command sender, replacing {key}
     * placeholders with the provided pairs (key, value, key, value, ...).
     */
    public void send(CommandSender sender, String key, Object... pairs) {
        sender.sendMessage(component(key, pairs));
    }

    public Component component(String key, Object... pairs) {
        String raw = raw(key);
        if (pairs != null && pairs.length > 0) {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                raw = raw.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
            }
        }
        return LEGACY.deserialize(raw);
    }
}
