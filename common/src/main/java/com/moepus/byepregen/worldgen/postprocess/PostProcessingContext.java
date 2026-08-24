package com.moepus.byepregen.worldgen.postprocess;

import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

abstract class PostProcessingContext {
    static final int LOCAL_MASK = 15;
    static final int DELAYED_FLUID_TICK_DELAY = 20;
    private static final Direction[] FLUID_CHECK_DIRECTIONS = {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    final ChunkAccess chunk;
    final ChunkPos chunkPos;
    final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    final BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

    private PostProcessingContext(ChunkAccess chunk) {
        this.chunk = chunk;
        this.chunkPos = chunk.getPos();
    }

    static PostProcessingContext preNorm(ChunkAccess chunk) {
        return new PreNorm(chunk);
    }

    static PostProcessingContext postProcess(LevelChunk chunk) {
        Level level = chunk.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("LevelChunk post-processing requires ServerLevel");
        }

        return new PostProcess(chunk, serverLevel);
    }

    void preProcess(ShortList[] postProcessing) {
    }

    void processList(int sectionIndex, ShortList list) {
        int writeIndex = 0;
        int size = list.size();

        for (int readIndex = 0; readIndex < size; readIndex++) {
            short packedPos = list.getShort(readIndex);
            BlockPos pos = unpackPos(sectionIndex, packedPos);
            BlockState state = getBlockState(pos);

            if (!removePostProcessingEntry(packedPos, pos, state)) {
                if (writeIndex != readIndex) {
                    list.set(writeIndex, packedPos);
                }
                writeIndex++;
            }
        }

        if (writeIndex < size) {
            list.removeElements(writeIndex, size);
        }
    }

    abstract boolean removePostProcessingEntry(short packedPos, BlockPos pos, BlockState state);

    BlockPos unpackPos(int sectionIndex, short packedPos) {
        int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
        return pos.set(chunkPos.getMinBlockX() + localX(packedPos), SectionPos.sectionToBlockCoord(sectionY, localY(packedPos)), chunkPos.getMinBlockZ() + localZ(packedPos));
    }

    boolean isOutsideBuildHeight(BlockPos pos) {
        return chunk.isOutsideBuildHeight(pos);
    }

    abstract BlockState getBlockState(BlockPos pos);

    boolean isTriviallyStableFluid(BlockPos pos, FluidState fluidState) {
        if (!(fluidState.getType() instanceof FlowingFluid) || !fluidState.isSource()) {
            return false;
        }

        for (Direction direction : FLUID_CHECK_DIRECTIONS) {
            BlockPos.MutableBlockPos neighborPos = this.neighborPos;
            neighborPos.setWithOffset(pos, direction);
            if (isOutsideBuildHeight(neighborPos)) {
                continue;
            }

            BlockState neighborState = getBlockState(neighborPos);
            FluidState neighborFluid = neighborState.getFluidState();
            if (!neighborFluid.getType().isSame(fluidState.getType()) || !neighborFluid.isSource()) {
                if (!neighborState.blocksMotion()) {
                    return false;
                }
            }
        }

        return true;
    }

    private static final class PreNorm extends PostProcessingContext {
        private PreNorm(ChunkAccess chunk) {
            super(chunk);
        }

        @Override
        void preProcess(ShortList[] postProcessing) {
            PostProcessingSorter.sortAndDeduplicate(postProcessing);
        }

        @Override
        boolean removePostProcessingEntry(short packedPos, BlockPos pos, BlockState state) {
            FluidState fluidState = state.getFluidState();
            if (fluidState.isEmpty()) {
                return PostProcessGenerationOptimizer.isNoOpPostProcess(state);
            }

            if (isLocalChunkEdge(localX(packedPos), localZ(packedPos))) {
                return false;
            }

            return isTriviallyStableFluid(pos, fluidState);
        }

        @Override
        BlockState getBlockState(BlockPos pos) {
            return chunk.getBlockState(pos);
        }
    }

    private static final class PostProcess extends PostProcessingContext {
        private final LevelChunk levelChunk;
        private final ServerLevel level;
        private boolean westChecked;
        private boolean eastChecked;
        private boolean northChecked;
        private boolean southChecked;
        private LevelChunk westChunk;
        private LevelChunk eastChunk;
        private LevelChunk northChunk;
        private LevelChunk southChunk;

        private PostProcess(LevelChunk chunk, ServerLevel level) {
            super(chunk);
            this.levelChunk = chunk;
            this.level = level;
        }

        @Override
        boolean removePostProcessingEntry(short packedPos, BlockPos pos, BlockState state) {
            FluidState fluidState = state.getFluidState();
            if (fluidState.isEmpty())
                return false;

            if (scheduleDelayedEdgeFluidTickIfNeighborMissing(pos, fluidState)) {
                return true;
            }

            return isTriviallyStableFluid(pos, fluidState);
        }

        @Override
        BlockState getBlockState(BlockPos pos) {
            return level.getBlockState(pos);
        }

        void scheduleDelayedFluidTick(BlockPos pos, Fluid fluid, int delay) {
            levelChunk.getLevel().scheduleTick(pos.immutable(), fluid, delay);
        }

        boolean areRequiredNeighborsFull(int localX, int localZ) {
            if (localX == 0 && !isWestFull())
                return false;
            if (localX == LOCAL_MASK && !isEastFull())
                return false;
            if (localZ == 0 && !isNorthFull())
                return false;
            return localZ != LOCAL_MASK || isSouthFull();
        }

        private boolean scheduleDelayedEdgeFluidTickIfNeighborMissing(BlockPos pos, FluidState fluidState) {
            int localX = SectionPos.sectionRelative(pos.getX());
            int localZ = SectionPos.sectionRelative(pos.getZ());
            if (!isLocalChunkEdge(localX, localZ)) {
                return false;
            }

            if (areRequiredNeighborsFull(localX, localZ)) {
                return false;
            }

            scheduleDelayedFluidTick(pos, fluidState.getType(), DELAYED_FLUID_TICK_DELAY);
            return true;
        }

        private boolean isWestFull() {
            if (!westChecked) {
                westChunk = getLoadedFullChunk(chunkPos.x() - 1, chunkPos.z());
                westChecked = true;
            }
            return westChunk != null;
        }

        private boolean isEastFull() {
            if (!eastChecked) {
                eastChunk = getLoadedFullChunk(chunkPos.x() + 1, chunkPos.z());
                eastChecked = true;
            }
            return eastChunk != null;
        }

        private boolean isNorthFull() {
            if (!northChecked) {
                northChunk = getLoadedFullChunk(chunkPos.x(), chunkPos.z() - 1);
                northChecked = true;
            }
            return northChunk != null;
        }

        private boolean isSouthFull() {
            if (!southChecked) {
                southChunk = getLoadedFullChunk(chunkPos.x(), chunkPos.z() + 1);
                southChecked = true;
            }
            return southChunk != null;
        }

        private LevelChunk getLoadedFullChunk(int chunkX, int chunkZ) {
            return level.getChunkSource().getChunkNow(chunkX, chunkZ);
        }
    }


    static int localX(short packedPos) {
        return packedPos & LOCAL_MASK;
    }

    static int localY(short packedPos) {
        return (packedPos >>> 4) & LOCAL_MASK;
    }

    static int localZ(short packedPos) {
        return (packedPos >>> 8) & LOCAL_MASK;
    }

    static boolean isLocalChunkEdge(int localX, int localZ) {
        return localX == 0 || localX == LOCAL_MASK || localZ == 0 || localZ == LOCAL_MASK;
    }
}
