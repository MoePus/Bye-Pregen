package com.moepus.byepregen.yalight.access;

import net.minecraft.world.level.chunk.ChunkAccess;

import javax.annotation.Nullable;

public interface YAImmediateChunkAccess {
    @Nullable
    ChunkAccess byepregen$getAnyChunkNow(int chunkX, int chunkZ);
}
