package com.moepus.byepregen.mixin;

import com.moepus.byepregen.PostProcessGenerationOptimizer;
import com.moepus.byepregen.WorldgenUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortListIterator;


@Mixin(value = LevelChunk.class, remap = false)
public abstract class LevelChunkPostProcessMixin extends ChunkAccess {
    @Shadow
    @Final
    Level level;

    @Unique
    private static final int c6c$DELAYED_FLUID_TICK_DELAY = 20;
    @Unique
    private static final Direction[] c6c$FLUID_CHECK_DIRECTIONS = {
            Direction.DOWN,
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    public LevelChunkPostProcessMixin(ChunkPos p_187621_, UpgradeData p_187622_, LevelHeightAccessor p_187623_, Registry<Biome> p_187624_, long p_187625_, @Nullable LevelChunkSection[] p_187626_, @Nullable BlendingData p_187627_) {
        super(p_187621_, p_187622_, p_187623_, p_187624_, p_187625_, p_187626_, p_187627_);
    }

    @Inject(method = "postProcessGeneration", at = @At("HEAD"))
    private void c6c$preprocessPostProcessingLists(CallbackInfo ci) {
        ChunkPos chunkPos = this.getPos();
        for (int sectionIndex = 0; sectionIndex < this.postProcessing.length; sectionIndex++) {
            ShortList list = this.postProcessing[sectionIndex];
            if (list == null) {
                continue;
            }

            for (ShortListIterator iterator = list.iterator(); iterator.hasNext(); ) {
                short packedPos = iterator.nextShort();
                BlockPos pos = ProtoChunk.unpackOffsetCoordinates(packedPos, this.getSectionYFromSectionIndex(sectionIndex), chunkPos);
                BlockState state = this.getBlockState(pos);
                FluidState fluidState = state.getFluidState();

                LevelChunk levelChunk = (LevelChunk) (Object) this;
                if (!fluidState.isEmpty()) {
                    if (WorldgenUtil.isChunkEdge(pos, chunkPos) && !WorldgenUtil.areEdgeNeighborChunksFull(levelChunk.getLevel(), pos)) {
                        this.level.scheduleTick(pos, fluidState.getType(), c6c$DELAYED_FLUID_TICK_DELAY);
                        iterator.remove();
                    } else if (c6c$isTriviallyStableFluid(levelChunk.getLevel(), pos, fluidState)) {
                        iterator.remove();
                    }
                } else if (PostProcessGenerationOptimizer.isNoOpPostProcess(state)) {
                    iterator.remove();
                }
            }
        }
    }

    @Redirect(
            method = "postProcessGeneration",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;updateFromNeighbourShapes(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private BlockState c6c$skipNoOpNeighbourShapeUpdates(BlockState state, LevelAccessor level, BlockPos pos) {
        return PostProcessGenerationOptimizer.updateFromNeighbourShapes(state, level, pos);
    }

    @Unique
    private static boolean c6c$isTriviallyStableFluid(Level level, BlockPos pos, FluidState fluidState) {
        if (!(fluidState.getType() instanceof FlowingFluid) || !fluidState.isSource()) {
            return false;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (Direction direction : c6c$FLUID_CHECK_DIRECTIONS) {
            mutablePos.setWithOffset(pos, direction);
            if (level.isOutsideBuildHeight(mutablePos)) {
                continue;
            }

            BlockState neighborState = level.getBlockState(mutablePos);
            FluidState neighborFluid = neighborState.getFluidState();
            if (neighborFluid.getType().isSame(fluidState.getType()) && neighborFluid.isSource()) {
                continue;
            }

            if (!neighborState.blocksMotion()) {
                return false;
            }
        }

        return true;
    }
}
