package com.moepus.byepregen.chunksave.storage;

import java.util.Arrays;

public record RawChunkData(byte[] bytes, int length) {
    public RawChunkData {
        if (length < 0 || length > bytes.length) {
            throw new IllegalArgumentException("Invalid raw chunk data length: " + length);
        }
    }

    public byte[] toByteArray() {
        return this.length == this.bytes.length ? this.bytes : Arrays.copyOf(this.bytes, this.length);
    }
}
