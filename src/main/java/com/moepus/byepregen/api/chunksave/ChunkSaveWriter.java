package com.moepus.byepregen.api.chunksave;

import java.nio.charset.StandardCharsets;
import net.minecraft.nbt.Tag;

/**
 * Minimal raw-NBT surface exposed to optional chunk-save integrations.
 *
 * <p>Every {@code byte[]} name is a length-prefixed NBT name as returned by {@link #name(String)}.
 * Passing plain bytes such as {@code "my_key".getBytes()} corrupts the chunk NBT stream. The
 * {@link String} overloads encode the name for you and allocate a small array on every call;
 * cache {@link #name(String)} and use the {@code byte[]} overloads when that matters.</p>
 */
public interface ChunkSaveWriter {
    void putByteArray(byte[] name, byte[] value);

    void putTag(byte[] name, Tag value);

    void putByte(byte[] name, byte value);

    void putBoolean(byte[] name, boolean value);

    void putInt(byte[] name, int value);

    void putLong(byte[] name, long value);

    void putString(byte[] name, String value);

    default void putByteArray(String name, byte[] value) {
        this.putByteArray(name(name), value);
    }

    default void putTag(String name, Tag value) {
        this.putTag(name(name), value);
    }

    default void putByte(String name, byte value) {
        this.putByte(name(name), value);
    }

    default void putBoolean(String name, boolean value) {
        this.putBoolean(name(name), value);
    }

    default void putInt(String name, int value) {
        this.putInt(name(name), value);
    }

    default void putLong(String name, long value) {
        this.putLong(name(name), value);
    }

    default void putString(String name, String value) {
        this.putString(name(name), value);
    }

    /**
     * Encodes an NBT name as a two-byte big-endian length followed by its ASCII bytes, ready for
     * the {@code byte[]} overloads.
     *
     * @throws IllegalArgumentException if the name is longer than 65535 bytes or is not ASCII
     */
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
