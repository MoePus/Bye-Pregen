package com.moepus.byepregen.palette.arena.codec;

import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;

import static com.moepus.byepregen.palette.arena.Layout.*;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.Arrays;

final class PaletteScratch {
    private static final int INITIAL_PALETTE_CAPACITY = 64;
    private static final int INITIAL_LOOKUP_CAPACITY = 128;

    // raw ID to local ID + 1, so an absent key and local ID 0 stay distinguishable.
    private final Int2IntOpenHashMap lookup = new Int2IntOpenHashMap(INITIAL_LOOKUP_CAPACITY);
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
        int marker = this.lookup.get(Math.max(rawId, 0));
        return marker == 0 ? 0 : marker - 1;
    }

    void clear() {
        this.lookup.clear();
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
        int marker = this.lookup.get(rawId);
        if (marker != 0) {
            return marker - 1;
        }

        int localId = this.paletteSize;
        this.ensurePaletteCapacity(localId + 1);
        this.paletteSize = localId + 1;
        this.paletteRawIds[localId] = rawId;
        this.lookup.put(rawId, localId + 1);
        return localId;
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
}
