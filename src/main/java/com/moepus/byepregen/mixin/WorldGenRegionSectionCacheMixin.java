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
    private long bpg$sectionChunkKey;
    @Unique
    private int bpg$sectionIndex;
    @Unique
    private LevelChunkSection bpg$section;
    @Unique
    private boolean bpg$sectionCacheValid;

    @Shadow
    @Nullable
    public abstract ChunkAccess getChunk(int sectionX, int sectionZ, ChunkStatus status, boolean load);

    @Override
    public LevelChunkSection bpg$getCachedSection(int sectionX, int sectionIndex, int sectionZ) {
        long sectionChunkKey = ChunkPos.asLong(sectionX, sectionZ);
        if (this.bpg$sectionCacheValid
                && this.bpg$sectionChunkKey == sectionChunkKey
                && this.bpg$sectionIndex == sectionIndex) {
            return this.bpg$section;
        }

        ChunkAccess chunk = this.getChunk(sectionX, sectionZ, ChunkStatus.EMPTY, true);
        this.bpg$sectionChunkKey = sectionChunkKey;
        this.bpg$sectionIndex = sectionIndex;
        this.bpg$section = chunk == null ? null : chunk.getSection(sectionIndex);
        this.bpg$sectionCacheValid = true;
        return this.bpg$section;
    }
}
