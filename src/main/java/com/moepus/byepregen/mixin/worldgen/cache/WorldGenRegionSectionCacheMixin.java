package com.moepus.byepregen.mixin.worldgen.cache;

import com.moepus.byepregen.worldgen.feature.WorldGenRegionSectionCache;

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
    private long byepregen$chunkKey;
    @Unique
    private boolean byepregen$hasChunkKey;
    @Unique
    private ChunkAccess byepregen$chunk;


    @Shadow
    @Nullable
    public abstract ChunkAccess getChunk(int sectionX, int sectionZ, ChunkStatus status, boolean load);

    @Override
    public ChunkAccess byepregen$getCachedChunk(int sectionX, int sectionZ) {
        long chunkKey = ChunkPos.asLong(sectionX, sectionZ);
        if (!this.byepregen$hasChunkKey || this.byepregen$chunkKey != chunkKey) {
            this.byepregen$chunk = this.getChunk(sectionX, sectionZ, ChunkStatus.EMPTY, true);
            this.byepregen$chunkKey = chunkKey;
            this.byepregen$hasChunkKey = true;
        }
        return this.byepregen$chunk;
    }
}
