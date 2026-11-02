package com.moepus.byepregen.api.chunksave;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Writes one mod's additional root fields into ByePregen's raw chunk NBT stream.
 *
 * <p>Handlers run in the middle of a chunk save and are not isolated from each other: an
 * exception aborts the save instead of being caught, so implementations must not throw.</p>
 *
 * <p>Handlers are only called on the raw GC-free save path; see
 * {@link ChunkSaveHandlerRegistry#isActive()} for whether that path is active at all.</p>
 */
@FunctionalInterface
public interface ChunkSaveHandler {
    void write(ServerLevel level, ChunkAccess chunk, ChunkSaveWriter writer);
}
