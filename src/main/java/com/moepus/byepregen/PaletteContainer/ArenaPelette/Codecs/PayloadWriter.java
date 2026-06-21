package com.moepus.byepregen.PaletteContainer.ArenaPelette.Codecs;

public final class PayloadWriter {
    private final byte[] bytes;
    private int offset;

    PayloadWriter(byte[] bytes) {
        this.bytes = bytes;
    }

    void writeNamedType(int type, byte[] name) {
        this.writeByte(type);
        this.writeBytes(name);
    }

    void writeByte(int value) {
        this.bytes[this.offset++] = (byte) value;
    }

    void writeInt(int value) {
        this.bytes[this.offset++] = (byte) (value >>> 24);
        this.bytes[this.offset++] = (byte) (value >>> 16);
        this.bytes[this.offset++] = (byte) (value >>> 8);
        this.bytes[this.offset++] = (byte) value;
    }

    public void writeLongArrayEntry(long value) {
        this.bytes[this.offset++] = (byte) (value >>> 56);
        this.bytes[this.offset++] = (byte) (value >>> 48);
        this.bytes[this.offset++] = (byte) (value >>> 40);
        this.bytes[this.offset++] = (byte) (value >>> 32);
        this.bytes[this.offset++] = (byte) (value >>> 24);
        this.bytes[this.offset++] = (byte) (value >>> 16);
        this.bytes[this.offset++] = (byte) (value >>> 8);
        this.bytes[this.offset++] = (byte) value;
    }

    void writeBytes(byte[] value) {
        System.arraycopy(value, 0, this.bytes, this.offset, value.length);
        this.offset += value.length;
    }

    void finish() {
        if (this.offset != this.bytes.length) {
            throw new IllegalStateException("NBT payload size mismatch: " + this.offset + " != " + this.bytes.length);
        }
    }
}
