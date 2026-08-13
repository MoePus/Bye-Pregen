package com.moepus.byepregen.chunksave.storage;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;

public interface RawChunkStorage {
    CompletableFuture<Void> byepregen$writeRawChunkData(ChunkPos pos, RawChunkData data);
}
