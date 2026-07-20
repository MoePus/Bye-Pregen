package com.moepus.byepregen.Feature;

import net.minecraft.world.level.chunk.LevelChunkSection;

public interface WorldGenRegionSectionCache {
    LevelChunkSection bpg$getCachedSection(int sectionX, int sectionIndex, int sectionZ);
}
