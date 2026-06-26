package com.moepus.byepregen.yalight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class YALightBlockAccess {
    private static final int RAW_ID_BITS = 30;
    private static final int RAW_ID_MASK = (1 << RAW_ID_BITS) - 1;
    private static final int SLOW_BIT = 1 << RAW_ID_BITS;
    private static final int STATEFUL_BIT = 1 << 31;

    // Packed block ids are arranged for the hot path: 0 is empty, 1 is known-full.
    // Negative values are stateful: sign bit marks "has raw id", bit 30 separates slow from shaped.
    private static final int EMPTY_BLOCK = 0;
    private static final int FULL_BLOCK = 1;
    private static final int SHAPED_BLOCK = STATEFUL_BIT;
    private static final int SLOW_BLOCK = STATEFUL_BIT | SLOW_BIT;

    private final YAChunkRunCache cache;
    private final LightChunkGetter chunkGetter;
    private final BlockGetter level;
    private final BlockPos.MutableBlockPos mutablePos;

    YALightBlockAccess(
            YAChunkRunCache cache,
            LightChunkGetter chunkGetter,
            BlockGetter level,
            BlockPos.MutableBlockPos mutablePos
    ) {
        this.cache = cache;
        this.chunkGetter = chunkGetter;
        this.level = level;
        this.mutablePos = mutablePos;
    }

    int blockAt(int x, int y, int z) {
        int rawId = this.cache.getRawId(this.chunkGetter, x, y, z);
        if (rawId < 0) {
            return 0;
        }
        int lightClass = YABlockStateLightClass.fromRawId(rawId);
        if (lightClass == YABlockStateLightClass.CLEAR) {
            return EMPTY_BLOCK;
        }
        if (lightClass == YABlockStateLightClass.FULL) {
            return FULL_BLOCK;
        }
        if (lightClass == YABlockStateLightClass.SHAPED) {
            return SHAPED_BLOCK | (rawId & RAW_ID_MASK);
        }
        return SLOW_BLOCK | (rawId & RAW_ID_MASK);
    }

    int rawId(int block) {
        return block & RAW_ID_MASK;
    }

    boolean isFull(int block) {
        return block == FULL_BLOCK;
    }

    boolean isShaped(int block) {
        return block < 0 && (block & SLOW_BIT) == 0;
    }

    boolean isSlow(int block) {
        return block < 0 && (block & SLOW_BIT) != 0;
    }

    BlockState stateAt(int x, int y, int z, int block) {
        if (block == EMPTY_BLOCK) {
            return YALightMath.air();
        }
        if (block == FULL_BLOCK) {
            throw new IllegalArgumentException("FULL light block does not retain a raw block-state id");
        }
        return this.cache.getState(this.chunkGetter, this.mutablePos, x, y, z, this.rawId(block));
    }

    int opacityAt(int x, int y, int z, int block) {
        if (block == EMPTY_BLOCK) {
            return 1;
        }
        if (block == FULL_BLOCK) {
            return 15;
        }
        if (this.isShaped(block)) {
            return 1;
        }
        return Math.max(1, this.stateAt(x, y, z, block).getLightBlock(this.level, this.mutablePos.set(x, y, z)));
    }

    int lightBlockAt(int x, int y, int z, int block) {
        if (block == EMPTY_BLOCK || this.isShaped(block)) {
            return 0;
        }
        if (block == FULL_BLOCK) {
            return 15;
        }
        return this.stateAt(x, y, z, block).getLightBlock(this.level, this.mutablePos.set(x, y, z));
    }

    boolean shapeOccludes(
            int fromX, int fromY, int fromZ, int fromBlock,
            int toX, int toY, int toZ, int toBlock,
            Direction direction
    ) {
        if (fromBlock == EMPTY_BLOCK && toBlock == EMPTY_BLOCK) {
            return false;
        }
        VoxelShape fromShape = this.faceShapeAt(fromX, fromY, fromZ, fromBlock, direction);
        VoxelShape toShape = this.faceShapeAt(toX, toY, toZ, toBlock, direction.getOpposite());
        return Shapes.faceShapeOccludes(fromShape, toShape);
    }

    private VoxelShape faceShapeAt(int x, int y, int z, int block, Direction direction) {
        if (block == EMPTY_BLOCK) {
            return Shapes.empty();
        }
        if (block == FULL_BLOCK) {
            return Shapes.block();
        }

        BlockState state = this.stateAt(x, y, z, block);
        // Slow includes emitters and dynamic/partial-opacity states; some still opt out of shape occlusion.
        if (this.isSlow(block) && YALightMath.isEmptyShape(state)) {
            return Shapes.empty();
        }
        return state.getFaceOcclusionShape(this.level, this.mutablePos.set(x, y, z), direction);
    }
}
