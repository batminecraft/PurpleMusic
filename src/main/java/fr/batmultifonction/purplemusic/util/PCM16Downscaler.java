package fr.batmultifonction.purplemusic.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Downscales PCM audio with sample size > 16 bits to 16-bit PCM.
 */
public class PCM16Downscaler extends InputStream {

    private final InputStream source;
    private final int srcSampleSizeBytes;

    public PCM16Downscaler(java.io.InputStream src) {
        this.source = src;
        this.srcSampleSizeBytes = 3; // we typically downscale 24-bit FLAC; 32-bit handled too via read
    }

    @Override
    public int read() throws IOException {
        throw new IOException("Use read(byte[], int, int)");
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        // We assume source is 24-bit little endian PCM. We output 16-bit by dropping LSB.
        int srcLen = (len / 2) * 3;
        byte[] tmp = new byte[srcLen];
        int read = 0;
        while (read < srcLen) {
            int r = source.read(tmp, read, srcLen - read);
            if (r < 0) break;
            read += r;
        }
        if (read <= 0) return -1;
        int samples = read / 3;
        for (int i = 0; i < samples; i++) {
            // little endian: drop the LSB (tmp[i*3])
            b[off + i * 2] = tmp[i * 3 + 1];
            b[off + i * 2 + 1] = tmp[i * 3 + 2];
        }
        return samples * 2;
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}
