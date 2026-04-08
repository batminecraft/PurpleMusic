package fr.batmultifonction.purplemusic.library;

import fr.batmultifonction.purplemusic.PurpleMusic;
import fr.batmultifonction.purplemusic.audio.AudioDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Indexes the musicdata folder. Provides safe path resolution and listing.
 */
public class MusicLibrary {

    private final PurpleMusic plugin;
    private final Path root;

    public MusicLibrary(PurpleMusic plugin) {
        this.plugin = plugin;
        this.root = Path.of(plugin.getDataFolder().getPath(), "musicdata").toAbsolutePath().normalize();
    }

    public Path root() { return root; }

    /**
     * Resolves a user-supplied relative filename to an actual file under musicdata,
     * preventing path traversal. Returns null if the file does not exist or is unsafe.
     */
    public Path resolve(String name) {
        if (name == null || name.isBlank()) return null;
        Path resolved = root.resolve(name).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) return null;
        if (!Files.isRegularFile(resolved)) return null;
        if (!AudioDecoder.isSupported(resolved.getFileName().toString())) return null;
        return resolved;
    }

    public boolean isPathSafe(String name) {
        Path resolved = root.resolve(name).toAbsolutePath().normalize();
        return resolved.startsWith(root);
    }

    public boolean isDepthAllowed(String name) {
        Path resolved = root.resolve(name).toAbsolutePath().normalize();
        Path rel = root.relativize(resolved);
        int depth = rel.getNameCount() - 1;
        return switch (plugin.subdirectoryDepth()) {
            case "none" -> depth == 0;
            case "single" -> depth <= 1;
            default -> true;
        };
    }

    /**
     * Returns the on-disk path that {@code name} would resolve to inside the
     * library, regardless of whether the file exists. Returns {@code null} if
     * the name is empty or escapes the library root.
     */
    public Path resolveDestination(String name) {
        if (name == null || name.isBlank()) return null;
        Path resolved = root.resolve(name).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) return null;
        return resolved;
    }

    /** Imports a new audio file from a stream. The stream is fully consumed. */
    public Path importFile(String name, InputStream data) throws IOException {
        Path dest = resolveDestination(name);
        if (dest == null) throw new IOException("Invalid track name: " + name);
        if (!AudioDecoder.isSupported(dest.getFileName().toString())) {
            throw new IOException("Unsupported audio format: " + name);
        }
        if (Files.exists(dest)) throw new IOException("Track already exists: " + name);
        Files.createDirectories(dest.getParent());
        Files.copy(data, dest);
        return dest;
    }

    /** Deletes a track on disk. Returns true if a file was actually removed. */
    public boolean deleteFile(String name) throws IOException {
        Path file = resolve(name);
        if (file == null) return false;
        Files.delete(file);
        return true;
    }

    /** Renames a track on disk to a new name. */
    public Path renameFile(String oldName, String newName) throws IOException {
        Path src = resolve(oldName);
        if (src == null) throw new IOException("Track not found: " + oldName);
        Path dest = resolveDestination(newName);
        if (dest == null) throw new IOException("Invalid new track name: " + newName);
        if (!AudioDecoder.isSupported(dest.getFileName().toString())) {
            throw new IOException("Unsupported audio format: " + newName);
        }
        if (Files.exists(dest)) throw new IOException("Track already exists: " + newName);
        Files.createDirectories(dest.getParent());
        return Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Lists every audio file in musicdata, returning paths relative to the root. */
    public List<String> listTracks() {
        if (!Files.isDirectory(root)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> AudioDecoder.isSupported(p.getFileName().toString()))
                .forEach(p -> out.add(root.relativize(p).toString().replace('\\', '/')));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not walk musicdata: " + e.getMessage());
        }
        Collections.sort(out);
        return out;
    }
}
