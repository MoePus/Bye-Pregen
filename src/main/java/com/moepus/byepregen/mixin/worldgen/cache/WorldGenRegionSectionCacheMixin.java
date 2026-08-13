package com.moepus.byepregen.mixin.worldgen.cache;

import com.moepus.byepregen.Feature.WorldGenRegionSectionCache;

import javax.annotation.Nullable;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = WorldGenRegion.class, remap = false)
public abstract class WorldGenRegionSectionCacheMixin implements WorldGenRegionSectionCache {
    @Unique
    private long bpg$chunkKey;
    @Unique
    private boolean bpg$hasChunkKey;
    @Unique
    private ChunkAccess bpg$chunk;


    @Shadow
    @Nullable
    public abstract ChunkAccess getChunk(int sectionX, int sectionZ, ChunkStatus status, boolean load);

    @Override
    public ChunkAccess bpg$getCachedChunk(int sectionX, int sectionZ) {
        long chunkKey = ChunkPos.asLong(sectionX, sectionZ);
        if (!this.bpg$hasChunkKey || this.bpg$chunkKey != chunkKey) {
            this.bpg$chunk = this.getChunk(sectionX, sectionZ, ChunkStatus.EMPTY, true);
            this.bpg$chunkKey = chunkKey;
            this.bpg$hasChunkKey = true;
        }
        return this.bpg$chunk;
    }
}
