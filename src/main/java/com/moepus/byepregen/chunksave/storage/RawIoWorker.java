package com.moepus.byepregen.chunksave.storage;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;

public interface RawIoWorker {
    CompletableFuture<Void> byepregen$storeRawChunkData(ChunkPos pos, RawChunkData data);
}
