package com.moepus.byepregen.palette.arena.codec;

import java.nio.ByteBuffer;

public final class PayloadWriter {
    private final ByteBuffer bytes;
    private int offset;

    PayloadWriter(byte[] bytes) {
        this.bytes = ByteBuffer.wrap(bytes);
    }

    void writeNamedType(int type, byte[] name) {
        this.writeByte(type);
        this.writeBytes(name);
    }

    void writeByte(int value) {
        this.bytes.put(this.offset++, (byte) value);
    }

    void writeInt(int value) {
        this.bytes.putInt(this.offset, value);
        this.offset += Integer.BYTES;
    }

    public void writeLongArrayEntry(long value) {
        this.bytes.putLong(this.offset, value);
        this.offset += Long.BYTES;
    }

    void writeBytes(byte[] value) {
        this.bytes.put(this.offset, value);
        this.offset += value.length;
    }

    void finish() {
        if (this.offset != this.bytes.capacity()) {
            throw new IllegalStateException("NBT payload size mismatch: " + this.offset + " != " + this.bytes.capacity());
        }
    }
}
