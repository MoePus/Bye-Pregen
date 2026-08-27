package com.moepus.byepregen.mixin.accessor.server.tick;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@MixinGate(config = ConfigFlag.FAST_CHUNK_TICKING)
@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheTickAccessor {
    @Invoker("storeInCache")
    void byepregen$storeInCache(long chunkPos, ChunkAccess chunk, ChunkStatus status);
}
