package com.moepus.byepregen;

import com.moepus.byepregen.mixin.ChunkHolderAccessor;
import com.moepus.byepregen.mixin.ChunkMapAccessor;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

public final class PostProcessGenerationOptimizer {
    private static final Direction[] UP_ONLY = {Direction.UP};
    private static final Direction[] UP_DOWN_ONLY = {Direction.UP, Direction.DOWN};
    private static final ClassValue<Boolean> HAS_CUSTOM_UPDATE_SHAPE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return hasCustomUpdateShape(type);
        }
    };

    private static final int LOCAL_MASK = 15;
    private static final int DELAYED_FLUID_TICK_DELAY = 20;
    private static final int BUCKET_INTERIOR = 0;
    private static final int BUCKET_NORTH_EDGE = 1;
    private static final int BUCKET_SOUTH_EDGE = 2;
    private static final int BUCKET_WEST_EDGE = 3;
    private static final int BUCKET_EAST_EDGE = 4;
    private static final int BUCKET_CORNERS = 5;
    private static final Direction[] FLUID_CHECK_DIRECTIONS = {
            Direction.DOWN,
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
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

    public static void preprocessPostProcessingLists(LevelChunk chunk, ShortList[] postProcessing) {
        PostProcessingContext context = new PostProcessingContext(chunk);
        for (int sectionIndex = 0; sectionIndex < postProcessing.length; sectionIndex++) {
            ShortList list = postProcessing[sectionIndex];
            if (list == null) {
                continue;
            }

            sortPostProcessingList(list);
            processPostProcessingList(context, sectionIndex, list);
        }
    }

    private static void sortPostProcessingList(ShortList list) {
        int size = list.size();
        for (int start = (size >>> 1) - 1; start >= 0; start--) {
            siftDownPostProcessingList(list, start, size);
        }

        for (int end = size - 1; end > 0; end--) {
            short first = list.getShort(0);
            list.set(0, list.getShort(end));
            list.set(end, first);
            siftDownPostProcessingList(list, 0, end);
        }
    }

    private static void siftDownPostProcessingList(ShortList list, int root, int size) {
        short rootValue = list.getShort(root);
        int rootKey = postProcessSortKey(rootValue);

        for (int child = (root << 1) + 1; child < size; child = (root << 1) + 1) {
            short childValue = list.getShort(child);
            int childKey = postProcessSortKey(childValue);
            int right = child + 1;
            if (right < size) {
                short rightValue = list.getShort(right);
                int rightKey = postProcessSortKey(rightValue);
                if (rightKey > childKey) {
                    child = right;
                    childValue = rightValue;
                    childKey = rightKey;
                }
            }

            if (rootKey >= childKey) {
                break;
            }

            list.set(root, childValue);
            root = child;
        }

        list.set(root, rootValue);
    }

    private static void processPostProcessingList(PostProcessingContext context, int sectionIndex, ShortList list) {
        int writeIndex = 0;
        int size = list.size();
        int sectionY = context.chunk.getSectionYFromSectionIndex(sectionIndex);
        boolean hasLastPackedPos = false;
        short lastPackedPos = 0;

        for (int readIndex = 0; readIndex < size; readIndex++) {
            short packedPos = list.getShort(readIndex);
            if (hasLastPackedPos && packedPos == lastPackedPos) {
                continue;
            }

            hasLastPackedPos = true;
            lastPackedPos = packedPos;

            BlockPos pos = unpackPostProcessingPos(context, sectionY, packedPos);
            BlockState state = context.chunk.getBlockState(pos);
            FluidState fluidState = state.getFluidState();
            boolean remove = false;

            if (!fluidState.isEmpty()) {
                remove = shouldRemoveFluidPostProcessing(context, pos, fluidState);
            } else if (isNoOpPostProcess(state)) {
                remove = true;
            }

            if (!remove) {
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

    private static BlockPos unpackPostProcessingPos(PostProcessingContext context, int sectionY, short packedPos) {
        return context.pos.set(
                context.chunkPos.getMinBlockX() + localX(packedPos),
                SectionPos.sectionToBlockCoord(sectionY, localY(packedPos)),
                context.chunkPos.getMinBlockZ() + localZ(packedPos)
        );
    }

    private static boolean shouldRemoveFluidPostProcessing(PostProcessingContext context, BlockPos pos, FluidState fluidState) {
        if (shouldDelayEdgeFluid(context, pos, fluidState)) {
            return true;
        }

        return isTriviallyStableFluid(context, pos, fluidState);
    }

    private static boolean shouldDelayEdgeFluid(PostProcessingContext context, BlockPos pos, FluidState fluidState) {
        int localX = SectionPos.sectionRelative(pos.getX());
        int localZ = SectionPos.sectionRelative(pos.getZ());
        if (!isLocalChunkEdge(localX, localZ)) {
            return false;
        }

        if (context.areRequiredNeighborsFull(localX, localZ)) {
            return false;
        }

        context.chunk.getLevel().scheduleTick(pos.immutable(), fluidState.getType(), DELAYED_FLUID_TICK_DELAY);
        return true;
    }

    private static LevelChunk getLoadedFullChunk(ServerLevel level, int chunkX, int chunkZ) {
        ChunkHolder holder = ((ChunkMapAccessor) level.getChunkSource().chunkMap)
                .byepregen$invokeGetVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
        if (holder == null) {
            return null;
        }

        LevelChunk chunk = ((ChunkHolderAccessor) holder).byepregen$getCurrentlyLoading();
        if (chunk != null) {
            return chunk;
        }

        ChunkAccess chunkAccess = holder.getChunkIfPresent(ChunkStatus.FULL);
        return chunkAccess instanceof LevelChunk levelChunk ? levelChunk : null;
    }

    private static boolean isTriviallyStableFluid(PostProcessingContext context, BlockPos pos, FluidState fluidState) {
        if (!(fluidState.getType() instanceof FlowingFluid) || !fluidState.isSource()) {
            return false;
        }

        for (Direction direction : FLUID_CHECK_DIRECTIONS) {
            BlockPos.MutableBlockPos neighborPos = context.neighborPos;
            neighborPos.setWithOffset(pos, direction);
            if (context.chunk.getLevel().isOutsideBuildHeight(neighborPos)) {
                continue;
            }

            if (!isStableFluidNeighbor(context.chunk.getLevel(), neighborPos, fluidState)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isStableFluidNeighbor(Level level, BlockPos pos, FluidState fluidState) {
        BlockState neighborState = level.getBlockState(pos);
        FluidState neighborFluid = neighborState.getFluidState();
        if (neighborFluid.getType().isSame(fluidState.getType()) && neighborFluid.isSource()) {
            return true;
        }

        return neighborState.blocksMotion();
    }

    private static int postProcessSortKey(short packedPos) {
        return (postProcessBucket(packedPos) << 12) | palettedIndex(packedPos);
    }

    private static int postProcessBucket(short packedPos) {
        int localX = localX(packedPos);
        int localZ = localZ(packedPos);
        boolean west = localX == 0;
        boolean east = localX == LOCAL_MASK;
        boolean north = localZ == 0;
        boolean south = localZ == LOCAL_MASK;

        if ((west || east) && (north || south)) return BUCKET_CORNERS;
        if (north) return BUCKET_NORTH_EDGE;
        if (south) return BUCKET_SOUTH_EDGE;
        if (west) return BUCKET_WEST_EDGE;
        if (east) return BUCKET_EAST_EDGE;
        return BUCKET_INTERIOR;
    }

    private static int palettedIndex(short packedPos) {
        return (localY(packedPos) << 8) | (localZ(packedPos) << 4) | localX(packedPos);
    }

    private static int localX(short packedPos) {
        return packedPos & LOCAL_MASK;
    }

    private static int localY(short packedPos) {
        return (packedPos >>> 4) & LOCAL_MASK;
    }

    private static int localZ(short packedPos) {
        return (packedPos >>> 8) & LOCAL_MASK;
    }

    private static boolean isLocalChunkEdge(int localX, int localZ) {
        return localX == 0 || localX == LOCAL_MASK || localZ == 0 || localZ == LOCAL_MASK;
    }

    private static final class PostProcessingContext {
        private final LevelChunk chunk;
        private final ServerLevel level;
        private final ChunkPos chunkPos;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private final BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        private boolean westChecked;
        private boolean eastChecked;
        private boolean northChecked;
        private boolean southChecked;
        private LevelChunk westChunk;
        private LevelChunk eastChunk;
        private LevelChunk northChunk;
        private LevelChunk southChunk;

        private PostProcessingContext(LevelChunk chunk) {
            Level level = chunk.getLevel();
            this.chunk = chunk;
            this.level = level instanceof ServerLevel serverLevel ? serverLevel : null;
            this.chunkPos = chunk.getPos();
        }

        private boolean areRequiredNeighborsFull(int localX, int localZ) {
            if (localX == 0 && !isWestFull()) return false;
            if (localX == LOCAL_MASK && !isEastFull()) return false;
            if (localZ == 0 && !isNorthFull()) return false;
            return localZ != LOCAL_MASK || isSouthFull();
        }

        private boolean isWestFull() {
            if (!westChecked) {
                westChunk = level == null ? null : getLoadedFullChunk(level, chunkPos.x - 1, chunkPos.z);
                westChecked = true;
            }
            return westChunk != null;
        }

        private boolean isEastFull() {
            if (!eastChecked) {
                eastChunk = level == null ? null : getLoadedFullChunk(level, chunkPos.x + 1, chunkPos.z);
                eastChecked = true;
            }
            return eastChunk != null;
        }

        private boolean isNorthFull() {
            if (!northChecked) {
                northChunk = level == null ? null : getLoadedFullChunk(level, chunkPos.x, chunkPos.z - 1);
                northChecked = true;
            }
            return northChunk != null;
        }

        private boolean isSouthFull() {
            if (!southChecked) {
                southChunk = level == null ? null : getLoadedFullChunk(level, chunkPos.x, chunkPos.z + 1);
                southChecked = true;
            }
            return southChunk != null;
        }
    }
}
