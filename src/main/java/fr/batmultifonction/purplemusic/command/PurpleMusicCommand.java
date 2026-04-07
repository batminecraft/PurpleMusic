package fr.batmultifonction.purplemusic.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.*;
import dev.jorel.commandapi.executors.CommandExecutor;
import fr.batmultifonction.purplemusic.PurpleMusic;
import fr.batmultifonction.purplemusic.library.Playlist;
import fr.batmultifonction.purplemusic.util.DownloadService;
import fr.batmultifonction.purplemusic.zone.MusicZone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 *   zone create <name> <track|playlist> <source> <radius> <continuous|interval> [seconds]
 *   zone delete <name>
 *   zone list
 *   zone mode <name> <SEQUENTIAL|LOOP|LOOP_ONE|SHUFFLE>
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

    private void msg(CommandSender s, String text) {
        s.sendMessage(Component.text("[PurpleMusic] ", NamedTextColor.LIGHT_PURPLE).append(Component.text(text, NamedTextColor.GRAY)));
    }

    private void err(CommandSender s, String text) {
        s.sendMessage(Component.text("[PurpleMusic] ", NamedTextColor.LIGHT_PURPLE).append(Component.text(text, NamedTextColor.RED)));
    }

    private int sendHelp(CommandSender s) {
        s.sendMessage(Component.text("PurpleMusic commands:", NamedTextColor.LIGHT_PURPLE));
        String[] lines = new String[] {
                "/pm download <url> <filename>",
                "/pm list",
                "/pm play <player> <track>",
                "/pm playall <track>",
                "/pm stop <player|all>",
                "/pm queue add|clear|list|skip <player|all> [track]",
                "/pm playlist play|playall|create|delete|add|remove|mode|list|show ...",
                "/pm zone create|delete|list|mode ...",
                "/pm reload",
        };
        for (String l : lines) s.sendMessage(Component.text("  " + l, NamedTextColor.GRAY));
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

    // ----- download -----

    private CommandAPICommand downloadCommand() {
        return new CommandAPICommand("download")
                .withPermission("purplemusic.download")
                .withArguments(new GreedyStringArgument("url-and-filename"))
                .executes((sender, args) -> {
                    String input = (String) args.get(0);
                    int sp = input.indexOf(' ');
                    if (sp <= 0) { err(sender, "Usage: /pm download <url> <filename>"); return; }
                    String url = input.substring(0, sp).trim();
                    String filename = input.substring(sp + 1).trim();
                    msg(sender, "Downloading...");
                    Bukkit.getAsyncScheduler().runNow(plugin, t -> {
                        try {
                            Path file = downloadService.download(url, filename);
                            msg(sender, "Saved as " + plugin.library().root().relativize(file).toString().replace('\\', '/'));
                        } catch (DownloadService.DownloadException e) {
                            err(sender, e.getMessage());
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
                    if (tracks.isEmpty()) { msg(sender, "No tracks in musicdata/."); return; }
                    msg(sender, tracks.size() + " track(s):");
                    for (String t : tracks) sender.sendMessage(Component.text("  - " + t, NamedTextColor.GRAY));
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
                    if (p == null) { err(sender, "Player not online: " + name); return; }
                    String track = (String) args.get("track");
                    if (plugin.playback().playTrackToPlayer(p, track)) {
                        msg(sender, "Playing '" + track + "' to " + p.getName() + ".");
                    } else {
                        err(sender, "Track not found: " + track);
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
                        msg(sender, "Playing '" + track + "' to everyone.");
                    } else {
                        err(sender, "Track not found: " + track);
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
                        msg(sender, "Stopped all playback.");
                        return;
                    }
                    Player p = Bukkit.getPlayerExact(target);
                    if (p == null) { err(sender, "Player not online: " + target); return; }
                    plugin.playback().stop(p);
                    msg(sender, "Stopped playback for " + p.getName() + ".");
                });
    }

    private CommandAPICommand reloadCommand() {
        return new CommandAPICommand("reload")
                .withPermission("purplemusic.reload")
                .executes((sender, args) -> {
                    plugin.reloadPlugin();
                    msg(sender, "Reloaded.");
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
                    if (plugin.playlists().get(name) != null) { err(sender, "Playlist already exists."); return; }
                    plugin.playlists().create(name);
                    msg(sender, "Created playlist '" + name + "'.");
                }));

        root.withSubcommand(new CommandAPICommand("delete")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .executes((sender, args) -> {
                    String name = (String) args.get("name");
                    if (plugin.playlists().delete(name)) msg(sender, "Deleted playlist '" + name + "'.");
                    else err(sender, "No such playlist.");
                }));

        root.withSubcommand(new CommandAPICommand("add")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .withArguments(new TextArgument("track").replaceSuggestions(ArgumentSuggestions.strings(info -> trackSuggestions())))
                .executes((sender, args) -> {
                    Playlist pl = plugin.playlists().get((String) args.get("name"));
                    if (pl == null) { err(sender, "No such playlist."); return; }
                    String track = (String) args.get("track");
                    if (plugin.library().resolve(track) == null) { err(sender, "Track not found: " + track); return; }
                    pl.add(track);
                    plugin.playlists().save();
                    msg(sender, "Added '" + track + "' to '" + pl.name() + "'.");
                }));

        root.withSubcommand(new CommandAPICommand("remove")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .withArguments(new TextArgument("track"))
                .executes((sender, args) -> {
                    Playlist pl = plugin.playlists().get((String) args.get("name"));
                    if (pl == null) { err(sender, "No such playlist."); return; }
                    String track = (String) args.get("track");
                    if (pl.remove(track)) {
                        plugin.playlists().save();
                        msg(sender, "Removed '" + track + "' from '" + pl.name() + "'.");
                    } else err(sender, "Track not in playlist.");
                }));

        root.withSubcommand(new CommandAPICommand("mode")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .withArguments(new MultiLiteralArgument("mode", "SEQUENTIAL", "LOOP", "LOOP_ONE", "SHUFFLE"))
                .executes((sender, args) -> {
                    Playlist pl = plugin.playlists().get((String) args.get("name"));
                    if (pl == null) { err(sender, "No such playlist."); return; }
                    pl.setMode(Playlist.Mode.parse((String) args.get("mode")));
                    plugin.playlists().save();
                    msg(sender, "Mode of '" + pl.name() + "' set to " + pl.mode() + ".");
                }));

        root.withSubcommand(new CommandAPICommand("list")
                .executes((sender, args) -> {
                    Collection<Playlist> all = plugin.playlists().all();
                    if (all.isEmpty()) { msg(sender, "No playlists."); return; }
                    msg(sender, all.size() + " playlist(s):");
                    for (Playlist pl : all) {
                        sender.sendMessage(Component.text("  - " + pl.name() + " [" + pl.mode() + ", " + pl.size() + " tracks]", NamedTextColor.GRAY));
                    }
                }));

        root.withSubcommand(new CommandAPICommand("show")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .executes((sender, args) -> {
                    Playlist pl = plugin.playlists().get((String) args.get("name"));
                    if (pl == null) { err(sender, "No such playlist."); return; }
                    msg(sender, "Playlist '" + pl.name() + "' [" + pl.mode() + "]:");
                    int i = 1;
                    for (String t : pl.tracks()) sender.sendMessage(Component.text("  " + i++ + ". " + t, NamedTextColor.GRAY));
                }));

        root.withSubcommand(new CommandAPICommand("play")
                .withArguments(new StringArgument("player").replaceSuggestions(ArgumentSuggestions.strings(info -> onlinePlayerNames())))
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .executes((sender, args) -> {
                    String pname = (String) args.get("player");
                    Player p = Bukkit.getPlayerExact(pname);
                    if (p == null) { err(sender, "Player not online: " + pname); return; }
                    String name = (String) args.get("name");
                    if (plugin.playback().playPlaylistToPlayer(p, name)) {
                        msg(sender, "Playing playlist '" + name + "' to " + p.getName() + ".");
                    } else err(sender, "No such playlist or it is empty.");
                }));

        root.withSubcommand(new CommandAPICommand("playall")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> playlistSuggestions())))
                .executes((sender, args) -> {
                    String name = (String) args.get("name");
                    if (plugin.playback().playPlaylistToAll(name)) {
                        msg(sender, "Playing playlist '" + name + "' to everyone.");
                    } else err(sender, "No such playlist or it is empty.");
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
                        if (plugin.playback().enqueueForAll(track)) msg(sender, "Queued '" + track + "' for everyone.");
                        else err(sender, "Track not found: " + track);
                        return;
                    }
                    Player p = Bukkit.getPlayerExact(target);
                    if (p == null) { err(sender, "Player not online: " + target); return; }
                    if (plugin.playback().enqueueForPlayer(p, track)) msg(sender, "Queued '" + track + "' for " + p.getName() + ".");
                    else err(sender, "Track not found: " + track);
                }));

        root.withSubcommand(new CommandAPICommand("clear")
                .withArguments(new TextArgument("target").replaceSuggestions(ArgumentSuggestions.strings("all")))
                .executes((sender, args) -> {
                    String target = (String) args.get("target");
                    if (target.equalsIgnoreCase("all")) { plugin.playback().clearGlobalQueue(); msg(sender, "Cleared global queue."); return; }
                    Player p = Bukkit.getPlayerExact(target);
                    if (p == null) { err(sender, "Player not online."); return; }
                    plugin.playback().clearQueue(p);
                    msg(sender, "Cleared queue for " + p.getName() + ".");
                }));

        root.withSubcommand(new CommandAPICommand("list")
                .withArguments(new TextArgument("target").replaceSuggestions(ArgumentSuggestions.strings("all")))
                .executes((sender, args) -> {
                    String target = (String) args.get("target");
                    List<String> q;
                    String label;
                    if (target.equalsIgnoreCase("all")) { q = plugin.playback().queueOfAll(); label = "global"; }
                    else {
                        Player p = Bukkit.getPlayerExact(target);
                        if (p == null) { err(sender, "Player not online."); return; }
                        q = plugin.playback().queueOf(p); label = p.getName();
                    }
                    if (q.isEmpty()) { msg(sender, "Queue (" + label + ") is empty."); return; }
                    msg(sender, "Queue (" + label + "):");
                    int i = 1;
                    for (String t : q) sender.sendMessage(Component.text("  " + i++ + ". " + t, NamedTextColor.GRAY));
                }));

        root.withSubcommand(new CommandAPICommand("skip")
                .withArguments(new TextArgument("target").replaceSuggestions(ArgumentSuggestions.strings("all")))
                .executes((sender, args) -> {
                    String target = (String) args.get("target");
                    if (target.equalsIgnoreCase("all")) { plugin.playback().skipGlobal(); msg(sender, "Skipped global."); return; }
                    Player p = Bukkit.getPlayerExact(target);
                    if (p == null) { err(sender, "Player not online."); return; }
                    plugin.playback().skip(p);
                    msg(sender, "Skipped current track for " + p.getName() + ".");
                }));

        return root;
    }

    // ----- zone -----

    private CommandAPICommand zoneCommand() {
        CommandAPICommand root = new CommandAPICommand("zone")
                .withPermission("purplemusic.zone");

        // /pm zone create <name> <track|playlist> <source> <radius> <continuous|interval> [seconds]
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
                .executesPlayer((player, args) -> {
                    String name = (String) args.get("name");
                    if (plugin.zones().exists(name)) { err(player, "Zone already exists."); return; }
                    String type = (String) args.get("type");
                    String source = (String) args.get("source");
                    float radius = (float) args.get("radius");
                    String schedule = (String) args.get("schedule");

                    MusicZone.SourceType st;
                    if ("playlist".equalsIgnoreCase(type)) {
                        if (plugin.playlists().get(source) == null) { err(player, "Playlist not found."); return; }
                        st = MusicZone.SourceType.PLAYLIST;
                    } else {
                        if (plugin.library().resolve(source) == null) { err(player, "Track not found."); return; }
                        st = MusicZone.SourceType.TRACK;
                    }

                    MusicZone.Schedule sch = "interval".equalsIgnoreCase(schedule)
                            ? MusicZone.Schedule.INTERVAL : MusicZone.Schedule.CONTINUOUS;
                    long intervalSec = 0L;
                    if (sch == MusicZone.Schedule.INTERVAL) {
                        Long given = (Long) args.get("interval-seconds");
                        if (given == null || given <= 0) { err(player, "Provide interval-seconds for interval schedule."); return; }
                        intervalSec = given;
                    }

                    Location loc = player.getLocation();
                    Playlist.Mode mode = (st == MusicZone.SourceType.PLAYLIST)
                            ? plugin.playlists().get(source).mode() : Playlist.Mode.SEQUENTIAL;
                    MusicZone zone = new MusicZone(name, loc.getWorld().getUID(),
                            loc.getX(), loc.getY(), loc.getZ(), radius, st, source, mode, sch, intervalSec);
                    plugin.zones().create(zone);
                    msg(player, "Created zone '" + name + "' (" + sch + (sch == MusicZone.Schedule.INTERVAL ? " " + intervalSec + "s" : "") + ").");
                }));

        root.withSubcommand(new CommandAPICommand("delete")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> zoneSuggestions())))
                .executes((sender, args) -> {
                    String name = (String) args.get("name");
                    if (plugin.zones().delete(name)) msg(sender, "Deleted zone '" + name + "'.");
                    else err(sender, "No such zone.");
                }));

        root.withSubcommand(new CommandAPICommand("list")
                .executes((sender, args) -> {
                    Collection<MusicZone> all = plugin.zones().all();
                    if (all.isEmpty()) { msg(sender, "No zones."); return; }
                    msg(sender, all.size() + " zone(s):");
                    for (MusicZone z : all) {
                        String w = Bukkit.getWorld(z.worldId()) != null ? Bukkit.getWorld(z.worldId()).getName() : z.worldId().toString();
                        sender.sendMessage(Component.text("  - " + z.name() + " @ " + w + " (" + (int) z.x() + "," + (int) z.y() + "," + (int) z.z() + ") r=" + (int) z.radius() + " " + z.sourceType() + ":" + z.source() + " [" + z.schedule() + (z.schedule() == MusicZone.Schedule.INTERVAL ? " " + z.intervalSeconds() + "s" : "") + "]", NamedTextColor.GRAY));
                    }
                }));

        root.withSubcommand(new CommandAPICommand("mode")
                .withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(info -> zoneSuggestions())))
                .withArguments(new MultiLiteralArgument("mode", "SEQUENTIAL", "LOOP", "LOOP_ONE", "SHUFFLE"))
                .executes((sender, args) -> {
                    MusicZone z = plugin.zones().get((String) args.get("name"));
                    if (z == null) { err(sender, "No such zone."); return; }
                    if (z.sourceType() != MusicZone.SourceType.PLAYLIST) { err(sender, "Mode only applies to playlist zones."); return; }
                    z.setPlaylistMode(Playlist.Mode.parse((String) args.get("mode")));
                    plugin.zones().save();
                    msg(sender, "Zone '" + z.name() + "' mode set to " + z.playlistMode() + ".");
                }));

        return root;
    }

    private String[] zoneSuggestions() {
        List<String> out = new ArrayList<>();
        for (MusicZone z : plugin.zones().all()) out.add(z.name());
        return out.toArray(new String[0]);
    }
}
