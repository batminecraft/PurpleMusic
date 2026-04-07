# PurpleMusic

A Paper/Folia plugin built on the [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) API
that lets staff stream real audio (wav / mp3 / flac) to players, build queues and playlists, and create
persistent music zones around any location.

## Features

- **Direct playback** - play a track to one player, several, or every online player.
- **Queues** - per-player and global queues that auto-advance when each track ends.
- **Playlists** - named, persistent playlists with four play modes:
  - `SEQUENTIAL` - play once in order
  - `LOOP` - replay the playlist forever
  - `LOOP_ONE` - repeat the same track forever
  - `SHUFFLE` - random pick forever
- **Music zones** - persistent spheres at a location with a configurable radius. Each zone plays
  a single track or a playlist, either continuously or on a fixed interval (X seconds between plays).
- **Downloads** - admins can pull `.wav` / `.mp3` / `.flac` files straight from a URL into
  `plugins/PurpleMusic/musicdata/`.

## Requirements

- Paper 1.21+
- Simple Voice Chat (server + client) 2.6.0 or newer

## Commands

All commands are also available under the aliases `/pmusic` and `/pm`.

| Command | Description |
| --- | --- |
| `/pm download <url> <filename>` | Download an audio file into `musicdata/` |
| `/pm list` | List every track in `musicdata/` |
| `/pm play <player> <track>` | Play a track to one player |
| `/pm playall <track>` | Play a track to every online player |
| `/pm stop <player\|all>` | Stop playback for a player or everyone |
| `/pm queue add <player\|all> <track>` | Add a track to a queue |
| `/pm queue clear <player\|all>` | Clear a queue |
| `/pm queue list <player\|all>` | Show queue contents |
| `/pm queue skip <player\|all>` | Skip the current track |
| `/pm playlist create <name>` | Create an empty playlist |
| `/pm playlist delete <name>` | Delete a playlist |
| `/pm playlist add <name> <track>` | Add a track to a playlist |
| `/pm playlist remove <name> <track>` | Remove a track from a playlist |
| `/pm playlist mode <name> <mode>` | Set the playlist mode |
| `/pm playlist list` | List every playlist |
| `/pm playlist show <name>` | Show one playlist's tracks |
| `/pm playlist play <player> <name>` | Play a playlist to one player |
| `/pm playlist playall <name>` | Play a playlist to every online player |
| `/pm zone create <name> <track\|playlist> <source> <radius> <continuous\|interval> [seconds]` | Create a music zone at your current location |
| `/pm zone delete <name>` | Delete a zone |
| `/pm zone list` | List every zone |
| `/pm zone mode <name> <mode>` | Set the play mode for a playlist-backed zone |
| `/pm reload` | Reload config, playlists and zones |

## Permissions

Each command checks a granular permission (`purplemusic.download`, `purplemusic.play`,
`purplemusic.stop`, `purplemusic.queue`, `purplemusic.playlist`, `purplemusic.zone`,
`purplemusic.list`, `purplemusic.reload`). The `purplemusic.*` parent grants them all.
All default to `op`.

## Data layout

```
plugins/PurpleMusic/
  config.yml       # global settings
  playlists.yml    # named playlists (auto-managed)
  zones.yml        # music zones (auto-managed)
  musicdata/       # all audio files (wav / mp3 / flac)
```

The `subdirectory-depth` config option controls how deeply you can nest files inside
`musicdata/` (`none`, `single`, or `unrestricted`).
