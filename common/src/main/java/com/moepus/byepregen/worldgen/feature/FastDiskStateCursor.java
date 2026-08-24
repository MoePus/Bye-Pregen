package com.moepus.byepregen.worldgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class FastDiskStateCursor {
    private static final int MAX_CACHED_OFFSETS = 8;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState VOID_AIR = Blocks.VOID_AIR.defaultBlockState();

    private final WorldGenLevel level;
    private final WorldGenRegionSectionCache chunkCache;
    private final Vec3i[] cachedOffsets = new Vec3i[MAX_CACHED_OFFSETS];
    private final BlockState[] cachedStates = new BlockState[MAX_CACHED_OFFSETS];
    private ChunkAccess columnChunk;
    private int columnSectionX = Integer.MIN_VALUE;
    private int columnSectionZ = Integer.MIN_VALUE;
    private int columnSectionIndex = Integer.MIN_VALUE;
    private LevelChunkSection columnSection;
    private ChunkAccess otherChunk;
    private long otherChunkKey;
    private boolean hasOtherChunkKey;
    private int otherSectionIndex = Integer.MIN_VALUE;
    private LevelChunkSection otherSection;
    private int x;
    private int y;
    private int z;
    private int cachedOffsetCount;

    public FastDiskStateCursor(WorldGenLevel level, WorldGenRegionSectionCache chunkCache) {
        this.level = level;
        this.chunkCache = chunkCache;
    }

    public WorldGenLevel level() {
        return this.level;
    }

    public boolean selectColumn(int x, int z) {
        int sectionX = SectionPos.blockToSectionCoord(x);
        int sectionZ = SectionPos.blockToSectionCoord(z);
        if (sectionX != this.columnSectionX || sectionZ != this.columnSectionZ) {
            this.columnChunk = this.chunkCache.byepregen$getCachedChunk(sectionX, sectionZ);
            this.columnSectionX = sectionX;
            this.columnSectionZ = sectionZ;
            this.columnSectionIndex = Integer.MIN_VALUE;
            this.columnSection = null;
        }
        this.x = x;
        this.z = z;
        return this.columnChunk != null;
    }

    public void beginPosition(int y) {
        this.y = y;
        this.cachedOffsetCount = 0;
    }

    public BlockState getState(Vec3i offset) {
        for (int i = 0; i < this.cachedOffsetCount; i++) {
            if (offset.equals(this.cachedOffsets[i])) {
                return this.cachedStates[i];
            }
        }

        BlockState state = this.getState(this.x + offset.getX(), this.y + offset.getY(), this.z + offset.getZ());
        if (this.cachedOffsetCount < MAX_CACHED_OFFSETS) {
            this.cachedOffsets[this.cachedOffsetCount] = offset;
            this.cachedStates[this.cachedOffsetCount] = state;
            this.cachedOffsetCount++;
        }
        return state;
    }

    public BlockState getState(int x, int y, int z) {
        if (this.level.isOutsideBuildHeight(y)) {
            return VOID_AIR;
        }

        int sectionX = SectionPos.blockToSectionCoord(x);
        int sectionZ = SectionPos.blockToSectionCoord(z);
        int sectionIndex = this.level.getSectionIndex(y);
        LevelChunkSection section = this.getSection(sectionX, sectionIndex, sectionZ);
        if (section == null || section.hasOnlyAir()) {
            return AIR;
        }
        return section.getBlockState(x & 15, y & 15, z & 15);
    }

    public void markForPostprocessing(BlockPos pos) {
        this.columnChunk.markPosForPostprocessing(pos);
    }

    private LevelChunkSection getSection(int sectionX, int sectionIndex, int sectionZ) {
        if (sectionX == this.columnSectionX && sectionZ == this.columnSectionZ) {
            if (sectionIndex != this.columnSectionIndex) {
                this.columnSection = this.columnChunk.getSection(sectionIndex);
                this.columnSectionIndex = sectionIndex;
            }
            return this.columnSection;
        }
        return this.getOtherSection(sectionX, sectionIndex, sectionZ);
    }

    private LevelChunkSection getOtherSection(int sectionX, int sectionIndex, int sectionZ) {
        long chunkKey = ChunkPos.pack(sectionX, sectionZ);
        if (!this.hasOtherChunkKey || chunkKey != this.otherChunkKey) {
            this.otherChunk = this.chunkCache.byepregen$getCachedChunk(sectionX, sectionZ);
            this.otherChunkKey = chunkKey;
            this.hasOtherChunkKey = true;
            this.otherSectionIndex = Integer.MIN_VALUE;
            this.otherSection = null;
        }
        if (this.otherChunk != null && sectionIndex != this.otherSectionIndex) {
            this.otherSection = this.otherChunk.getSection(sectionIndex);
            this.otherSectionIndex = sectionIndex;
        }
        return this.otherSection;
    }
}
