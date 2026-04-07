package fr.batmultifonction.purplemusic.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Named, ordered collection of track paths plus a play mode.
 */
public class Playlist {

    public enum Mode {
        SEQUENTIAL,   // play once in order
        LOOP,         // play in order, restart at the end
        LOOP_ONE,     // repeat the same track forever
        SHUFFLE,      // pick random tracks forever
        ;

        public static Mode parse(String s) {
            if (s == null) return SEQUENTIAL;
            try { return Mode.valueOf(s.toUpperCase()); }
            catch (IllegalArgumentException e) { return SEQUENTIAL; }
        }
    }

    private final String name;
    private final List<String> tracks = new ArrayList<>();
    private Mode mode = Mode.SEQUENTIAL;

    public Playlist(String name) {
        this.name = name;
    }

    public String name() { return name; }
    public List<String> tracks() { return Collections.unmodifiableList(tracks); }
    public Mode mode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public void add(String track) { tracks.add(track); }
    public boolean remove(String track) { return tracks.remove(track); }
    public boolean removeIndex(int idx) {
        if (idx < 0 || idx >= tracks.size()) return false;
        tracks.remove(idx);
        return true;
    }
    public void clear() { tracks.clear(); }
    public int size() { return tracks.size(); }
    public boolean isEmpty() { return tracks.isEmpty(); }

    /**
     * Computes the next track index given the current one and the mode.
     * Returns -1 if playback should end.
     */
    public int nextIndex(int current, Random rng) {
        if (tracks.isEmpty()) return -1;
        return switch (mode) {
            case SEQUENTIAL -> (current + 1 < tracks.size()) ? current + 1 : -1;
            case LOOP -> (current + 1) % tracks.size();
            case LOOP_ONE -> Math.max(current, 0);
            case SHUFFLE -> rng.nextInt(tracks.size());
        };
    }
}
