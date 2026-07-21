package com.moepus.byepregen.PaletteContainer.ArenaPelette;

import static com.moepus.byepregen.PaletteContainer.ArenaPelette.Layout.*;

import java.util.Arrays;

/** Builds one complete 16x16x4 Arena page before installing it into a section. */
public final class ArenaPageBuildBuffer {
    private final int[] packedIndexes = new int[INDEX_WORDS_PER_PAGE];
    private final int[] paletteRawIds = new int[PAGE_PALETTE_SIZE];
    private int paletteSize;
    private int[] denseRawIds;
    private boolean dense;

    public ArenaPageBuildBuffer() {
        this.reset(ArenaBlockStatePalettedContainer.AIR_RAW_ID);
    }

    public void reset(int defaultRawId) {
        Arrays.fill(this.packedIndexes, 0);
        this.paletteRawIds[0] = sanitizeRawId(defaultRawId);
        this.paletteSize = 1;
        this.dense = false;
    }

    public void setRawId(int localIndex, int rawId) {
        rawId = sanitizeRawId(rawId);
        if (this.dense) {
            this.denseRawIds[localIndex] = rawId;
            return;
        }

        int paletteIndex = this.findPaletteIndex(rawId);
        if (paletteIndex < 0 && this.paletteSize < PAGE_PALETTE_SIZE) {
            paletteIndex = this.paletteSize;
            this.paletteRawIds[this.paletteSize++] = rawId;
        }
        if (paletteIndex < 0) {
            this.promoteToDense();
            this.denseRawIds[localIndex] = rawId;
            return;
        }

        setLocalPaletteIndex(this.packedIndexes, 0, localIndex, paletteIndex);
    }

    public boolean isUniformRawId(int rawId) {
        return !this.dense && this.paletteSize == 1 && this.paletteRawIds[0] == rawId;
    }

    boolean hasDenseIds() {
        return this.dense;
    }

    void writeToArena(int[] arena, int base) {
        Arrays.fill(arena, base, base + PAGE_STRIDE, 0);
        System.arraycopy(this.packedIndexes, 0, arena, base, INDEX_WORDS_PER_PAGE);
        arena[base + PAGE_DEFAULT_OFFSET] = this.paletteRawIds[0];
        for (int i = 1; i < this.paletteSize; ++i) {
            arena[base + EXTRA_PALETTE_OFFSET + i - 1] = this.paletteRawIds[i] + 1;
        }
    }

    void writeToDense(int[] denseIds, int page) {
        int targetBase = page * PAGE_SIZE;
        if (this.dense) {
            System.arraycopy(this.denseRawIds, 0, denseIds, targetBase, PAGE_SIZE);
            return;
        }

        for (int local = 0; local < PAGE_SIZE; ++local) {
            int paletteIndex = localPaletteIndex(this.packedIndexes, 0, local);
            denseIds[targetBase + local] = this.paletteRawIds[paletteIndex];
        }
    }

    private int findPaletteIndex(int rawId) {
        for (int i = 0; i < this.paletteSize; ++i) {
            if (this.paletteRawIds[i] == rawId) {
                return i;
            }
        }
        return -1;
    }

    private void promoteToDense() {
        if (this.denseRawIds == null) {
            this.denseRawIds = new int[PAGE_SIZE];
        }
        for (int local = 0; local < PAGE_SIZE; ++local) {
            int paletteIndex = localPaletteIndex(this.packedIndexes, 0, local);
            this.denseRawIds[local] = this.paletteRawIds[paletteIndex];
        }
        this.dense = true;
    }

    private static int sanitizeRawId(int rawId) {
        return rawId < 0 ? ArenaBlockStatePalettedContainer.AIR_RAW_ID : rawId;
    }
}
