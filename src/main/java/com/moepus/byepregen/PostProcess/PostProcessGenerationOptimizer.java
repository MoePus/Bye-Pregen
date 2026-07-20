package com.moepus.byepregen.PostProcess;

import it.unimi.dsi.fastutil.shorts.ShortList;
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
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

/*
 * Post-processing pipeline:
 * - preNorm runs before the chunk is submitted to the main thread. It sorts and deduplicates the
 *   postProcessing lists, then removes chunk-local no-op entries, including non-edge stable fluids.
 * - postProcess runs on the main thread. It handles the remaining fluid entries: edge fluids are
 *   delayed when required neighbor chunks are not FULL, and stable fluids are removed.
 */
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
        return block instanceof LightBlock || block instanceof LeavesBlock || block instanceof BrushableBlock || block instanceof SlabBlock || block instanceof TrapDoorBlock || block instanceof BeehiveBlock || block instanceof FallingBlock;
    }

    public static BlockState updateFromNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos) {
        Block block = state.getBlock();
        if (block instanceof SnowLayerBlock
                || block instanceof BushBlock
                || block instanceof CarpetBlock
                || block instanceof CactusBlock) {
            return updateShapeWithoutNeighbourLookup(state, level, pos, Direction.DOWN);
        }

        if (block instanceof SnowyDirtBlock
                || block instanceof MagmaBlock) {
            return updateFromNeighbourShapes(state, level, pos, UP_ONLY);
        }

        if (block instanceof CocoaBlock) {
            return updateFromNeighbourShape(state, level, pos, state.getValue(CocoaBlock.FACING));
        }

        if (block instanceof CaveVinesBlock
                || block instanceof CaveVinesPlantBlock) {
            return updateFromNeighbourShapes(state, level, pos, UP_DOWN_ONLY);
        }

        if (!HAS_CUSTOM_UPDATE_SHAPE.get(block.getClass())) {
            return state;
        }

        return Block.updateFromNeighbourShapes(state, level, pos);
    }

    private static boolean hasCustomUpdateShape(Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Block.class) {
            try {
                current.getDeclaredMethod("updateShape", BlockState.class, Direction.class, BlockState.class, LevelAccessor.class, BlockPos.class, BlockPos.class);
                return true;
            } catch (NoSuchMethodException ignored) {
            } catch (RuntimeException | LinkageError exception) {
                return true;
            }

            try {
                current = current.getSuperclass();
            } catch (RuntimeException | LinkageError exception) {
                return true;
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

    public static void preprocessPostProcessingLists(LevelChunk chunk, ShortList[] postProcessing) { // Main Thread
        processPostProcessingLists(PostProcessingContext.postProcess(chunk), postProcessing);
    }

    public static void preNormalizeAndFilterChunkLocalPostProcessingLists(ChunkAccess chunk, ShortList[] postProcessing) { // Worker Thread
        processPostProcessingLists(PostProcessingContext.preNorm(chunk), postProcessing);
    }

    private static void processPostProcessingLists(PostProcessingContext context, ShortList[] postProcessing) {
        context.preProcess(postProcessing);
        for (int sectionIndex = 0; sectionIndex < postProcessing.length; sectionIndex++) {
            ShortList list = postProcessing[sectionIndex];
            if (list == null || list.isEmpty()) {
                continue;
            }

            context.processList(sectionIndex, list);
        }
    }

}
