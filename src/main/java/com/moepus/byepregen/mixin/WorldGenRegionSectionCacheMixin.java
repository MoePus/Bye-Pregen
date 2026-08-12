package com.moepus.byepregen.mixin;

import com.moepus.byepregen.Feature.WorldGenRegionSectionCache;

import javax.annotation.Nullable;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = WorldGenRegion.class, remap = false)
public abstract class WorldGenRegionSectionCacheMixin implements WorldGenRegionSectionCache {
    @Unique
    private long bpg$chunkKey = Long.MIN_VALUE;
    @Unique
    private ChunkAccess bpg$chunk;


    @Shadow
    @Nullable
    public abstract ChunkAccess getChunk(int sectionX, int sectionZ, ChunkStatus status, boolean load);

    @Override
    public LevelChunkSection bpg$getCachedSection(int sectionX, int sectionIndex, int sectionZ) {
        long chunkKey = ChunkPos.asLong(sectionX, sectionZ);
        if (this.bpg$chunkKey != chunkKey) {
            this.bpg$chunk = this.getChunk(sectionX, sectionZ, ChunkStatus.EMPTY, true);
            this.bpg$chunkKey = chunkKey;
        }
        return this.bpg$chunk == null ? null : this.bpg$chunk.getSection(sectionIndex);
    }
}
