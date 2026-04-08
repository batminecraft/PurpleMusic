package fr.batmultifonction.purplemusic;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import fr.batmultifonction.purplemusic.api.PurpleMusicAPI;
import fr.batmultifonction.purplemusic.api.PurpleMusicAPIProvider;
import fr.batmultifonction.purplemusic.api.impl.PurpleMusicAPIImpl;
import fr.batmultifonction.purplemusic.audio.AudioEngine;
import fr.batmultifonction.purplemusic.audio.SfxManager;
import fr.batmultifonction.purplemusic.audio.VoicePlugin;
import fr.batmultifonction.purplemusic.command.PurpleMusicCommand;
import fr.batmultifonction.purplemusic.library.MusicLibrary;
import fr.batmultifonction.purplemusic.library.PlaylistManager;
import fr.batmultifonction.purplemusic.playback.PlaybackService;
import fr.batmultifonction.purplemusic.util.ConfigMigrator;
import fr.batmultifonction.purplemusic.util.Messages;
import fr.batmultifonction.purplemusic.zone.ZoneManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Logger;

public final class PurpleMusic extends JavaPlugin {

    private static PurpleMusic instance;

    private VoicePlugin voicePlugin;
    private AudioEngine audioEngine;
    private MusicLibrary musicLibrary;
    private PlaylistManager playlistManager;
    private PlaybackService playbackService;
    private ZoneManager zoneManager;
    private Messages messages;
    private SfxManager sfxManager;
    private PurpleMusicAPI api;

    private boolean debugMode;
    private float musicVolume;
    private int filenameMaxLength;
    private int maxDownloadSize;
    private String subdirectoryDepth;
    private int defaultZoneRadius;
    private String language;

    @Override
    public void onLoad() {
        instance = this;
        CommandAPI.onLoad(new CommandAPIPaperConfig(this).verboseOutput(false).fallbackToLatestNMS(true));
    }

    @Override
    public void onEnable() {
        Logger log = getLogger();
        CommandAPI.onEnable();

        saveDefaultConfig();
        ConfigMigrator.migrate(this);
        loadConfigValues();

        File musicDir = new File(getDataFolder(), "musicdata");
        if (!musicDir.exists()) musicDir.mkdirs();

        this.messages = new Messages(this);
        this.messages.load(this.language);

        this.audioEngine = new AudioEngine(this);
        this.musicLibrary = new MusicLibrary(this);
        this.playlistManager = new PlaylistManager(this);
        this.sfxManager = new SfxManager(this, audioEngine);
        this.sfxManager.reload();
        this.playbackService = new PlaybackService(this, audioEngine);
        this.zoneManager = new ZoneManager(this, audioEngine, playlistManager);

        playlistManager.load();
        zoneManager.load();
        zoneManager.start();

        this.api = new PurpleMusicAPIImpl(this);
        PurpleMusicAPIProvider.register(this.api);

        BukkitVoicechatService svc = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (svc != null) {
            voicePlugin = new VoicePlugin();
            svc.registerPlugin(voicePlugin);
            log.info("Successfully registered with Simple Voice Chat.");
        } else {
            log.severe("Simple Voice Chat is not installed. PurpleMusic cannot play audio.");
        }

        new PurpleMusicCommand(this).register("purplemusic");

        log.info("PurpleMusic enabled.");
    }

    @Override
    public void onDisable() {
        PurpleMusicAPIProvider.unregister();
        if (zoneManager != null) zoneManager.stop();
        if (playbackService != null) playbackService.stopAll();
        if (audioEngine != null) audioEngine.shutdown();
        CommandAPI.onDisable();
        if (voicePlugin != null) {
            getServer().getServicesManager().unregister(voicePlugin);
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        ConfigMigrator.migrate(this);
        loadConfigValues();
        if (messages != null) messages.load(this.language);
        if (sfxManager != null) sfxManager.reload();
        playlistManager.load();
        zoneManager.reload();
    }

    private void loadConfigValues() {
        debugMode = getConfig().getBoolean("debug-mode", false);
        maxDownloadSize = getConfig().getInt("max-download-size", 50);
        filenameMaxLength = getConfig().getInt("filename-maximum-length", 100);
        musicVolume = (float) getConfig().getDouble("music-volume", 1.0d);
        if (musicVolume < 0f) musicVolume = 0f;
        if (musicVolume > 1f) musicVolume = 1f;
        subdirectoryDepth = getConfig().getString("subdirectory-depth", "unrestricted");
        defaultZoneRadius = getConfig().getInt("default-zone-radius", 24);
        language = getConfig().getString("language", "en");
    }

    public static PurpleMusic getInstance() { return instance; }

    public AudioEngine audioEngine() { return audioEngine; }
    public MusicLibrary library() { return musicLibrary; }
    public PlaylistManager playlists() { return playlistManager; }
    public PlaybackService playback() { return playbackService; }
    public ZoneManager zones() { return zoneManager; }
    public Messages messages() { return messages; }
    public SfxManager sfx() { return sfxManager; }
    public PurpleMusicAPI api() { return api; }

    public boolean isDebugMode() { return debugMode; }
    public float musicVolume() { return musicVolume; }
    public int filenameMaxLength() { return filenameMaxLength; }
    public int maxDownloadSize() { return maxDownloadSize; }
    public String subdirectoryDepth() { return subdirectoryDepth; }
    public int defaultZoneRadius() { return defaultZoneRadius; }
}
