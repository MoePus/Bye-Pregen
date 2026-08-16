package com.moepus.byepregen.chunksave.serialize;

import com.moepus.byepregen.chunksave.storage.ChunkSavingCompression;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.Test;

public final class ChunkSavingBufferTest {
    private static final int RETAINED_WRITE = 128 * 1024;
    private static final int OVERSIZED_WRITE = (int) ChunkSavingNbtWriterCache.MAX_RETAINED_CAPACITY + 1;

    private ChunkSavingBufferTest() {}

    @Test
    void writesVanillaCompatibleZlib() throws Exception {
        byte[] expected = "ByePregen chunk compression compatibility".getBytes(StandardCharsets.UTF_8);
        for (boolean retainBuffer : new boolean[]{false, true}) {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (OutputStream output = ChunkSavingCompression.wrap(
                    RegionFileVersion.VERSION_DEFLATE, compressed, retainBuffer)) {
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
        try (ChunkSavingNbtWriterCache.Lease lease = ChunkSavingNbtWriterCache.acquire(true)) {
            lease.writer().write(new byte[OVERSIZED_WRITE]);
            oversizedCapacity = lease.writer().capacity();
        }
        try (ChunkSavingNbtWriterCache.Lease lease = ChunkSavingNbtWriterCache.acquire(true)) {
            if (lease.writer().capacity() >= oversizedCapacity) {
                throw new AssertionError("oversized NBT buffer was retained");
            }
        }
    }

    @Test
    void bypassesRetainedWriterWhenRetentionIsDisabled() {
        long retainedCapacity;
        try (ChunkSavingNbtWriterCache.Lease lease = ChunkSavingNbtWriterCache.acquire(true)) {
            lease.writer().write(new byte[RETAINED_WRITE]);
            retainedCapacity = lease.writer().capacity();
        }
        try (ChunkSavingNbtWriterCache.Lease lease = ChunkSavingNbtWriterCache.acquire(false)) {
            if (lease.writer().capacity() >= retainedCapacity) {
                throw new AssertionError("disabled retention reused the thread-local NBT writer");
            }
        }
        try (ChunkSavingNbtWriterCache.Lease lease = ChunkSavingNbtWriterCache.acquire(true)) {
            if (lease.writer().capacity() != retainedCapacity) {
                throw new AssertionError("disabled retention modified the retained NBT writer");
            }
        }
    }
}
