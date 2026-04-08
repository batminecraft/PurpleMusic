package fr.batmultifonction.purplemusic.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.*;
import dev.jorel.commandapi.executors.CommandExecutor;
import fr.batmultifonction.purplemusic.PurpleMusic;
import fr.batmultifonction.purplemusic.library.Playlist;
import fr.batmultifonction.purplemusic.util.DownloadService;
import fr.batmultifonction.purplemusic.util.Messages;
import fr.batmultifonction.purplemusic.zone.MusicZone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Root /purplemusic command. Aliased as /pmusic and /pm.
 *
 * Subcommands:
 *   download <url> <filename>
 *   list                        - list available tracks
 *   play <player> <track>
 *   playall <track>
 *   playlist play <player> <name>
 *   playlist playall <name>
 *   playlist create <name>
 *   playlist delete <name>
 *   playlist add <name> <track>
 *   playlist remove <name> <track>
 *   playlist mode <name> <SEQUENTIAL|LOOP|LOOP_ONE|SHUFFLE>
 *   playlist list
 *   playlist show <name>
 *   queue add <player|all> <track>
 *   queue clear <player|all>
 *   queue list <player|all>
 *   queue skip <player|all>
 *   stop <player|all>
 *   zone create <name> <track|playlist> <source> <radius> <continuous|interval> [seconds] [volume]
 *   zone delete <name>
 *   zone list
 *   zone mode <name> <SEQUENTIAL|LOOP|LOOP_ONE|SHUFFLE>
 *   zone volume <name> <0.0..1.0>
 *   reload
 */
public class PurpleMusicCommand extends CommandAPICommand {

    private final PurpleMusic plugin;
    private final DownloadService downloadService;

    public PurpleMusicCommand(PurpleMusic plugin) {
        super("purplemusic");
        this.plugin = plugin;
        this.downloadService = new DownloadService(plugin);

        withAliases("pmusic", "pm");
        withFullDescription("PurpleMusic root command.");
        withPermission(CommandPermission.NONE);

        executes((CommandExecutor) (sender, args) -> sendHelp(sender));

        withSubcommand(downloadCommand());
        withSubcommand(listCommand());
        withSubcommand(playCommand());
        withSubcommand(playAllCommand());
        withSubcommand(stopCommand());
        withSubcommand(reloadCommand());
        withSubcommand(playlistCommand());
        withSubcommand(queueCommand());
        withSubcommand(zoneCommand());
    }

    // ----- helpers -----

    private Messages m() { return plugin.messages(); }

    private int sendHelp(CommandSender s) {
        m().send(s, "help-header");
        String[] lines = new String[] {
                "/pm download <url> <filename>",
                "/pm list",
                "/pm play <player> <track>",
                "/pm playall <track>",
                "/pm stop <player|all>",
                "/pm queue add|clear|list|skip <player|all> [track]",
                "/pm playlist play|playall|create|delete|add|remove|mode|list|show ...",
                "/pm zone create|delete|list|mode|volume ...",
                "/pm reload",
        };
        for (String l : lines) m().send(s, "help-line", "command", l);
        return 1;
    }

    private String[] trackSuggestions() {
        return plugin.library().listTracks().toArray(new String[0]);
    }

    private String[] playlistSuggestions() {
        return plugin.playlists().names().toArray(new String[0]);
    }

