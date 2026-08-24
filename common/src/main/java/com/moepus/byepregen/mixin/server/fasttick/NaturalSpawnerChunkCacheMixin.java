package com.moepus.byepregen.mixin.server.fasttick;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.mixin.accessor.server.tick.ServerChunkCacheTickAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@MixinGate(config = ConfigFlag.FAST_CHUNK_TICKING)
@Mixin(value = NaturalSpawner.class, remap = false)
public abstract class NaturalSpawnerChunkCacheMixin {
    @InjectLite(method = "spawnForChunk", at = @At("HEAD"))
    private static void byepregen$seedSpawnChunkCache(ServerLevel level, LevelChunk chunk) {
        ServerChunkCacheTickAccessor cache = (ServerChunkCacheTickAccessor) level.getChunkSource();
        long chunkPos = chunk.getPos().pack();
        cache.byepregen$storeInCache(chunkPos, chunk, ChunkStatus.BIOMES);
        cache.byepregen$storeInCache(chunkPos, chunk, ChunkStatus.FULL);
    }
}
