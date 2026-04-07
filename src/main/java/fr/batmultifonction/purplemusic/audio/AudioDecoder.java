package fr.batmultifonction.purplemusic.audio;

import fr.batmultifonction.purplemusic.util.PCM16Downscaler;
import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;
import org.jflac.sound.spi.Flac2PcmAudioInputStream;
import org.jflac.sound.spi.FlacAudioFileReader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Decodes wav/mp3/flac files into the canonical 48 kHz, mono, 16-bit PCM stream that
 * Simple Voice Chat consumes.
 */
public final class AudioDecoder {

    public static final AudioFormat TARGET_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, 48000F, 16, 1, 2, 48000F, false);

    private AudioDecoder() {}

    public static AudioInputStream open(Path file) throws UnsupportedAudioFileException, IOException {
        String ext = extension(file.getFileName().toString());
        return switch (ext) {
            case "wav" -> {
                AudioInputStream raw = AudioSystem.getAudioInputStream(file.toFile());
                yield AudioSystem.getAudioInputStream(TARGET_FORMAT, raw);
            }
            case "mp3" -> {
                AudioInputStream raw = new MpegAudioFileReader().getAudioInputStream(file.toFile());
                AudioFormat base = raw.getFormat();
                AudioFormat decoded = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        base.getSampleRate(), 16, base.getChannels(),
                        base.getChannels() * 2, base.getSampleRate(), false);
                AudioInputStream pcm = new MpegFormatConversionProvider().getAudioInputStream(decoded, raw);
                yield AudioSystem.getAudioInputStream(TARGET_FORMAT, pcm);
            }
            case "flac" -> {
                AudioInputStream raw = new FlacAudioFileReader().getAudioInputStream(file.toFile());
                AudioFormat base = raw.getFormat();
                AudioFormat decodedFlac = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        base.getSampleRate(), base.getSampleSizeInBits(), base.getChannels(),
                        (base.getSampleSizeInBits() / 8) * base.getChannels(), base.getSampleRate(), false);
                AudioInputStream pcm = new Flac2PcmAudioInputStream(raw, decodedFlac, raw.getFrameLength());
                AudioInputStream pcm16;
                if (base.getSampleSizeInBits() > 16) {
                    AudioFormat finalFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                            base.getSampleRate(), 16, base.getChannels(),
                            base.getChannels() * 2, base.getSampleRate(), false);
                    pcm16 = new AudioInputStream(new PCM16Downscaler(pcm), finalFormat, pcm.getFrameLength());
                } else {
                    pcm16 = pcm;
                }
                yield AudioSystem.getAudioInputStream(TARGET_FORMAT, pcm16);
            }
            default -> throw new UnsupportedAudioFileException("Unsupported audio format: " + ext);
        };
    }

    public static boolean isSupported(String filename) {
        String ext = extension(filename);
        return ext.equals("wav") || ext.equals("mp3") || ext.equals("flac");
    }

    public static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
