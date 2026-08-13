package com.moepus.byepregen.palette.arena.codec;

import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;

import static com.moepus.byepregen.palette.arena.Layout.*;

import java.util.Arrays;

final class PaletteScratch {
    private static final int INITIAL_PALETTE_CAPACITY = 64;
    private static final int INITIAL_LOOKUP_CAPACITY = 128;
    private static final int HASH_MULTIPLIER = 0x9E3779B9;

    private int[] lookupKeys;
    private int[] lookupValues;
    private int[] paletteRawIds = new int[INITIAL_PALETTE_CAPACITY];
    private byte[] pageLocalIds;
    private int paletteSize;

    int size() {
        return this.paletteSize;
    }

    int rawId(int localId) {
        return this.paletteRawIds[localId];
    }

    byte[] pageLocalIds() {
        return this.pageLocalIds;
    }

    void collectRawIds(ArenaBlockStatePalettedContainer container) {
        if (container.hasDenseIds()) {
            for (int rawId : container.denseRawIdCounts().keySet()) {
                this.localId(Math.max(rawId, 0));
            }
            return;
        }

        container.forEachRawId(this::addRawId);
    }

    void collectPagePalette(ArenaBlockStatePalettedContainer container) {
        this.ensurePageLocalIds();
        for (int page = 0; page < PAGE_COUNT; ++page) {
            this.collectPage(container, page);
        }
    }

    void collectPagePaletteRawIds(ArenaBlockStatePalettedContainer container) {
        for (int page = 0; page < PAGE_COUNT; ++page) {
            int base = container.arenaPageBase(page);
            int mask = container.arenaLivePaletteMask(base);
            while (mask != 0) {
                int paletteIndex = Integer.numberOfTrailingZeros(mask);
                this.addPaletteRawIdWithoutLookup(container.arenaPaletteRawId(base, paletteIndex));
                mask &= mask - 1;
            }
        }
    }

    int localIdFor(int rawId) {
        this.ensureLookup();
        int slot = this.findSlot(Math.max(rawId, 0));
        int marker = this.lookupValues[slot];
        return marker == 0 ? 0 : marker - 1;
    }

    void clear() {
        if (this.lookupValues != null) {
            Arrays.fill(this.lookupValues, 0);
        }
        if (this.pageLocalIds != null) {
            Arrays.fill(this.pageLocalIds, (byte) 0);
        }
        this.paletteSize = 0;
    }

    private void collectPage(ArenaBlockStatePalettedContainer container, int page) {
        int base = container.arenaPageBase(page);
        int mask = container.arenaLivePaletteMask(base);
        int offset = page * PAGE_PALETTE_SIZE;
        while (mask != 0) {
            int paletteIndex = Integer.numberOfTrailingZeros(mask);
            int rawId = container.arenaPaletteRawId(base, paletteIndex);
            this.pageLocalIds[offset + paletteIndex] = (byte) this.addPaletteRawIdWithoutLookup(rawId);
            mask &= mask - 1;
        }
    }

    private void addRawId(int sectionIndex, int rawId) {
        this.localId(Math.max(rawId, 0));
    }

    private int addPaletteRawIdWithoutLookup(int rawId) {
        rawId = Math.max(rawId, 0);
        for (int i = 0; i < this.paletteSize; ++i) {
            if (this.paletteRawIds[i] == rawId) {
                return i;
            }
        }
        int localId = this.paletteSize++;
        this.ensurePaletteCapacity(this.paletteSize);
        this.paletteRawIds[localId] = rawId;
        return localId;
    }

    private int localId(int rawId) {
        this.ensureLookup();
        int slot = this.findSlot(rawId);
        int marker = this.lookupValues[slot];
        if (marker != 0) {
            return marker - 1;
        }

        int localId = this.paletteSize;
        this.ensurePaletteCapacity(localId + 1);
        if ((localId + 1) * 2 > this.lookupKeys.length) {
            this.growLookup();
            slot = this.findSlot(rawId);
        }

        this.paletteSize = localId + 1;
        this.lookupKeys[slot] = rawId;
        this.lookupValues[slot] = localId + 1;
        this.paletteRawIds[localId] = rawId;
        return localId;
    }

    private void ensureLookup() {
        if (this.lookupKeys != null) {
            return;
        }
        this.lookupKeys = new int[INITIAL_LOOKUP_CAPACITY];
        this.lookupValues = new int[INITIAL_LOOKUP_CAPACITY];
    }

    private void ensurePageLocalIds() {
        if (this.pageLocalIds == null) {
            this.pageLocalIds = new byte[PAGE_COUNT * PAGE_PALETTE_SIZE];
        }
    }

    private void ensurePaletteCapacity(int required) {
        if (this.paletteRawIds.length >= required) {
            return;
        }

        int newLength = this.paletteRawIds.length;
        do {
            newLength *= 2;
        } while (newLength < required);
        this.paletteRawIds = Arrays.copyOf(this.paletteRawIds, newLength);
    }

    private void growLookup() {
        this.lookupKeys = new int[this.lookupKeys.length * 2];
        this.lookupValues = new int[this.lookupValues.length * 2];
        for (int i = 0; i < this.paletteSize; ++i) {
            int slot = this.findSlot(this.paletteRawIds[i]);
            this.lookupKeys[slot] = this.paletteRawIds[i];
            this.lookupValues[slot] = i + 1;
        }
    }

    private int findSlot(int rawId) {
        int slot = mix(rawId) & (this.lookupKeys.length - 1);
        while (this.lookupValues[slot] != 0 && this.lookupKeys[slot] != rawId) {
            slot = (slot + 1) & (this.lookupKeys.length - 1);
        }
        return slot;
    }

    private static int mix(int value) {
        int hash = value * HASH_MULTIPLIER;
        return hash ^ (hash >>> 16);
    }
}
