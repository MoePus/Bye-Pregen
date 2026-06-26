package com.moepus.byepregen.gcfree;

/* Adapted from C2ME's GC-free chunk serializer. MIT License, copyright (c) 2021-2024 ishland. */

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.io.DataOutput;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.minecraft.nbt.Tag;
import sun.misc.Unsafe;

public final class NbtWriter implements DataOutput {
    private static final int DEFAULT_CAPACITY = 64 * 1024;
    private static final int GROWTH_FACTOR = 2;
    private static final Unsafe UNSAFE = loadUnsafe();
    private static final long BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);

    private long buffer;
    private long capacity;
    private long offset;

    public NbtWriter() {
        this(DEFAULT_CAPACITY);
    }

    public NbtWriter(int initialCapacity) {
        this.capacity = Math.max(1, initialCapacity);
        this.buffer = UNSAFE.allocateMemory(this.capacity);
    }

    public void startRootCompound() {
        this.writeByte(Tag.TAG_COMPOUND);
        this.writeShort(0);
    }

    public void compoundEntryStart() {}

    public void finishCompound() {
        this.writeByte(Tag.TAG_END);
    }

    public void putBoolean(byte[] name, boolean value) {
        this.putByte(name, (byte) (value ? 1 : 0));
    }

    public void putByte(byte[] name, byte value) {
        this.writeNamedType(Tag.TAG_BYTE, name);
        this.writeByte(value);
    }

    public void putShort(byte[] name, short value) {
        this.writeNamedType(Tag.TAG_SHORT, name);
        this.writeShort(value);
    }

    public void putInt(byte[] name, int value) {
        this.writeNamedType(Tag.TAG_INT, name);
        this.writeInt(value);
    }

    public void putLong(byte[] name, long value) {
        this.writeNamedType(Tag.TAG_LONG, name);
        this.writeLong(value);
    }

    public void putString(byte[] name, byte[] value) {
        this.writeNamedType(Tag.TAG_STRING, name);
        this.writeBytesRaw(value);
    }

    public void putString(byte[] name, String value) {
        this.putString(name, stringBytes(value));
    }

    public void startCompound(byte[] name) {
        this.writeNamedType(Tag.TAG_COMPOUND, name);
    }

    public void startFixedList(byte[] name, int size, byte type) {
        this.writeNamedType(Tag.TAG_LIST, name);
        this.writeByte(type);
        this.writeInt(size);
    }

    public long startList(byte[] name, byte type) {
        this.writeNamedType(Tag.TAG_LIST, name);
        this.writeByte(type);
        long lengthOffset = this.offset;
        this.writeInt(0);
        return lengthOffset;
    }

    public void finishList(long lengthOffset, int size) {
        this.putIntAt(lengthOffset, size);
    }

    public void startFixedListEntry(int size, byte type) {
        this.writeByte(type);
        this.writeInt(size);
    }

    public void putByteArray(byte[] name, byte[] value) {
        this.writeNamedType(Tag.TAG_BYTE_ARRAY, name);
        this.writeInt(value.length);
        this.write(value, 0, value.length);
    }

    public void putByteArrayFilled(byte[] name, int length, byte value) {
        this.writeNamedType(Tag.TAG_BYTE_ARRAY, name);
        this.writeInt(length);
        this.ensureCapacity(length);
        UNSAFE.setMemory(this.address(), length, value);
        this.offset += length;
    }

    public void putIntArray(byte[] name, int[] value) {
        this.writeNamedType(Tag.TAG_INT_ARRAY, name);
        this.writeInt(value.length);
        this.ensureCapacity(value.length * Integer.BYTES);
        for (int entry : value) {
            this.insertInt(entry);
        }
    }

    public void putLongArray(byte[] name, long[] value) {
        this.startLongArray(name, value.length);
        this.writeLongArrayPayload(value, value.length);
    }

    public void putLongArray(byte[] name, long[] value, int length) {
        this.startLongArray(name, length);
        this.writeLongArrayPayload(value, length);
    }

    public void putLongArray(byte[] name, LongCollection value) {
        this.startLongArray(name, value.size());
        LongIterator iterator = value.iterator();
        while (iterator.hasNext()) {
            this.writeLongArrayEntry(iterator.nextLong());
        }
    }

    public void startLongArray(byte[] name, int length) {
        this.writeNamedType(Tag.TAG_LONG_ARRAY, name);
        this.writeInt(length);
        this.ensureCapacity((long) length * Long.BYTES);
    }

    public void writeLongArrayEntry(long entry) {
        this.insertLong(entry);
    }

    public void putDoubles(byte[] name, double[] value) {
        this.startFixedList(name, value.length, Tag.TAG_DOUBLE);
        for (double entry : value) {
            this.writeDouble(entry);
        }
    }

    public void putTag(byte[] name, Tag tag) {
        this.writeNamedType(tag.getId(), name);
        this.writeTagPayload(tag);
    }

    public void putTagEntry(Tag tag) {
        this.writeTagPayload(tag);
    }

    public byte[] toByteArray() {
        if (this.buffer == 0L) {
            throw new IllegalStateException("NbtWriter has been released");
        }
        int length = Math.toIntExact(this.offset);
        byte[] bytes = new byte[length];
        UNSAFE.copyMemory(null, this.buffer, bytes, BYTE_ARRAY_OFFSET, length);
        return bytes;
    }

    public RawChunkData toRawChunkData(int maxSlackBytes) {
        byte[] bytes = this.toByteArray();
        return new RawChunkData(bytes, bytes.length);
    }

    public void release() {
        if (this.buffer != 0L) {
            UNSAFE.freeMemory(this.buffer);
            this.buffer = 0L;
        }
    }

    @Override
    public void write(int value) {
        this.writeByte(value);
    }

    @Override
    public void write(byte[] value) {
        this.write(value, 0, value.length);
    }

    @Override
    public void write(byte[] value, int off, int len) {
        this.ensureCapacity(len);
        UNSAFE.copyMemory(value, BYTE_ARRAY_OFFSET + off, null, this.address(), len);
        this.offset += len;
    }

    @Override
    public void writeBoolean(boolean value) {
        this.writeByte(value ? 1 : 0);
    }

    @Override
    public void writeByte(int value) {
        this.ensureCapacity(1);
        this.insertByte(value);
    }

    @Override
    public void writeShort(int value) {
        this.ensureCapacity(Short.BYTES);
        this.insertShort(value);
    }

    @Override
    public void writeChar(int value) {
        this.writeShort(value);
    }

    @Override
    public void writeInt(int value) {
        this.ensureCapacity(Integer.BYTES);
        this.insertInt(value);
    }

    @Override
    public void writeLong(long value) {
        this.ensureCapacity(Long.BYTES);
        this.insertLong(value);
    }

    @Override
    public void writeFloat(float value) {
        this.writeInt(Float.floatToRawIntBits(value));
    }

    @Override
    public void writeDouble(double value) {
        this.writeLong(Double.doubleToRawLongBits(value));
    }

    @Override
    public void writeBytes(String value) {
        this.write(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Override
    public void writeChars(String value) {
        for (int i = 0; i < value.length(); ++i) {
            this.writeChar(value.charAt(i));
        }
    }

    @Override
    public void writeUTF(String value) throws IOException {
        this.writeBytesRaw(stringBytes(value));
    }

    public static byte[] asciiName(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            if (b <= 0) {
                throw new IllegalArgumentException("NBT name is not ascii-safe: " + value);
            }
        }
        return wrapUtfBytes(bytes);
    }

    public static byte[] stringBytes(String value) {
        byte[] bytes = new byte[value.length() * 3 + 2];
        int index = 2;
        for (int i = 0; i < value.length(); ++i) {
            index = writeUtfChar(bytes, index, value.charAt(i));
        }
        int length = index - 2;
        if (length > 65535) {
            throw new RuntimeException(new UTFDataFormatException("String too large"));
        }
        bytes[0] = (byte) (length >>> 8);
        bytes[1] = (byte) length;
        return Arrays.copyOf(bytes, index);
    }

    private void writeLongArrayPayload(long[] value, int length) {
        for (int i = 0; i < length; ++i) {
            this.writeLongArrayEntry(value[i]);
        }
    }

    private void writeNamedType(int type, byte[] name) {
        this.ensureCapacity(1L + name.length);
        this.insertByte(type);
        this.insertBytes(name);
    }

    private void writeBytesRaw(byte[] bytes) {
        this.write(bytes, 0, bytes.length);
    }

    private void writeTagPayload(Tag tag) {
        try {
            tag.write(this);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected in-memory NBT write failure", e);
        }
    }

    private void putIntAt(long index, int value) {
        UNSAFE.putInt(this.buffer + index, Integer.reverseBytes(value));
    }

    private void ensureCapacity(long extra) {
        if (this.buffer == 0L) {
            throw new IllegalStateException("NbtWriter has been released");
        }
        long required = this.offset + extra;
        if (required <= this.capacity) {
            return;
        }
        do {
            this.capacity *= GROWTH_FACTOR;
        } while (required > this.capacity);
        this.buffer = UNSAFE.reallocateMemory(this.buffer, this.capacity);
    }

    private long address() {
        return this.buffer + this.offset;
    }

    private void insertByte(int value) {
        UNSAFE.putByte(this.address(), (byte) value);
        this.offset += Byte.BYTES;
    }

    private void insertShort(int value) {
        UNSAFE.putShort(this.address(), Short.reverseBytes((short) value));
        this.offset += Short.BYTES;
    }

    private void insertInt(int value) {
        UNSAFE.putInt(this.address(), Integer.reverseBytes(value));
        this.offset += Integer.BYTES;
    }

    private void insertLong(long value) {
        UNSAFE.putLong(this.address(), Long.reverseBytes(value));
        this.offset += Long.BYTES;
    }

    private void insertBytes(byte[] value) {
        UNSAFE.copyMemory(value, BYTE_ARRAY_OFFSET, null, this.address(), value.length);
        this.offset += value.length;
    }

    private static int writeUtfChar(byte[] bytes, int index, char c) {
        if (c >= 1 && c <= 127) {
            bytes[index++] = (byte) c;
        } else if (c <= 2047) {
            bytes[index++] = (byte) (192 | (c >> 6));
            bytes[index++] = (byte) (128 | (c & 63));
        } else {
            bytes[index++] = (byte) (224 | (c >> 12));
            bytes[index++] = (byte) (128 | ((c >> 6) & 63));
            bytes[index++] = (byte) (128 | (c & 63));
        }
        return index;
    }

    private static byte[] wrapUtfBytes(byte[] bytes) {
        byte[] wrapped = new byte[bytes.length + 2];
        wrapped[0] = (byte) (bytes.length >>> 8);
        wrapped[1] = (byte) bytes.length;
        System.arraycopy(bytes, 0, wrapped, 2, bytes.length);
        return wrapped;
    }

    private static Unsafe loadUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
