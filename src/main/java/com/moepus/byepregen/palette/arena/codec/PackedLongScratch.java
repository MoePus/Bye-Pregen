package com.moepus.byepregen.palette.arena.codec;

final class PackedLongScratch {
    private long writeMask;
    private long packedWord;
    private long emittedWord;
    private int bits;
    private int valuesPerLong;
    private int packedShift;
    private int packedValuesInWord;

    void begin(int bits) {
        this.bits = bits;
        this.valuesPerLong = Long.SIZE / bits;
        this.writeMask = (1L << bits) - 1L;
        this.packedWord = 0L;
        this.emittedWord = 0L;
        this.packedShift = 0;
        this.packedValuesInWord = 0;
    }

    boolean write(int localId) {
        this.packedWord |= ((long) localId & this.writeMask) << this.packedShift;
        if (++this.packedValuesInWord == this.valuesPerLong) {
            this.emittedWord = this.packedWord;
            this.packedWord = 0L;
            this.packedShift = 0;
            this.packedValuesInWord = 0;
            return true;
        }
        this.packedShift += this.bits;
        return false;
    }

    boolean hasPendingWord() {
        return this.packedValuesInWord != 0;
    }

    long emittedWord() {
        return this.emittedWord;
    }

    long pendingWord() {
        return this.packedWord;
    }

    int valuesPerLong() {
        return this.valuesPerLong;
    }

    void clear() {
        this.writeMask = 0L;
        this.packedWord = 0L;
        this.emittedWord = 0L;
        this.bits = 0;
        this.valuesPerLong = 0;
        this.packedShift = 0;
        this.packedValuesInWord = 0;
    }
}
