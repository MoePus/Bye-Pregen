package com.moepus.byepregen.gcfree;

import java.util.Arrays;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;

final class ChunkSectionSerializationContext {
    private static final int INITIAL_PALETTE_CAPACITY = 16;
    private static final int INITIAL_TABLE_CAPACITY = 32;
    private static final int TABLE_LOAD_DIVISOR = 2;
    private static final int BITS_PER_LONG = Long.SIZE;

    private Object[] paletteEntries;
    private Object[] tableKeys;
    private int[] tableValues;
    private long[] packed;
    private int paletteSize;
    private int packedLength;

    <T> boolean pack(PalettedContainerRO<T> source, int minimumBits) {
        if (!(source instanceof PalettedContainer<?> container)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        PalettedContainer<T> typed = (PalettedContainer<T>) container;
        this.pack(typed, minimumBits);
        return true;
    }

    <T> void pack(PalettedContainer<T> container, int minimumBits) {
        this.clear();
        container.acquire();
        try {
            PalettedContainer.Data<T> data = container.data;
            this.collectPalette(data);
            int bits = this.paletteSize == 1
                    ? 0
                    : Math.max(minimumBits, ceilLog2(this.paletteSize));
            if (bits != 0) {
                this.packStorage(data, bits);
            }
        } finally {
            container.release();
        }
    }

    int paletteSize() {
        return this.paletteSize;
    }

    @SuppressWarnings("unchecked")
    <T> T paletteEntry(int index) {
        return (T) this.paletteEntries[index];
    }

    long[] packed() {
        return this.packed;
    }

    int packedLength() {
        return this.packedLength;
    }

    void clear() {
        if (this.paletteEntries != null) {
            Arrays.fill(this.paletteEntries, 0, this.paletteSize, null);
        }
        if (this.tableKeys != null) {
            Arrays.fill(this.tableKeys, null);
        }
        this.paletteSize = 0;
        this.packedLength = 0;
    }

    private <T> void collectPalette(PalettedContainer.Data<T> data) {
        BitStorage storage = data.storage();
        Palette<T> palette = data.palette();
        for (int index = 0; index < storage.getSize(); ++index) {
            this.idFor(palette.valueFor(storage.get(index)));
        }
    }

    private <T> void packStorage(PalettedContainer.Data<T> data, int bits) {
        BitStorage storage = data.storage();
        Palette<T> palette = data.palette();
        int valuesPerLong = BITS_PER_LONG / bits;
        this.packedLength = (storage.getSize() + valuesPerLong - 1) / valuesPerLong;
        this.ensurePackedCapacity(this.packedLength);
        Arrays.fill(this.packed, 0, this.packedLength, 0L);
        for (int index = 0; index < storage.getSize(); ++index) {
            int serializedId = this.find(palette.valueFor(storage.get(index)));
            int wordIndex = index / valuesPerLong;
            int bitIndex = index % valuesPerLong * bits;
            this.packed[wordIndex] |= (long) serializedId << bitIndex;
        }
    }

    private int idFor(Object value) {
        this.ensureTable();
        int existing = this.find(value);
        if (existing >= 0) {
            return existing;
        }
        if (this.paletteSize + 1 > this.tableKeys.length / TABLE_LOAD_DIVISOR) {
            this.growTable();
        }
        this.ensurePaletteCapacity(this.paletteSize + 1);
        int id = this.paletteSize++;
        this.paletteEntries[id] = value;
        this.insert(value, id);
        return id;
    }

    private int find(Object value) {
        if (this.tableKeys == null) {
            return -1;
        }
        int mask = this.tableKeys.length - 1;
        int index = identityHash(value) & mask;
        while (this.tableKeys[index] != null) {
            if (this.tableKeys[index] == value) {
                return this.tableValues[index];
            }
            index = index + 1 & mask;
        }
        return -1;
    }

    private void insert(Object value, int id) {
        int mask = this.tableKeys.length - 1;
        int index = identityHash(value) & mask;
        while (this.tableKeys[index] != null) {
            index = index + 1 & mask;
        }
        this.tableKeys[index] = value;
        this.tableValues[index] = id;
    }

    private void growTable() {
        this.tableKeys = new Object[this.tableKeys.length * 2];
        this.tableValues = new int[this.tableKeys.length];
        for (int id = 0; id < this.paletteSize; ++id) {
            this.insert(this.paletteEntries[id], id);
        }
    }

    private void ensureTable() {
        if (this.tableKeys == null) {
            this.tableKeys = new Object[INITIAL_TABLE_CAPACITY];
            this.tableValues = new int[INITIAL_TABLE_CAPACITY];
        }
    }

    private void ensurePaletteCapacity(int required) {
        if (this.paletteEntries == null) {
            this.paletteEntries = new Object[INITIAL_PALETTE_CAPACITY];
        } else if (required > this.paletteEntries.length) {
            this.paletteEntries = Arrays.copyOf(this.paletteEntries, this.paletteEntries.length * 2);
        }
    }

    private void ensurePackedCapacity(int required) {
        if (this.packed == null || required > this.packed.length) {
            this.packed = new long[required];
        }
    }

    private static int ceilLog2(int value) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(value - 1);
    }

    private static int identityHash(Object value) {
        int hash = System.identityHashCode(value);
        return hash ^ hash >>> 16;
    }
}
