package com.moepus.byepregen.yalight;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;

public final class YABlockLightEngine extends BlockLightEngine implements YALightLayerEngine {
    final LightChunkGetter chunkGetter;
    final BlockGetter levelReader;
    final YALightStorage storage;
    final YAChunkRunCache runCache = new YAChunkRunCache();
    final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
    private final YALightQueue lightQueue = new YALightQueue();
    private final YADLongQueue decreaseQueue = new YADLongQueue();
    private final YADLongQueue increaseQueue = new YADLongQueue();
    private final YALightBlockAccess blocks;

    public YABlockLightEngine(LightChunkGetter chunkGetter) {
        super(chunkGetter, null);
        this.chunkGetter = chunkGetter;
        this.levelReader = chunkGetter.getLevel();
        this.storage = new YALightStorage(chunkGetter, chunkGetter.getLevel(), LightLayer.BLOCK);
        this.blocks = new YALightBlockAccess(this.runCache, chunkGetter, this.levelReader, this.mutablePos);
    }

    @Override
    public LightChunkGetter chunkGetter() {
        return this.chunkGetter;
    }

    @Override
    public LightLayer lightLayer() {
        return LightLayer.BLOCK;
    }

    @Override
    public YALightStorage storage() {
        return this.storage;
    }

    @Override
    public YAChunkRunCache runCache() {
        return this.runCache;
    }

    @Override
    public YALightQueue lightQueue() {
        return this.lightQueue;
    }

    @Override
    public YADLongQueue decreaseQueue() {
        return this.decreaseQueue;
    }

    @Override
    public YADLongQueue increaseQueue() {
        return this.increaseQueue;
    }

    @Override
    public YALightBlockAccess blockAccess() {
        return this.blocks;
    }

    @Override
    public int sourceLight(long pos, int block) {
        if (!this.blocks.isSlow(block)) {
            return 0;
        }
        BlockState state = this.blocks.toState(block);
        return state.getLightEmission(this.levelReader, this.mutablePos.set(pos));
    }

    @Override
    public void checkBlock(BlockPos pos) {
        YALightLayerEngine.super.checkBlock(pos);
    }

    @Override
    public boolean hasLightWork() {
        return YALightLayerEngine.super.hasLightWork();
    }

    @Override
    public int runLightUpdates() {
        return YALightLayerEngine.super.runLightUpdates();
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        YALightLayerEngine.super.updateSectionStatus(pos, isEmpty);
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean lightEnabled) {
        YALightLayerEngine.super.setLightEnabled(pos, lightEnabled);
    }

    @Override
    public void propagateLightSources(ChunkPos pos) {
        YALightLayerEngine.super.propagateLightSources(pos);
    }

    @Override
    public DataLayer getDataLayerData(SectionPos pos) {
        return YALightLayerEngine.super.getDataLayerData(pos);
    }

    @Override
    public int getLightValue(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        YAChunkLightData data = this.storage.existingData(this.storage.chunkAccess(chunkX, chunkZ));
        return this.getVisibleLightValue(pos, data);
    }

    int getVisibleLightValue(BlockPos pos, YAChunkLightData data) {
        YANibbleArray[] sections = data == null
                ? YAVisibleLightReader.EMPTY_SECTIONS
                : data.visibleSections();
        int sectionIndex = this.storage.sectionIndex(pos.getY() >> 4);
        return YAVisibleLightReader.blockLight(sections, sectionIndex, pos);
    }

    @Override
    public void queueSectionData(long sectionPos, @Nullable DataLayer data) {
        this.queueSectionData(SectionPos.of(sectionPos), data);
    }

    @Override
    public void retainData(ChunkPos pos, boolean retainData) {
        // YA light data is chunk-owned.
    }

    @Override
    public String getDebugData(long sectionPos) {
        return this.getDebugData(SectionPos.of(sectionPos));
    }

    @Override
    public LayerLightSectionStorage.SectionType getDebugSectionType(long sectionPos) {
        return this.lightOnInSection(SectionPos.of(sectionPos))
                ? LayerLightSectionStorage.SectionType.LIGHT_AND_DATA
                : LayerLightSectionStorage.SectionType.EMPTY;
    }

    @Override
    public void checkBlockInternal(long pos) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        if (!this.canUseSection(x >> 4, y >> 4, z >> 4)) {
            return;
        }

