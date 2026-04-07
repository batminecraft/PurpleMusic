package fr.batmultifonction.purplemusic.audio;

import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import fr.batmultifonction.purplemusic.PurpleMusic;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Low-level audio playback. Streams a decoded file to a SVC channel and returns
 * a {@link Handle} that can be used to stop or attach completion callbacks.
 */
public class AudioEngine {

    private final PurpleMusic plugin;
    private final ExecutorService io;

    public AudioEngine(PurpleMusic plugin) {
        this.plugin = plugin;
        this.io = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "PurpleMusic-Audio");
            t.setDaemon(true);
            return t;
        });
    }

    public void shutdown() {
        io.shutdownNow();
    }

    /** Plays a track to a single player as a static (always-audible) channel attached to that player. */
    public Handle playToPlayer(Player player, Path file) {
        VoicechatServerApi api = VoicePlugin.voicechatServerApi;
        if (api == null) return Handle.failed();
        ServerPlayer sp = api.fromServerPlayer(player);
        VoicechatConnection conn = api.getConnectionOf(sp);
        if (conn == null) return Handle.failed();
        UUID id = UUID.randomUUID();
        StaticAudioChannel channel = api.createStaticAudioChannel(id, api.fromServerLevel(player.getWorld()), conn);
        if (channel == null) return Handle.failed();
        channel.setCategory(VoicePlugin.CATEGORY_ID);
        return startStream(api, channel, file);
    }

    /** Plays a track to many players (one channel per player). */
    public Handle playToPlayers(Collection<Player> players, Path file) {
        Handle group = new Handle();
        for (Player p : players) {
            Handle h = playToPlayer(p, file);
            group.attachChild(h);
        }
        return group;
    }

    /** Plays a track at a fixed location (locational channel) heard by everyone in range. */
    public Handle playLocational(Location location, float range, Path file) {
        VoicechatServerApi api = VoicePlugin.voicechatServerApi;
        if (api == null) return Handle.failed();
        ServerLevel level = api.fromServerLevel(location.getWorld());
        UUID id = UUID.randomUUID();
        LocationalAudioChannel channel = api.createLocationalAudioChannel(id, level,
                api.createPosition(location.getX(), location.getY(), location.getZ()));
        if (channel == null) return Handle.failed();
        channel.setCategory(VoicePlugin.CATEGORY_ID);
        channel.setDistance(range);
        return startStream(api, channel, file);
    }

    private Handle startStream(VoicechatServerApi api, AudioChannel channel, Path file) {
        Handle handle = new Handle();
        io.execute(() -> {
            AudioInputStream stream;
            try {
                stream = AudioDecoder.open(file);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to open audio file: " + file, e);
                handle.markStopped();
                return;
            }
            AudioPlayer player = api.createAudioPlayer(channel, api.createEncoder(), () -> {
                try {
                    return readFrame(stream);
                } catch (Exception e) {
                    if (plugin.isDebugMode()) plugin.getLogger().log(Level.SEVERE, "Decode error", e);
                    return null;
                }
            });
            player.setOnStopped(() -> {
                try { stream.close(); } catch (IOException ignored) {}
                handle.markStopped();
            });
            handle.bind(player);
            player.startPlaying();
        });
        return handle;
    }

    private short[] readFrame(AudioInputStream stream) throws IOException {
        final int FRAME_BYTES = 1920; // 20 ms @ 48 kHz mono 16-bit
        byte[] buf = new byte[FRAME_BYTES];
        int total = 0;
        while (total < FRAME_BYTES) {
            int r = stream.read(buf, total, FRAME_BYTES - total);
            if (r < 0) break;
            total += r;
        }
        if (total <= 0) return null;
        for (int i = total; i < FRAME_BYTES; i++) buf[i] = 0;
        // volume scale
        float vol = plugin.musicVolume();
        if (vol < 1f) {
            for (int i = 0; i < FRAME_BYTES; i += 2) {
                short s = (short) ((buf[i] & 0xff) | (buf[i + 1] << 8));
                s = (short) (s * vol);
                buf[i] = (byte) s;
                buf[i + 1] = (byte) (s >> 8);
            }
        }
        return VoicePlugin.voicechatApi.getAudioConverter().bytesToShorts(buf);
    }

    /**
     * Handle returned by playback methods. Supports stop, completion callbacks, and
     * grouping multiple sub-handles together.
     */
    public static class Handle {
        private volatile AudioPlayer player;
        private volatile boolean stopped;
        private volatile boolean stopRequested;
        private Runnable onComplete;
        private final java.util.List<Handle> children = new java.util.ArrayList<>();

        public static Handle failed() {
            Handle h = new Handle();
            h.stopped = true;
            return h;
        }

        synchronized void bind(AudioPlayer p) {
            this.player = p;
            if (stopRequested) p.stopPlaying();
        }

        synchronized void markStopped() {
            stopped = true;
            if (onComplete != null) {
                Runnable cb = onComplete;
                onComplete = null;
                cb.run();
            }
        }

        public synchronized void attachChild(Handle child) {
            children.add(child);
        }

        public synchronized void stop() {
            stopRequested = true;
            if (player != null) player.stopPlaying();
            for (Handle c : children) c.stop();
        }

        public boolean isStopped() {
            if (children.isEmpty()) return stopped;
            for (Handle c : children) if (!c.isStopped()) return false;
            return true;
        }

        public synchronized void onComplete(Runnable r) {
            if (children.isEmpty()) {
                if (stopped) r.run();
                else this.onComplete = r;
            } else {
                // wait for all children
                int[] remaining = { children.size() };
                Runnable bump = () -> {
                    synchronized (remaining) {
                        remaining[0]--;
                        if (remaining[0] == 0) r.run();
                    }
                };
                for (Handle c : children) c.onComplete(bump);
            }
        }
    }
}
