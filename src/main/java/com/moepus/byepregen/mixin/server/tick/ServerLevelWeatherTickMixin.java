package com.moepus.byepregen.mixin.server.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelWeatherTickMixin extends Level {
    protected ServerLevelWeatherTickMixin(
            WritableLevelData levelData,
            ResourceKey<Level> dimension,
            RegistryAccess registryAccess,
            Holder<DimensionType> dimensionTypeRegistration,
            Supplier<ProfilerFiller> profiler,
            boolean isClientSide,
            boolean isDebug,
            long biomeZoomSeed,
            int maxChainedNeighborUpdates) {
        super(levelData, dimension, registryAccess, dimensionTypeRegistration, profiler, isClientSide, isDebug, biomeZoomSeed, maxChainedNeighborUpdates);
    }

    @Redirect(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;tickPrecipitation(Lnet/minecraft/core/BlockPos;)V"
            )
    )
    private void byepregen$tickPrecipitationWithCurrentChunk(
            ServerLevel level,
            BlockPos blockPos,
            LevelChunk chunk,
            int randomTickSpeed) {
        this.byepregen$tickPrecipitation(chunk, blockPos);
    }

    @Unique
    private void byepregen$tickPrecipitation(LevelChunk chunk, BlockPos blockPos) {
        BlockPos surface = new BlockPos(
                blockPos.getX(),
                chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX() & 15, blockPos.getZ() & 15) + 1,
                blockPos.getZ());
        BlockPos belowSurface = surface.below();
        Biome biome = this.byepregen$getBiome(chunk, surface).value();
        BlockState belowSurfaceState = null;

        if (!biome.warmEnoughToRain(belowSurface)) {
            belowSurfaceState = chunk.getBlockState(belowSurface);
        }

        if (belowSurfaceState != null && this.byepregen$shouldFreeze(chunk, belowSurface, belowSurfaceState)) {
            BlockState ice = Blocks.ICE.defaultBlockState();
            if (this.byepregen$setBlockAndUpdate(chunk, belowSurface, ice)) {
                belowSurfaceState = ice;
            }
        }

        if (!this.isRaining()) {
            return;
        }

        int snowAccumulationHeight = this.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
        if (snowAccumulationHeight > 0
                && !biome.warmEnoughToRain(surface)
                && surface.getY() < this.getMaxBuildHeight()
                && this.getBrightness(LightLayer.BLOCK, surface) < 10) {
            BlockState state = chunk.getBlockState(surface);

            if ((state.isAir() || state.is(Blocks.SNOW))) {
                if (belowSurfaceState == null) {
                    belowSurfaceState = chunk.getBlockState(belowSurface);
                }
                if (this.byepregen$snowCanSurvive(surface, belowSurfaceState)) {
                    if (state.is(Blocks.SNOW)) {
                        int layers = state.getValue(SnowLayerBlock.LAYERS);
                        if (layers < Math.min(snowAccumulationHeight, 8)) {
                            BlockState newState = state.setValue(SnowLayerBlock.LAYERS, layers + 1);
                            Block.pushEntitiesUp(state, newState, this, surface);
                            this.byepregen$setBlockAndUpdate(chunk, surface, newState);
                        }
                    } else {
                        this.byepregen$setBlockAndUpdate(chunk, surface, Blocks.SNOW.defaultBlockState());
                    }
                }
            }
        }

        Biome.Precipitation precipitation = biome.getPrecipitationAt(belowSurface);
        if (precipitation != Biome.Precipitation.NONE) {
            if (belowSurfaceState == null) {
                belowSurfaceState = chunk.getBlockState(belowSurface);
            }
            belowSurfaceState.getBlock().handlePrecipitation(belowSurfaceState, (ServerLevel) (Object) this, belowSurface, precipitation);
        }
    }

    @Unique
    private Holder<Biome> byepregen$getBiome(LevelChunk currentChunk, BlockPos pos) {
        return this.byepregen$getSectionNoiseBiome(
                currentChunk,
                QuartPos.fromBlock(pos.getX()),
                QuartPos.fromBlock(pos.getY()),
                QuartPos.fromBlock(pos.getZ()));
    }

    @Unique
    private Holder<Biome> byepregen$getSectionNoiseBiome(LevelChunk chunk, int quartX, int quartY, int quartZ) {
        int minQuartY = QuartPos.fromBlock(chunk.getMinBuildHeight());
        int maxQuartY = minQuartY + QuartPos.fromBlock(chunk.getHeight()) - 1;
        int clampedQuartY = Mth.clamp(quartY, minQuartY, maxQuartY);
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(QuartPos.toBlock(clampedQuartY)));
        return section.getNoiseBiome(quartX & 3, clampedQuartY & 3, quartZ & 3);
    }

    @Unique
    private boolean byepregen$shouldFreeze(LevelChunk chunk, BlockPos water, BlockState state) {
        FluidState fluid = state.getFluidState();
        if (fluid.getType() != Fluids.WATER || !(state.getBlock() instanceof LiquidBlock)) {
            return false;
        }
        if (this.getBrightness(LightLayer.BLOCK, water) >= 10) {
            return false;
        }

        if (!this.byepregen$freezeNeighborChunksLoaded(chunk, water)) {
            return false;
        }

        boolean surroundedByWater = this.byepregen$isWaterAt(chunk, water.west())
                && this.byepregen$isWaterAt(chunk, water.east())
                && this.byepregen$isWaterAt(chunk, water.north())
                && this.byepregen$isWaterAt(chunk, water.south());
        return !surroundedByWater;
    }

    @Unique
    private boolean byepregen$freezeNeighborChunksLoaded(LevelChunk currentChunk, BlockPos water) {
        int localX = water.getX() & 15;
        int localZ = water.getZ() & 15;
        if (localX == 0 && this.byepregen$getChunkForPos(currentChunk, water.west()) == null) {
            return false;
        }
        if (localX == 15 && this.byepregen$getChunkForPos(currentChunk, water.east()) == null) {
            return false;
        }
        if (localZ == 0 && this.byepregen$getChunkForPos(currentChunk, water.north()) == null) {
            return false;
        }
        return localZ != 15 || this.byepregen$getChunkForPos(currentChunk, water.south()) != null;
    }

    @Unique
    private boolean byepregen$snowCanSurvive(BlockPos pos, BlockState belowState) {
        BlockPos below = pos.below();
        if (belowState.is(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON)) {
            return false;
        }
        if (belowState.is(BlockTags.SNOW_LAYER_CAN_SURVIVE_ON)) {
            return true;
        }
        return Block.isFaceFull(belowState.getCollisionShape(this, below), Direction.UP)
                || belowState.is(Blocks.SNOW) && belowState.getValue(SnowLayerBlock.LAYERS) == 8;
    }

    @Unique
    private boolean byepregen$isWaterAt(LevelChunk currentChunk, BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return false;
        }

        LevelChunk chunk = this.byepregen$getChunkForPos(currentChunk, pos);
        return chunk != null && chunk.getFluidState(pos).is(FluidTags.WATER);
    }

    @Unique
    private LevelChunk byepregen$getChunkForPos(LevelChunk currentChunk, BlockPos pos) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        if (currentChunk.getPos().x == chunkX && currentChunk.getPos().z == chunkZ) {
            return currentChunk;
        }
        return this.getChunkSource().getChunkNow(chunkX, chunkZ);
    }

    @Unique
    private boolean byepregen$setBlockAndUpdate(LevelChunk chunk, BlockPos pos, BlockState state) {
        if (this.captureBlockSnapshots) {
            return this.setBlock(pos, state, 3, 512);
        }
        if (this.isOutsideBuildHeight(pos) || !this.isClientSide && this.isDebug()) {
            return false;
        }

        pos = pos.immutable();
        BlockState oldState = chunk.setBlockState(pos, state, false);
        if (oldState == null) {
            return false;
        }

        this.markAndNotifyBlock(pos, chunk, oldState, state, 3, 512);
        return true;
    }
}
