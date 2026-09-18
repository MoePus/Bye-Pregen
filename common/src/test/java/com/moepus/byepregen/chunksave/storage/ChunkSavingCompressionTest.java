package com.moepus.byepregen.chunksave.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import java.util.zip.InflaterInputStream;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ChunkSavingCompressionTest {
    private static final int PAYLOAD_SIZE = 65536;

    @Test
    void repeatedAndNestedStreamsDoNotShareDeflaterState() throws Exception {
        byte[] first = payload(1);
        byte[] second = payload(2);
        ByteArrayOutputStream outer = new ByteArrayOutputStream();
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        try (OutputStream a = wrap(outer)) {
            a.write(first, 0, first.length / 2);
            try (OutputStream b = wrap(inner)) { b.write(second); }
            a.write(first, first.length / 2, first.length / 2);
        }
        assertArrayEquals(first, inflate(outer));
        assertArrayEquals(second, inflate(inner));
        assertRoundTrip(second);
    }

    @Test
    void failedCloseReleasesLeaseForNextWrite() throws Exception {
        OutputStream broken = new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("fixture"); }
        };
        OutputStream compressed = wrap(broken);
        compressed.write(new byte[]{1, 2, 3});
        assertThrows(IOException.class, compressed::close);
        assertRoundTrip(payload(3));
    }

    @Test
    void uncompressedVersionPassesThroughEvenWithRetention() throws Exception {
        byte[] bytes = payload(4);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (OutputStream stream = ChunkSavingCompression.wrap(RegionFileVersion.VERSION_NONE, output, true)) {
            stream.write(bytes);
        }
        assertArrayEquals(bytes, output.toByteArray());
    }

    private static void assertRoundTrip(byte[] expected) throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        OutputStream stream = wrap(compressed);
        stream.write(expected);
        stream.close();
        stream.close();
        assertArrayEquals(expected, inflate(compressed));
    }

    private static OutputStream wrap(OutputStream output) throws IOException {
        return ChunkSavingCompression.wrap(RegionFileVersion.VERSION_DEFLATE, output, true);
    }

    private static byte[] inflate(ByteArrayOutputStream output) throws IOException {
        try (var input = new InflaterInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            return input.readAllBytes();
        }
    }

    private static byte[] payload(long seed) {
        byte[] bytes = new byte[PAYLOAD_SIZE];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }
}
