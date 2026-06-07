package com.moepus.byepregen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class PostProcessGenerationOptimizer {
    private static final Direction[] UP_ONLY = {Direction.UP};
    private static final Direction[] UP_DOWN_ONLY = {Direction.UP, Direction.DOWN};
    private static final ClassValue<Boolean> HAS_CUSTOM_UPDATE_SHAPE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return hasCustomUpdateShape(type);
        }
    };

    private PostProcessGenerationOptimizer() {
    }

    public static boolean isNoOpPostProcess(BlockState state) {
        Block block = state.getBlock();
        if (block.getClass().equals(Block.class)) return true;
        return block instanceof LightBlock
                || block instanceof LeavesBlock
                || block instanceof BrushableBlock
                || block instanceof SlabBlock
                || block instanceof TrapDoorBlock
                || block instanceof BeehiveBlock
                || block instanceof FallingBlock;
    }

    public static BlockState updateFromNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos) {
        Block block = state.getBlock();

        switch (block) {
            case SnowLayerBlock ignored -> {
                return updateShapeWithoutNeighbourLookup(state, level, pos, Direction.DOWN);
            }
            case BushBlock ignored -> {
                return updateShapeWithoutNeighbourLookup(state, level, pos, Direction.DOWN);
            }
            case CarpetBlock ignored -> {
                return updateShapeWithoutNeighbourLookup(state, level, pos, Direction.DOWN);
            }
            case CactusBlock ignored -> {
                return updateShapeWithoutNeighbourLookup(state, level, pos, Direction.DOWN);
            }
            case SnowyDirtBlock ignored -> {
                return updateFromNeighbourShapes(state, level, pos, UP_ONLY);
            }
            case MagmaBlock ignored -> {
                return updateFromNeighbourShapes(state, level, pos, UP_ONLY);
            }
            case CocoaBlock ignored -> {
                return updateFromNeighbourShape(state, level, pos, state.getValue(CocoaBlock.FACING));
            }
            case CaveVinesBlock ignored -> {
                return updateFromNeighbourShapes(state, level, pos, UP_DOWN_ONLY);
            }
            case CaveVinesPlantBlock ignored -> {
                return updateFromNeighbourShapes(state, level, pos, UP_DOWN_ONLY);
            }
            default -> {
            }
        }

        if (!HAS_CUSTOM_UPDATE_SHAPE.get(block.getClass())) {
            return state;
        }

        return Block.updateFromNeighbourShapes(state, level, pos);
    }

    private static boolean hasCustomUpdateShape(Class<?> type) {
        for (Class<?> current = type; current != null && current != Block.class; current = current.getSuperclass()) {
            try {
                current.getDeclaredMethod(
                        "updateShape",
                        BlockState.class,
                        Direction.class,
                        BlockState.class,
                        LevelAccessor.class,
                        BlockPos.class,
                        BlockPos.class
                );
                // logCustomUpdateShape(type);
                return true;
            } catch (NoSuchMethodException ignored) {
            }
        }

        return false;
    }

    private static BlockState updateFromNeighbourShape(BlockState state, LevelAccessor level, BlockPos pos, Direction direction) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        mutablePos.setWithOffset(pos, direction);
        return state.updateShape(direction, level.getBlockState(mutablePos), level, pos, mutablePos);
    }

    private static BlockState updateFromNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, Direction[] directions) {
        BlockState updatedState = state;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (Direction direction : directions) {
            mutablePos.setWithOffset(pos, direction);
            updatedState = updatedState.updateShape(direction, level.getBlockState(mutablePos), level, pos, mutablePos);
        }

        return updatedState;
    }

    private static BlockState updateShapeWithoutNeighbourLookup(BlockState state, LevelAccessor level, BlockPos pos, Direction direction) {
        return state.updateShape(direction, state, level, pos, pos);
    }
}
