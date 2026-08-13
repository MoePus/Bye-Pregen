package com.moepus.byepregen.gcfree;

import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.config.ConfigParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public final class ChunkSavingBufferTest {
    private static final int OVERSIZED_WRITE = (int) ChunkSavingNbtWriterCache.MAX_RETAINED_CAPACITY + 1;

    private ChunkSavingBufferTest() {}

    @Test
    void writesVanillaCompatibleZlib() throws Exception {
        byte[] expected = "ByePregen chunk compression compatibility".getBytes(StandardCharsets.UTF_8);
        for (int iteration = 0; iteration < 2; ++iteration) {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (OutputStream output = ChunkSavingCompression.wrap(
                    RegionFileVersion.VERSION_DEFLATE, compressed)) {
                output.write(expected);
            }
            byte[] actual;
            try (InflaterInputStream input = new InflaterInputStream(
                    new ByteArrayInputStream(compressed.toByteArray()))) {
                actual = input.readAllBytes();
            }
            if (!Arrays.equals(expected, actual)) {
                throw new AssertionError("retained Deflater produced incompatible zlib data");
            }
        }
    }

    @Test
    void dropsOversizedNbtWriter() {
        long oversizedCapacity;
        try (ChunkSavingNbtWriterCache.Lease lease = ChunkSavingNbtWriterCache.acquire()) {
            lease.writer().write(new byte[OVERSIZED_WRITE]);
            oversizedCapacity = lease.writer().capacity();
        }
        try (ChunkSavingNbtWriterCache.Lease lease = ChunkSavingNbtWriterCache.acquire()) {
            if (lease.writer().capacity() >= oversizedCapacity) {
                throw new AssertionError("oversized NBT buffer was retained");
            }
        }
    }

    @BeforeAll
    static void installConfig() {
        Config config = new Config();
        config.retainChunkSavingBuffer = true;
        ConfigParser.setConfig(config);
    }
}
