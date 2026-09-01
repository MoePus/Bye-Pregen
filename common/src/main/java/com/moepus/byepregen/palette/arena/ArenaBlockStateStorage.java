package com.moepus.byepregen.palette.arena;

import static com.moepus.byepregen.palette.arena.Layout.*;

import com.moepus.byepregen.UnsafeIntArrayAccess;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class ArenaBlockStateStorage {
    private int uniformRawId;
    private BlockState uniformState;
    private int[] arena;
    private int[] denseIds;
    private Int2IntOpenHashMap denseRawIdCounts;

    ArenaBlockStateStorage(int uniformRawId, BlockState uniformState) {
        this.uniformRawId = uniformRawId;
        this.uniformState = uniformState;
    }

    void releaseRawIds() {
        this.setUniformSection(ArenaBlockStatePalettedContainer.AIR_RAW_ID);
    }

    void copyFrom(ArenaBlockStateStorage source) {
        this.uniformRawId = source.uniformRawId;
        this.uniformState = source.uniformState;
        this.arena = source.arena == null ? null : source.arena.clone();
        this.denseIds = source.denseIds == null ? null : source.denseIds.clone();
        // Counts are derived from denseIds and may be changing while a renderer snapshots the section.
        this.denseRawIdCounts = null;
    }

    BlockState stateAt(int x, int y, int z) {
        int[] dense = this.denseIds;
        if (dense != null) {
            return Block.stateById(UnsafeIntArrayAccess.get(dense, localIndex(x, y, z)));
        }
        int[] pageArena = this.arena;
        if (pageArena == null) {
            return this.uniformState;
        }
        int page = y >>> 2;
        int local = ((y & 3) << 8) | (z << 4) | x;
        int base = pageBase(page);
        int paletteIndex = localPaletteIndex(pageArena, base, local);
        return Block.stateById(ArenaPaletteConversions.rawIdForPaletteIndex(
                pageArena, base, paletteIndex));
    }

    BlockState stateAt(int index) {
        int[] dense = this.denseIds;
        if (dense != null) {
            return Block.stateById(UnsafeIntArrayAccess.get(dense, index));
        }
        int[] pageArena = this.arena;
        if (pageArena == null) {
            return this.uniformState;
        }
        int page = pageIndexFromSectionIndex(index);
        int local = pageLocalIndexFromSectionIndex(index);
        int base = pageBase(page);
        int paletteIndex = localPaletteIndex(pageArena, base, local);
        return Block.stateById(ArenaPaletteConversions.rawIdForPaletteIndex(
                pageArena, base, paletteIndex));
    }

    int rawIdAt(int index) {
        int[] dense = this.denseIds;
        if (dense != null) {
            return UnsafeIntArrayAccess.get(dense, index);
        }
        int[] pageArena = this.arena;
        if (pageArena == null) {
            return this.uniformRawId;
        }
        int page = pageIndexFromSectionIndex(index);
        int local = pageLocalIndexFromSectionIndex(index);
        int base = pageBase(page);
        return ArenaPaletteConversions.rawIdForPaletteIndex(
                pageArena, base, localPaletteIndex(pageArena, base, local));
    }

    void setRawId(int index, int rawId) {
        int[] dense = this.denseIds;
        if (dense != null) {
            this.writeDense(index, rawId, dense);
            return;
        }

        int[] pageArena = this.arena;
        if (pageArena == null) {
            if (rawId == this.uniformRawId) {
                return;
            }
            pageArena = this.ensureArena();
        }

        int page = pageIndexFromSectionIndex(index);
        int local = pageLocalIndexFromSectionIndex(index);
        int base = pageBase(page);
        int oldPaletteIndex = localPaletteIndex(pageArena, base, local);
        int oldRawId = ArenaPaletteConversions.rawIdForPaletteIndex(pageArena, base, oldPaletteIndex);
        if (oldRawId == rawId) {
            return;
        }

        int paletteIndex = ArenaPaletteConversions.findPaletteIndex(pageArena, base, rawId);
        if (paletteIndex < 0) {
            paletteIndex = ArenaPaletteConversions.appendPaletteIndex(pageArena, base, rawId);
        }
        if (paletteIndex < 0 && ArenaPaletteConversions.tryReuseDeadSlot(
                pageArena, base, local, oldPaletteIndex, rawId)) {
            return;
        }
        if (paletteIndex < 0) {
            this.promoteToDense();
            UnsafeIntArrayAccess.set(this.denseIds, index, rawId);
            return;
        }
        setLocalPaletteIndex(pageArena, base, local, paletteIndex);
    }

    void batchWriteRawId(int page, int pageLocalIndex, int rawId, int airRawId) {
        if (rawId < 0 || rawId == airRawId) {
            return;
        }
        int sectionIndex = sectionIndex(page, pageLocalIndex);
        if (this.denseIds != null) {
            UnsafeIntArrayAccess.set(this.denseIds, sectionIndex, rawId);
            return;
        }
        int[] pageArena = this.ensureArena();
        int base = pageBase(page);
        int paletteIndex = ArenaPaletteConversions.findOrAppendFreshPaletteIndex(pageArena, base, rawId);
        if (paletteIndex < 0) {
            this.promoteToDense();
            UnsafeIntArrayAccess.set(this.denseIds, sectionIndex, rawId);
            return;
        }
        setLocalPaletteIndex(pageArena, base, pageLocalIndex, paletteIndex);
    }

    int[] ensureArena() {
        if (this.arena == null) {
            this.arena = ArenaPaletteConversions.createArena(this.uniformRawId);
        }
        return this.arena;
    }

    void promoteToDense() {
        if (this.denseIds != null) {
            return;
        }
        this.denseIds = ArenaPaletteConversions.toDense(this.arena, this.uniformRawId);
        this.arena = null;
        this.denseRawIdCounts = null;
    }

    void tryPromoteFullUniformSection() {
        int rawId = ArenaPaletteConversions.uniformRawId(this.arena);
        if (rawId >= 0 && this.denseIds == null) {
            this.setUniformSection(rawId);
        }
    }

    void setUniformSection(int rawId) {
        this.uniformRawId = rawId;
        this.uniformState = Block.stateById(rawId);
        this.arena = null;
        this.denseIds = null;
        this.denseRawIdCounts = null;
    }

    void unpack(Object[] values) {
        ArenaPaletteConversions.unpack(values, this.uniformState, this.arena, this.denseIds);
    }

    boolean isUniform() {
        return this.arena == null && this.denseIds == null;
    }

    boolean isFreshAirForWorldgen(int airRawId, BlockState airState) {
        return this.isUniform()
                && this.denseRawIdCounts == null
                && this.uniformRawId == airRawId
                && this.uniformState == airState;
    }

    int uniformRawId() {
        return this.uniformRawId;
    }

    boolean hasPagePalettes() {
        return this.arena != null && this.denseIds == null;
    }

    boolean hasDenseIds() {
        return this.denseIds != null;
    }

    Int2IntOpenHashMap denseRawIdCounts() {
        Int2IntOpenHashMap counts = this.denseRawIdCounts;
        if (counts != null) {
            return counts;
        }

        int[] dense = this.denseIds;
        if (dense == null) {
            return null;
        }

        counts = new Int2IntOpenHashMap();
        for (int i = 0; i < SECTION_SIZE; ++i) {
            counts.addTo(UnsafeIntArrayAccess.get(dense, i), 1);
        }
        this.denseRawIdCounts = counts;
        return counts;
    }

    int arenaPageBase(int page) {
        return pageBase(page);
    }

    int arenaLivePaletteMask(int base) {
        return pageLivePaletteMask(this.arena, base);
    }

    int arenaPaletteRawId(int base, int paletteIndex) {
        return ArenaPaletteConversions.rawIdForPaletteIndex(this.arena, base, paletteIndex);
    }

    int arenaPaletteWord(int base, int wordIndex) {
        return this.arena[base + wordIndex];
    }

    boolean isUniformRawId(int rawId) {
        return this.arena == null && rawId == this.uniformRawId;
    }

    private void writeDense(int index, int rawId, int[] dense) {
        int oldRawId = UnsafeIntArrayAccess.get(dense, index);
        if (oldRawId == rawId) {
            return;
        }
        UnsafeIntArrayAccess.set(dense, index, rawId);
        Int2IntOpenHashMap counts = this.denseRawIdCounts;
        if (counts == null) {
            return;
        }
        int oldCount = counts.get(oldRawId);
        if (oldCount <= 1) {
            counts.remove(oldRawId);
        } else {
            counts.put(oldRawId, oldCount - 1);
        }
        counts.addTo(rawId, 1);
    }
}