        int block = this.blocks.blockAt(x, y, z);
        int emitted = 0;
        // Fast classes are guaranteed non-emissive; only slow blocks need a BlockState source query.
        if (this.blocks.isSlow(block)) {
            BlockState state = this.blocks.toState(block);
            emitted = state.getLightEmission(this.levelReader, this.mutablePos.set(pos));
        }
        int current = this.getCachedUpdatingLight(x, y, z);
        if (current > emitted) {
            this.setCachedUpdatingLight(x, y, z, 0);
            this.enqueueDecrease(pos, current, YALightMath.ALL_DIRECTIONS);
        } else {
            this.enqueueDecrease(pos, 1, YALightMath.ALL_DIRECTIONS);
        }
        if (emitted > 0) {
            this.enqueueIncrease(pos, emitted, YALightMath.ALL_DIRECTIONS, YALightMath.FLAG_WRITE_LEVEL);
        }
    }

    @Override
    public void propagateLightSourcesInternal(ChunkAccess chunk, boolean fresh) {
        chunk.findBlockLightSources((pos, state) -> {
            int emitted = state.getLightEmission(this.levelReader, pos);
            if (emitted <= 0 || emitted <= this.getCachedUpdatingLight(pos.getX(), pos.getY(), pos.getZ())) {
                return;
            }
            this.setCachedUpdatingLight(pos.getX(), pos.getY(), pos.getZ(), emitted);
            this.enqueueIncrease(pos.asLong(), emitted, YALightMath.ALL_DIRECTIONS, 0L);
        });
    }

    @Override
    public void propagateIncrease(long pos, long meta) {
        int level = YALightMath.level(meta);
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        int stored = this.getCachedUpdatingLight(x, y, z);
        if (stored != level) {
            boolean canWrite = (meta & (YALightMath.FLAG_RECHECK | YALightMath.FLAG_WRITE_LEVEL))
                    == YALightMath.FLAG_WRITE_LEVEL && stored < level;
            if (!canWrite) {
                return;
            }
            this.setCachedUpdatingLight(x, y, z, level);
        }

        int fromBlock = 0;
        boolean fromBlockLoaded = false;
        int directions = YALightMath.directions(meta);
        while (directions != 0) {
            int directionIndex = Integer.numberOfTrailingZeros(directions);
            directions &= directions - 1;
            int toX = x + YALightMath.stepX(directionIndex);
            int toY = y + YALightMath.stepY(directionIndex);
            int toZ = z + YALightMath.stepZ(directionIndex);
            int current = this.getEnabledCachedUpdatingLight(toX, toY, toZ);
            if (current < 0 || current >= level - 1) {
                continue;
            }
            int toBlock = this.blocks.blockAt(toX, toY, toZ);
            if (this.blocks.isFull(toBlock)) {
                continue;
            }

            int target = this.blocks.attenuatedLevel(level, toX, toY, toZ, toBlock);
            if (target <= current) {
                continue;
            }
            if (!fromBlockLoaded) {
                fromBlock = this.blocks.blockAt(x, y, z);
                fromBlockLoaded = true;
            }
            if ((fromBlock | toBlock) != 0 && this.blocks.shapeOccludes(
                    x, y, z, fromBlock, toX, toY, toZ, toBlock, YALightMath.direction(directionIndex))) {
                continue;
            }
            this.setCachedUpdatingLight(toX, toY, toZ, target);
            if (target > 1) {
                this.enqueueIncrease(
                        BlockPos.asLong(toX, toY, toZ), target, YALightMath.withoutOpposite(directionIndex), 0L);
            }
        }
    }

    @Override
    public void propagateDecrease(long pos, long meta) {
        int level = YALightMath.level(meta);
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        int directions = YALightMath.directions(meta);
        while (directions != 0) {
            int directionIndex = Integer.numberOfTrailingZeros(directions);
            directions &= directions - 1;
            int toX = x + YALightMath.stepX(directionIndex);
            int toY = y + YALightMath.stepY(directionIndex);
            int toZ = z + YALightMath.stepZ(directionIndex);
            int current = this.getEnabledCachedUpdatingLight(toX, toY, toZ);
            if (current <= 0) {
                continue;
            }
            long toPos = BlockPos.asLong(toX, toY, toZ);
            int toBlock = this.blocks.blockAt(toX, toY, toZ);
            if (this.blocks.isFull(toBlock)) {
                this.setCachedUpdatingLight(toX, toY, toZ, 0);
                continue;
            }

            int target = this.blocks.attenuatedLevel(level, toX, toY, toZ, toBlock);
            if (current > target) {
                this.enqueueIncrease(
                        toPos, current, YALightMath.oppositeMask(directionIndex), YALightMath.FLAG_RECHECK);
                continue;
            }
            this.setCachedUpdatingLight(toX, toY, toZ, 0);
            if (this.blocks.isSlow(toBlock)) {
                int source = this.blocks.toState(toBlock).getLightEmission(
                        this.levelReader, this.mutablePos.set(toPos));
                if (source > 0) {
                    this.enqueueIncrease(toPos, source, YALightMath.ALL_DIRECTIONS, YALightMath.FLAG_WRITE_LEVEL);
                }
            }
            this.enqueueDecrease(toPos, current, YALightMath.withoutOpposite(directionIndex));
        }
    }

}
