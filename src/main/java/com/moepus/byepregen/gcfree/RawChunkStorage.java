package com.moepus.byepregen.gcfree;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;

public interface RawChunkStorage {
    CompletableFuture<Void> byepregen$writeRawChunkData(ChunkPos pos, RawChunkData data);
}
