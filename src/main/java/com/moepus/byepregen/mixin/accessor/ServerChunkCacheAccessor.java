package com.moepus.byepregen.mixin.accessor;

import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;

@Mixin(value = ServerChunkCache.class, remap = false)
public interface ServerChunkCacheAccessor {
    @Invoker("getChunkFutureMainThread")
    CompletableFuture<ChunkResult<ChunkAccess>> byepregen$getChunkFutureMainThread(
            int chunkX, int chunkZ, ChunkStatus status, boolean requireChunk);
}
