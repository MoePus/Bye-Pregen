package com.moepus.byepregen.yalight.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class YALightBlockAccess {
    // Classified blocks are 0, 1, or negative; this cannot alias a real block class.
    public static final int UNRESOLVED_BLOCK = 2;
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

    public YALightBlockAccess(
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

    public int blockAt(int x, int y, int z) {
        return this.blockFromRawId(this.cache.getRawId(this.chunkGetter, x, y, z));
    }

    public int blockAt(YASectionAccess section, int localIndex) {
        return this.blockFromRawId(section.rawId(localIndex));
    }

    private int blockFromRawId(int rawId) {
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

    public int rawId(int block) {
        return block & RAW_ID_MASK;
    }

    public boolean isFull(int block) {
        return block == FULL_BLOCK;
    }

    public boolean isSlow(int block) {
        return (block & SLOW_BIT) != 0;
    }

    public BlockState toState(int block) {
        if (block == EMPTY_BLOCK) {
            return Blocks.AIR.defaultBlockState();
        }
        if (block == FULL_BLOCK) {
            return Blocks.STONE.defaultBlockState();
        }
        return Block.stateById(this.rawId(block));
    }

    // Callers must reject FULL_BLOCK before reaching this hot-path helper.
    public int attenuatedLevel(int level, int x, int y, int z, int block) {
        if (!this.isSlow(block)) {
            return level - 1;
        }
        int opacity = Math.max(1, this.toState(block).getLightBlock(this.level, this.mutablePos.set(x, y, z)));
        return Math.max(0, level - opacity);
    }

    // NeoForge 1.21.1 permits position-dependent opacity and occlusion shapes.
    public int slowAttenuatedLevel(int level, int block, long pos) {
        return level - Math.max(1, this.toState(block).getLightBlock(this.level, this.mutablePos.set(pos)));
    }

    public boolean shapeOccludes(long fromPos, int fromBlock, long toPos, int toBlock, Direction direction) {
        return this.shapeOccludes(BlockPos.getX(fromPos), BlockPos.getY(fromPos), BlockPos.getZ(fromPos), fromBlock,
                BlockPos.getX(toPos), BlockPos.getY(toPos), BlockPos.getZ(toPos), toBlock, direction);
    }

    public boolean shapeOccludes(
            int fromX, int fromY, int fromZ, int fromBlock,
            int toX, int toY, int toZ, int toBlock,
            Direction direction
    ) {
        if (fromBlock == EMPTY_BLOCK && toBlock == EMPTY_BLOCK) {
            return false;
        }
        if (fromBlock == FULL_BLOCK || toBlock == FULL_BLOCK) {
            return true;
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

        BlockState state = this.toState(block);
        // Slow includes emitters and dynamic/partial-opacity states; some still opt out of shape occlusion.
        if (this.isSlow(block) && YALightMath.isEmptyShape(state)) {
            return Shapes.empty();
        }
        return state.getFaceOcclusionShape(this.level, this.mutablePos.set(x, y, z), direction);
    }
}
