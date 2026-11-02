package com.moepus.byepregen.api.chunksave;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.serialization.nbt.NbtWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class ChunkSaveHandlerRegistryTest {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("byepregen_test", "handler");

    @Test
    void writesReadableRootFields() throws Exception {
        ChunkSaveHandlerRegistry.register(ID, (level, chunk, writer) -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("k", "v");
            writer.putInt("test:count", 7);
            writer.putLong("test:time", 1234567890123L);
            writer.putBoolean("test:flag", true);
            writer.putString("test:name", "value");
            writer.putByteArray("test:blob", new byte[]{1, 2, 3});
            writer.putTag("test:tag", payload);
        });
        try {
            NbtWriter writer = new NbtWriter(256);
            try {
                writer.startRootCompound();
                ChunkSaveHandlerRegistry.write(null, null, writer);
                writer.finishCompound();
                CompoundTag root;
                try (DataInputStream input = new DataInputStream(
                        new ByteArrayInputStream(writer.toByteArray()))) {
                    root = NbtIo.read(input);
                }
                assertNotNull(root);
                assertEquals(7, root.getInt("test:count"));
                assertEquals(1234567890123L, root.getLong("test:time"));
                assertTrue(root.getBoolean("test:flag"));
                assertEquals("value", root.getString("test:name"));
                assertArrayEquals(new byte[]{1, 2, 3}, root.getByteArray("test:blob"));
                assertEquals("v", root.getCompound("test:tag").getString("k"));
            } finally {
                writer.release();
            }
        } finally {
            ChunkSaveHandlerRegistry.unregister(ID);
        }
    }
}
