package fr.batmultifonction.purplemusic.util;

import fr.batmultifonction.purplemusic.PurpleMusic;
import fr.batmultifonction.purplemusic.audio.AudioDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Downloads audio files into the musicdata folder. Validates URL scheme, file
 * extension, filename safety, and content size.
 */
public class DownloadService {

    public static class DownloadException extends Exception {
        public DownloadException(String message) { super(message); }
    }

    private final PurpleMusic plugin;

    public DownloadService(PurpleMusic plugin) {
        this.plugin = plugin;
    }

    public Path download(String urlString, String filename) throws DownloadException {
        if (filename.length() > plugin.filenameMaxLength()) {
            throw new DownloadException("Filename too long (max " + plugin.filenameMaxLength() + ").");
        }
        if (!AudioDecoder.isSupported(filename)) {
            throw new DownloadException("Unsupported file extension. Use .wav, .mp3, or .flac.");
        }
        if (!plugin.library().isPathSafe(filename)) {
            throw new DownloadException("Invalid filename (path traversal).");
        }
        if (!plugin.library().isDepthAllowed(filename)) {
            throw new DownloadException("Subdirectory depth not allowed by config.");
        }

        URI uri;
        try { uri = new URI(urlString); }
        catch (Exception e) { throw new DownloadException("Invalid URL."); }
        if (uri.getScheme() == null
                || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
            throw new DownloadException("Only http(s) URLs are supported.");
        }

        Path target = plugin.library().root().resolve(filename).toAbsolutePath().normalize();
        if (!target.startsWith(plugin.library().root())) {
            throw new DownloadException("Resolved path escapes musicdata.");
        }
        target = uniquePath(target);

        try {
            Files.createDirectories(target.getParent());
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestProperty("User-Agent", "PurpleMusic/1.0");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);
            conn.connect();
            if (conn.getResponseCode() != 200) {
                throw new DownloadException("HTTP " + conn.getResponseCode());
            }
            long len = conn.getContentLengthLong();
            if (len > 0 && len / 1048576L > plugin.maxDownloadSize()) {
                throw new DownloadException("File too large (max " + plugin.maxDownloadSize() + " MB).");
            }
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            // size check after if header was missing
            if (len <= 0 && Files.size(target) / 1048576L > plugin.maxDownloadSize()) {
                Files.deleteIfExists(target);
                throw new DownloadException("File too large (max " + plugin.maxDownloadSize() + " MB).");
            }
            return target;
        } catch (IOException e) {
            throw new DownloadException("Download failed: " + e.getMessage());
        }
    }

    private Path uniquePath(Path candidate) {
        if (!Files.exists(candidate)) return candidate;
        String name = candidate.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        Path parent = candidate.getParent();
        for (int i = 1; i < 1000; i++) {
            Path c = parent.resolve(base + "_" + i + ext);
            if (!Files.exists(c)) return c;
        }
        return candidate;
    }
}
