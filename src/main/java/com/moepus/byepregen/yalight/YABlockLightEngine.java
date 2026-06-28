package com.moepus.byepregen.yalight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

public final class YABlockLightEngine extends YALightLayerEngine {
    private final YABlockRunCache runCache = new YABlockRunCache();
    private final YALightBlockAccess blocks;

    YABlockLightEngine(LightChunkGetter chunkGetter, LevelHeightAccessor level) {
        super(chunkGetter, level, LightLayer.BLOCK);
        this.blocks = new YALightBlockAccess(this.runCache, this.chunkGetter, this.levelReader, this.mutablePos);
    }

    @Override
    protected void clearRunCache() {
        this.runCache.clear();
    }

    @Override
    public int getLightValue(BlockPos pos) {
        int sectionY = pos.getY() >> 4;
        YANibbleArray nibble = this.getNibble(pos.getX() >> 4, sectionY, pos.getZ() >> 4);
        return nibble == null ? 0 : nibble.getVisible(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    protected void checkBlockInternal(long pos) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        if (!this.canUseCachedSection(x >> 4, y >> 4, z >> 4)) {
            return;
        }

        int block = this.blocks.blockAt(x, y, z);
        int emitted = 0;
        // Fast classes are guaranteed non-emissive; only slow blocks need a BlockState source query.
        if (this.blocks.isSlow(block)) {
            BlockState state = this.blocks.toState(block);
            emitted = this.getSourceLight(pos, state);
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
    protected void propagateLightSourcesInternal(int chunkX, int chunkZ) {
        LightChunk chunk = this.runCache.chunk(this.chunkGetter, chunkX, chunkZ);
        if (chunk == null) {
            return;
        }
        ChunkAccess access = (ChunkAccess)chunk;
        chunk.findBlockLightSources((pos, state) -> {
            int sectionY = pos.getY() >> 4;
            this.storage.getOrCreateSection(access, sectionY);
            int emitted = state.getLightEmission(this.levelReader, pos);
            if (emitted <= 0) {
                return;
            }
            if (emitted <= this.getCachedUpdatingLight(pos.getX(), pos.getY(), pos.getZ())) {
                return;
            }
            this.setCachedUpdatingLight(pos.getX(), pos.getY(), pos.getZ(), emitted);
            this.enqueueIncrease(pos.asLong(), emitted, YALightMath.ALL_DIRECTIONS, 0L);
        });
    }

    @Override
    protected void propagateLightSectionInternal(int chunkX, int sectionY, int chunkZ) {
        // Vanilla updateSectionStatus only changes storage availability for block light.
        // Block sources are collected by propagateLightSources(chunk), using
        // ChunkAccess.findBlockLightSources. Scanning the whole section here finds
        // additional dynamic emitters that vanilla intentionally does not collect
        // during chunk lighting, which changes saved light results.
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        if (!isEmpty) {
            this.storage.getOrCreateSection(pos.x(), pos.y(), pos.z());
        }
    }

    @Override
    protected void propagateIncrease(long pos, long meta) {
        int level = YALightMath.level(meta);
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        int stored = this.getCachedUpdatingLight(x, y, z);
        if ((meta & YALightMath.FLAG_RECHECK) != 0L && stored != level) {
            return;
        }
        if ((meta & YALightMath.FLAG_WRITE_LEVEL) != 0L && stored < level) {
            this.setCachedUpdatingLight(x, y, z, level);
            stored = level;
        }
        if (stored != level) {
            return;
        }

        int fromBlock = this.blocks.blockAt(x, y, z);
        int directions = YALightMath.directions(meta);
        while (directions != 0) {
            int directionIndex = Integer.numberOfTrailingZeros(directions);
            directions &= directions - 1;
            int toX = x + YALightMath.stepX(directionIndex);
            int toY = y + YALightMath.stepY(directionIndex);
            int toZ = z + YALightMath.stepZ(directionIndex);
            if (!this.canUseCachedSection(toX >> 4, toY >> 4, toZ >> 4)) {
                continue;
            }
            int current = this.getCachedUpdatingLight(toX, toY, toZ);
            if (current >= level - 1) {
                continue;
            }
            int toBlock = this.blocks.blockAt(toX, toY, toZ);
            if (this.blocks.isFull(toBlock)) {
                continue;
            }

            int target = level - this.blocks.opacityAt(toX, toY, toZ, toBlock);
            if (target <= current) {
                continue;
            }
            if ((fromBlock | toBlock) != 0
                    && this.blocks.shapeOccludes(x, y, z, fromBlock, toX, toY, toZ, toBlock, YALightMath.direction(directionIndex))) {
                continue;
            }
            this.setCachedUpdatingLight(toX, toY, toZ, target);
            if (target > 1) {
                this.enqueueIncrease(BlockPos.asLong(toX, toY, toZ), target, YALightMath.withoutOpposite(directionIndex), 0L);
            }
        }
    }

    @Override
    protected void propagateDecrease(long pos, long meta) {
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
            if (!this.canUseCachedSection(toX >> 4, toY >> 4, toZ >> 4)) {
                continue;
            }
            int current = this.getCachedUpdatingLight(toX, toY, toZ);
            if (current == 0) {
                continue;
            }
            long toPos = BlockPos.asLong(toX, toY, toZ);
            int toBlock = this.blocks.blockAt(toX, toY, toZ);
            if (this.blocks.isFull(toBlock)) {
                this.setCachedUpdatingLight(toX, toY, toZ, 0);
                continue;
            }

            int target = Math.max(0, level - this.blocks.opacityAt(toX, toY, toZ, toBlock));
            if (current > target) {
                // Neighbor still has more light than this removal explains; recheck it as another source path.
                this.enqueueIncrease(toPos, current, YALightMath.oppositeMask(directionIndex), YALightMath.FLAG_RECHECK);
                continue;
            }
            this.setCachedUpdatingLight(toX, toY, toZ, 0);
            if (this.blocks.isSlow(toBlock)) {
                int source = this.getSourceLight(toPos, this.blocks.toState(toBlock));
                if (source > 0) {
                    this.enqueueIncrease(toPos, source, YALightMath.ALL_DIRECTIONS, YALightMath.FLAG_WRITE_LEVEL);
                }
            }
            this.enqueueDecrease(toPos, target, YALightMath.withoutOpposite(directionIndex));
        }
    }

    @Override
    protected int getSourceLight(long pos, BlockState state) {
        if (!this.runCache.enabled(this.storage, BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4)) {
            return 0;
        }
        return state.getLightEmission(this.levelReader, this.mutablePos.set(pos));
    }

    private boolean canUseCachedSection(int chunkX, int sectionY, int chunkZ) {
        return sectionY >= this.minLightSection
                && sectionY <= this.maxLightSection
                && this.runCache.lightEnabled(this.storage, chunkX, chunkZ);
    }

    private int getCachedUpdatingLight(int x, int y, int z) {
        return this.runCache.getUpdatingLight(this.storage, x, y, z);
    }

    private void setCachedUpdatingLight(int x, int y, int z, int value) {
        this.runCache.setUpdatingLight(this.storage, x, y, z, value);
    }
}