    private String[] onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toArray(String[]::new);
    }

    private String[] zoneSuggestions() {
        List<String> out = new ArrayList<>();
        for (MusicZone z : plugin.zones().all()) out.add(z.name());
        return out.toArray(new String[0]);
    }

    // ----- download -----

    private CommandAPICommand downloadCommand() {
        return new CommandAPICommand("download")
                .withPermission("purplemusic.download")
                .withArguments(new GreedyStringArgument("url-and-filename"))
                .executes((sender, args) -> {
                    String input = (String) args.get(0);
                    int sp = input.indexOf(' ');
                    if (sp <= 0) { m().send(sender, "download-usage"); return; }
                    String url = input.substring(0, sp).trim();
                    String filename = input.substring(sp + 1).trim();
                    m().send(sender, "downloading");
                    Bukkit.getAsyncScheduler().runNow(plugin, t -> {
                        try {
                            Path file = downloadService.download(url, filename);
                            String rel = plugin.library().root().relativize(file).toString().replace('\\', '/');
                            m().send(sender, "download-saved", "file", rel);
                        } catch (DownloadService.DownloadException e) {
                            m().send(sender, "download-failed", "error", e.getMessage());
                        }
                    });
                });
    }

    // ----- list -----

    private CommandAPICommand listCommand() {
        return new CommandAPICommand("list")
                .withPermission("purplemusic.list")
                .executes((sender, args) -> {
                    List<String> tracks = plugin.library().listTracks();
                    if (tracks.isEmpty()) { m().send(sender, "library-empty"); return; }
                    m().send(sender, "library-count", "count", tracks.size());
                    for (String t : tracks) m().send(sender, "library-entry", "track", t);
                });
    }

    // ----- play / playall / stop -----

    private CommandAPICommand playCommand() {
        return new CommandAPICommand("play")
                .withPermission("purplemusic.play")
                .withArguments(new StringArgument("player").replaceSuggestions(ArgumentSuggestions.strings(info -> onlinePlayerNames())))
                .withArguments(new TextArgument("track").replaceSuggestions(ArgumentSuggestions.strings(info -> trackSuggestions())))
                .executes((sender, args) -> {
                    String name = (String) args.get("player");
                    Player p = Bukkit.getPlayerExact(name);
                    if (p == null) { m().send(sender, "player-not-online", "player", name); return; }
                    String track = (String) args.get("track");
                    if (plugin.playback().playTrackToPlayer(p, track)) {
                        m().send(sender, "playing-to-player", "track", track, "player", p.getName());
                    } else {
                        m().send(sender, "track-not-found", "track", track);
                    }
                });
    }

    private CommandAPICommand playAllCommand() {
        return new CommandAPICommand("playall")
                .withPermission("purplemusic.play")
                .withArguments(new TextArgument("track").replaceSuggestions(ArgumentSuggestions.strings(info -> trackSuggestions())))
                .executes((sender, args) -> {
                    String track = (String) args.get("track");
                    if (plugin.playback().playTrackToAll(track)) {
                        m().send(sender, "playing-to-all", "track", track);
                    } else {
                        m().send(sender, "track-not-found", "track", track);
                    }
                });
    }

    private CommandAPICommand stopCommand() {
        return new CommandAPICommand("stop")
                .withPermission("purplemusic.stop")
                .withArguments(new TextArgument("target").replaceSuggestions(ArgumentSuggestions.strings("all")))
                .executes((sender, args) -> {
                    String target = (String) args.get("target");
                    if (target.equalsIgnoreCase("all") || target.equals("*")) {
                        plugin.playback().stopAll();
                        m().send(sender, "stopped-all");
                        return;
                    }
                    Player p = Bukkit.getPlayerExact(target);
                    if (p == null) { m().send(sender, "player-not-online", "player", target); return; }
                    plugin.playback().stop(p);
                    m().send(sender, "stopped-player", "player", p.getName());
                });
    }

    private CommandAPICommand reloadCommand() {
        return new CommandAPICommand("reload")
                .withPermission("purplemusic.reload")
                .executes((sender, args) -> {
                    plugin.reloadPlugin();
                    m().send(sender, "reload-done");
                });
    }

    // ----- playlist -----

    private CommandAPICommand playlistCommand() {
        CommandAPICommand root = new CommandAPICommand("playlist")
                .withPermission("purplemusic.playlist");

        root.withSubcommand(new CommandAPICommand("create")
                .withArguments(new StringArgument("name"))
                .executes((sender, args) -> {
                    String name = (String) args.get("name");
                    if (plugin.playlists().get(name) != null) { m().send(sender, "playlist-exists"); return; }
                    plugin.playlists().create(name);
                    m().send(sender, "playlist-created", "name", name);
                }));

        root.withSubcommand(new CommandAPICommand("delete")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .executes((sender, args) -> {
                    String name = (String) args.get("name");
                    if (plugin.playlists().delete(name)) m().send(sender, "playlist-deleted", "name", name);
                    else m().send(sender, "playlist-not-found", "name", name);
                }));

        root.withSubcommand(new CommandAPICommand("add")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .withArguments(new TextArgument("track").replaceSuggestions(ArgumentSuggestions.strings(info -> trackSuggestions())))
                .executes((sender, args) -> {
                    String plName = (String) args.get("name");
                    Playlist pl = plugin.playlists().get(plName);
                    if (pl == null) { m().send(sender, "playlist-not-found", "name", plName); return; }
                    String track = (String) args.get("track");
                    if (plugin.library().resolve(track) == null) { m().send(sender, "track-not-found", "track", track); return; }
                    pl.add(track);
                    plugin.playlists().save();
                    m().send(sender, "playlist-added", "track", track, "name", pl.name());
                }));

        root.withSubcommand(new CommandAPICommand("remove")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .withArguments(new TextArgument("track"))
                .executes((sender, args) -> {
                    String plName = (String) args.get("name");
                    Playlist pl = plugin.playlists().get(plName);
                    if (pl == null) { m().send(sender, "playlist-not-found", "name", plName); return; }
                    String track = (String) args.get("track");
                    if (pl.remove(track)) {
                        plugin.playlists().save();
                        m().send(sender, "playlist-removed", "track", track, "name", pl.name());
                    } else {
                        m().send(sender, "playlist-track-missing");
                    }
                }));

        root.withSubcommand(new CommandAPICommand("mode")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .withArguments(new MultiLiteralArgument("mode", "SEQUENTIAL", "LOOP", "LOOP_ONE", "SHUFFLE"))
                .executes((sender, args) -> {
                    String plName = (String) args.get("name");
                    Playlist pl = plugin.playlists().get(plName);
                    if (pl == null) { m().send(sender, "playlist-not-found", "name", plName); return; }
                    pl.setMode(Playlist.Mode.parse((String) args.get("mode")));
                    plugin.playlists().save();
                    m().send(sender, "playlist-mode-set", "name", pl.name(), "mode", pl.mode());
                }));

        root.withSubcommand(new CommandAPICommand("list")
                .executes((sender, args) -> {
                    Collection<Playlist> all = plugin.playlists().all();
                    if (all.isEmpty()) { m().send(sender, "playlist-list-empty"); return; }
                    m().send(sender, "playlist-list-header", "count", all.size());
                    for (Playlist pl : all) {
                        m().send(sender, "playlist-list-entry",
                                "name", pl.name(),
                                "mode", pl.mode(),
                                "size", pl.size());
                    }
                }));

        root.withSubcommand(new CommandAPICommand("show")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .executes((sender, args) -> {
                    String plName = (String) args.get("name");
                    Playlist pl = plugin.playlists().get(plName);
                    if (pl == null) { m().send(sender, "playlist-not-found", "name", plName); return; }
                    m().send(sender, "playlist-show-header", "name", pl.name(), "mode", pl.mode());
                    int i = 1;
                    for (String t : pl.tracks()) {
                        m().send(sender, "playlist-show-entry", "index", i++, "track", t);
                    }
                }));

        root.withSubcommand(new CommandAPICommand("play")
                .withArguments(new StringArgument("player").replaceSuggestions(ArgumentSuggestions.strings(info -> onlinePlayerNames())))
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .executes((sender, args) -> {
                    String pname = (String) args.get("player");
                    Player p = Bukkit.getPlayerExact(pname);
                    if (p == null) { m().send(sender, "player-not-online", "player", pname); return; }
                    String name = (String) args.get("name");
                    if (plugin.playback().playPlaylistToPlayer(p, name)) {
                        m().send(sender, "playlist-playing-player", "name", name, "player", p.getName());
                    } else {
                        m().send(sender, "playlist-empty");
                    }
                }));

        root.withSubcommand(new CommandAPICommand("playall")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .executes((sender, args) -> {
                    String name = (String) args.get("name");
                    if (plugin.playback().playPlaylistToAll(name)) {
                        m().send(sender, "playlist-playing-all", "name", name);
                    } else {
                        m().send(sender, "playlist-empty");
                    }
                }));

        return root;
    }

    // ----- queue -----

    private CommandAPICommand queueCommand() {
        CommandAPICommand root = new CommandAPICommand("queue")
                .withPermission("purplemusic.queue");

        root.withSubcommand(new CommandAPICommand("add")
                .withArguments(new TextArgument("target").replaceSuggestions(ArgumentSuggestions.strings("all")))
                .withArguments(new TextArgument("track").replaceSuggestions(ArgumentSuggestions.strings(info -> trackSuggestions())))
                .executes((sender, args) -> {
                    String target = (String) args.get("target");
                    String track = (String) args.get("track");
                    if (target.equalsIgnoreCase("all")) {
                        if (plugin.playback().enqueueForAll(track)) {
                            m().send(sender, "queue-added-all", "track", track);
                        } else {
                            m().send(sender, "track-not-found", "track", track);
                        }
                        return;
                    }
                    Player p = Bukkit.getPlayerExact(target);
                    if (p == null) { m().send(sender, "player-not-online", "player", target); return; }
                    if (plugin.playback().enqueueForPlayer(p, track)) {
                        m().send(sender, "queue-added-player", "track", track, "player", p.getName());
                    } else {
                        m().send(sender, "track-not-found", "track", track);
                    }
                }));

        root.withSubcommand(new CommandAPICommand("clear")
                .withArguments(new TextArgument("target").replaceSuggestions(ArgumentSuggestions.strings("all")))
                .executes((sender, args) -> {
                    String target = (String) args.get("target");
                    if (target.equalsIgnoreCase("all")) {
                        plugin.playback().clearGlobalQueue();
                        m().send(sender, "queue-cleared-all");
                        return;
                    }
                    Player p = Bukkit.getPlayerExact(target);
                    if (p == null) { m().send(sender, "player-not-online", "player", target); return; }
                    plugin.playback().clearQueue(p);
                    m().send(sender, "queue-cleared-player", "player", p.getName());
                }));

        root.withSubcommand(new CommandAPICommand("list")
                .withArguments(new TextArgument("target").replaceSuggestions(ArgumentSuggestions.strings("all")))
                .executes((sender, args) -> {
                    String target = (String) args.get("target");
                    List<String> q;
                    String label;
                    if (target.equalsIgnoreCase("all")) {
                        q = plugin.playback().queueOfAll();
                        label = "global";
                    } else {
                        Player p = Bukkit.getPlayerExact(target);
                        if (p == null) { m().send(sender, "player-not-online", "player", target); return; }
                        q = plugin.playback().queueOf(p);
                        label = p.getName();
                    }
                    if (q.isEmpty()) { m().send(sender, "queue-empty", "target", label); return; }
                    m().send(sender, "queue-header", "target", label);
                    int i = 1;
                    for (String t : q) {
                        m().send(sender, "queue-entry", "index", i++, "track", t);
                    }
                }));

        root.withSubcommand(new CommandAPICommand("skip")
                .withArguments(new TextArgument("target").replaceSuggestions(ArgumentSuggestions.strings("all")))
                .executes((sender, args) -> {
                    String target = (String) args.get("target");
                    if (target.equalsIgnoreCase("all")) {
                        plugin.playback().skipGlobal();
                        m().send(sender, "queue-skipped-all");
                        return;
                    }
                    Player p = Bukkit.getPlayerExact(target);
                    if (p == null) { m().send(sender, "player-not-online", "player", target); return; }
                    plugin.playback().skip(p);
                    m().send(sender, "queue-skipped-player", "player", p.getName());
                }));

        return root;
    }

    // ----- zone -----

    private CommandAPICommand zoneCommand() {
        CommandAPICommand root = new CommandAPICommand("zone")
                .withPermission("purplemusic.zone");

        // /pm zone create <name> <track|playlist> <source> <radius>
        //                 <continuous|interval> [interval-seconds] [volume]
        root.withSubcommand(new CommandAPICommand("create")
                .withArguments(new StringArgument("name"))
                .withArguments(new MultiLiteralArgument("type", "track", "playlist"))
                .withArguments(new TextArgument("source").replaceSuggestions(ArgumentSuggestions.strings(info -> {
                    Object t = info.previousArgs().get("type");
                    if ("playlist".equalsIgnoreCase(String.valueOf(t))) return playlistSuggestions();
                    return trackSuggestions();
                })))
                .withArguments(new FloatArgument("radius", 1f, 1024f))
                .withArguments(new MultiLiteralArgument("schedule", "continuous", "interval"))
                .withOptionalArguments(new LongArgument("interval-seconds", 1L))
                .withOptionalArguments(new FloatArgument("volume", 0f, 1f))
                .executesPlayer((player, args) -> {
                    String name = (String) args.get("name");
                    if (plugin.zones().exists(name)) { m().send(player, "zone-exists"); return; }
                    String type = (String) args.get("type");
                    String source = (String) args.get("source");
                    float radius = (float) args.get("radius");
                    String schedule = (String) args.get("schedule");

                    MusicZone.SourceType st;
                    if ("playlist".equalsIgnoreCase(type)) {
                        if (plugin.playlists().get(source) == null) {
                            m().send(player, "playlist-not-found", "name", source);
                            return;
                        }
                        st = MusicZone.SourceType.PLAYLIST;
                    } else {
                        if (plugin.library().resolve(source) == null) {
                            m().send(player, "track-not-found", "track", source);
                            return;
                        }
                        st = MusicZone.SourceType.TRACK;
                    }

                    MusicZone.Schedule sch = "interval".equalsIgnoreCase(schedule)
                            ? MusicZone.Schedule.INTERVAL : MusicZone.Schedule.CONTINUOUS;
                    long intervalSec = 0L;
                    if (sch == MusicZone.Schedule.INTERVAL) {
                        Long given = (Long) args.get("interval-seconds");
                        if (given == null || given <= 0) {
                            m().send(player, "zone-need-interval");
                            return;
                        }
                        intervalSec = given;
                    }

                    Float volArg = (Float) args.get("volume");
                    float volume = volArg == null ? 1.0f : volArg;

                    Location loc = player.getLocation();
                    Playlist.Mode mode = (st == MusicZone.SourceType.PLAYLIST)
                            ? plugin.playlists().get(source).mode() : Playlist.Mode.SEQUENTIAL;
                    MusicZone zone = new MusicZone(name, loc.getWorld().getUID(),
                            loc.getX(), loc.getY(), loc.getZ(), radius, st, source, mode,
                            sch, intervalSec, volume);
                    plugin.zones().create(zone);

                    String scheduleDesc = sch == MusicZone.Schedule.INTERVAL
                            ? sch + " " + intervalSec + "s" : sch.name();
                    m().send(player, "zone-created",
                            "name", name, "schedule", scheduleDesc);
                }));

        root.withSubcommand(new CommandAPICommand("delete")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> zoneSuggestions())))
                .executes((sender, args) -> {
                    String name = (String) args.get("name");
                    if (plugin.zones().delete(name)) {
                        m().send(sender, "zone-deleted", "name", name);
                    } else {
                        m().send(sender, "zone-not-found", "name", name);
                    }
                }));

        root.withSubcommand(new CommandAPICommand("list")
                .executes((sender, args) -> {
                    Collection<MusicZone> all = plugin.zones().all();
                    if (all.isEmpty()) { m().send(sender, "zone-list-empty"); return; }
                    m().send(sender, "zone-list-header", "count", all.size());
                    for (MusicZone z : all) {
                        String w = Bukkit.getWorld(z.worldId()) != null
                                ? Bukkit.getWorld(z.worldId()).getName() : z.worldId().toString();
                        String scheduleDesc = z.schedule() == MusicZone.Schedule.INTERVAL
                                ? z.schedule() + " " + z.intervalSeconds() + "s" : z.schedule().name();
                        m().send(sender, "zone-list-entry",
                                "name", z.name(),
                                "world", w,
                                "x", (int) z.x(),
                                "y", (int) z.y(),
                                "z", (int) z.z(),
                                "radius", (int) z.radius(),
                                "volume", String.format("%.2f", z.volume()),
                                "type", z.sourceType(),
                                "source", z.source(),
                                "schedule", scheduleDesc);
                    }
                }));

        root.withSubcommand(new CommandAPICommand("mode")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> zoneSuggestions())))
                .withArguments(new MultiLiteralArgument("mode", "SEQUENTIAL", "LOOP", "LOOP_ONE", "SHUFFLE"))
                .executes((sender, args) -> {
                    String zName = (String) args.get("name");
                    MusicZone z = plugin.zones().get(zName);
                    if (z == null) { m().send(sender, "zone-not-found", "name", zName); return; }
                    if (z.sourceType() != MusicZone.SourceType.PLAYLIST) {
                        m().send(sender, "zone-mode-only-playlist");
                        return;
                    }
                    z.setPlaylistMode(Playlist.Mode.parse((String) args.get("mode")));
                    plugin.zones().save();
                    m().send(sender, "zone-mode-set", "name", z.name(), "mode", z.playlistMode());
                }));

        root.withSubcommand(new CommandAPICommand("volume")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> zoneSuggestions())))
                .withArguments(new FloatArgument("volume", 0f, 1f))
                .executes((sender, args) -> {
                    String zName = (String) args.get("name");
                    MusicZone z = plugin.zones().get(zName);
                    if (z == null) { m().send(sender, "zone-not-found", "name", zName); return; }
                    float v = (float) args.get("volume");
                    if (Float.isNaN(v) || v < 0f || v > 1f) {
                        m().send(sender, "zone-volume-invalid");
                        return;
                    }
                    z.setVolume(v);
                    plugin.zones().save();
                    m().send(sender, "zone-volume-set",
                            "name", z.name(), "volume", String.format("%.2f", z.volume()));
                }));

        return root;
    }
}
