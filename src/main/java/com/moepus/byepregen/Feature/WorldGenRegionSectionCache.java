package com.moepus.byepregen.Feature;

import javax.annotation.Nullable;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

public interface WorldGenRegionSectionCache {
    @Nullable
    ChunkAccess byepregen$getCachedChunk(int sectionX, int sectionZ);

    default LevelChunkSection byepregen$getCachedSection(int sectionX, int sectionIndex, int sectionZ) {
        ChunkAccess chunk = this.byepregen$getCachedChunk(sectionX, sectionZ);
        return chunk == null ? null : chunk.getSection(sectionIndex);
    }
}
