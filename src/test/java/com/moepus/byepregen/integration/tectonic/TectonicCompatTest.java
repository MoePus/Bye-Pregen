package com.moepus.byepregen.integration.tectonic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.moepus.byepregen.serialization.nbt.NbtWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

final class TectonicCompatTest {
    private static final String KEY = "tectonic:blending_version";
    private static final byte[] ENCODED_KEY = NbtWriter.asciiName(KEY);

    @Test
    void writesNonZeroBlendingVersion() throws Exception {
        CompoundTag tag = writeMarker(7);

        assertEquals(7, tag.getInt(KEY));
    }

    @Test
    void skipsDisabledBlendingVersion() throws Exception {
        CompoundTag tag = writeMarker(0);

        assertFalse(tag.contains(KEY));
    }

    private static CompoundTag writeMarker(int version) throws Exception {
        NbtWriter writer = new NbtWriter(32);
        try {
            writer.startRootCompound();
            TectonicCompat.writeBlendingVersion(writer, ENCODED_KEY, version);
            writer.finishCompound();
            try (DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(writer.toByteArray()))) {
                return NbtIo.read(input);
            }
        } finally {
            writer.release();
        }
    }
}
