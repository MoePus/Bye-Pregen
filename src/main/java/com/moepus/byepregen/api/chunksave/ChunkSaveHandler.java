package com.moepus.byepregen.api.chunksave;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Writes one mod's additional root fields into ByePregen's raw chunk NBT stream. */
@FunctionalInterface
public interface ChunkSaveHandler {
    void write(ServerLevel level, ChunkAccess chunk, ChunkSaveWriter writer);
}
