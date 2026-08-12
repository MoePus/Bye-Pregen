package com.moepus.byepregen.Feature;

import javax.annotation.Nullable;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

public interface WorldGenRegionSectionCache {
    @Nullable
    ChunkAccess bpg$getCachedChunk(int sectionX, int sectionZ);

    default LevelChunkSection bpg$getCachedSection(int sectionX, int sectionIndex, int sectionZ) {
        ChunkAccess chunk = this.bpg$getCachedChunk(sectionX, sectionZ);
        return chunk == null ? null : chunk.getSection(sectionIndex);
    }
}
