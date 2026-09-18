package com.moepus.byepregen.worldgen.terrain;

import com.moepus.byepregen.mixin.accessor.arena.LevelChunkSectionAccessor;
import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.palette.arena.ArenaFreshSectionWriter;
import net.minecraft.world.level.chunk.LevelChunkSection;

final class TerrainSection {
    private final LevelChunkSection section;
    private final ArenaFreshSectionWriter writer;
    private long counts;

    TerrainSection(LevelChunkSection section, int defaultRawId) {
        this.section = section;
        this.writer = ((ArenaBlockStatePalettedContainer) section.getStates()).beginTerrainWrite(defaultRawId);
    }

    ArenaFreshSectionWriter.Page page(int index) { return this.writer.page(index); }

    void add(TerrainBlock block) {
        // Each location is written once. Four unsigned 16-bit lanes cannot carry
        // into each other because each count is at most the section's 4096 blocks.
        this.counts += block.counts();
    }

    void finish() {
        var access = (LevelChunkSectionAccessor) (Object) this.section;
        access.byepregen$setNonEmptyBlockCount((short) this.counts);
        access.byepregen$setFluidCount((short) (this.counts >>> TerrainBlock.FLUID_SHIFT));
        access.byepregen$setTickingBlockCount((short) (this.counts >>> TerrainBlock.TICKING_BLOCK_SHIFT));
        access.byepregen$setTickingFluidCount((short) (this.counts >>> TerrainBlock.TICKING_FLUID_SHIFT));
    }
}
