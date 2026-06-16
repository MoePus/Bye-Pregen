package com.moepus.byepregen;

import net.minecraft.world.level.chunk.LevelChunkSection;

public interface WorldGenRegionSectionCache {
    LevelChunkSection bpg$getCachedSection(int sectionX, int sectionIndex, int sectionZ);
}
