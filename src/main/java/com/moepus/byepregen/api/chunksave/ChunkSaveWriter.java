package com.moepus.byepregen.api.chunksave;

import java.nio.charset.StandardCharsets;
import net.minecraft.nbt.Tag;

/** Minimal raw-NBT surface exposed to optional chunk-save integrations. */
public interface ChunkSaveWriter {
    void putByteArray(byte[] name, byte[] value);

    void putTag(byte[] name, Tag value);

    static byte[] name(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0xFFFF) throw new IllegalArgumentException("NBT name is too long");
        for (byte valueByte : bytes) {
            if (valueByte <= 0) throw new IllegalArgumentException("NBT name is not ascii-safe: " + value);
        }
        byte[] result = new byte[bytes.length + 2];
        result[0] = (byte) (bytes.length >>> 8);
        result[1] = (byte) bytes.length;
        System.arraycopy(bytes, 0, result, 2, bytes.length);
        return result;
    }
}
