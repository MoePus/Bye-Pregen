package com.moepus.byepregen.lighttest;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/** Detached world data: changing emission never changes the stored BlockState. */
public final class DynamicLightTestView implements BlockAndLightGetter, DynamicLightTestBlock.Source {
    private final LevelHeightAccessor height;
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Map<BlockPos, Integer> emission = new HashMap<>();

    public DynamicLightTestView(LevelHeightAccessor height) {
        this.height = height;
    }

    public void put(BlockPos pos, BlockState state, int light) {
        this.blocks.put(pos.immutable(), state);
        this.setEmission(pos, light);
    }

    public void setEmission(BlockPos pos, int light) {
        this.emission.put(pos.immutable(), light);
    }

    public void forEachBlock(BiConsumer<BlockPos, BlockState> consumer) {
        this.blocks.forEach(consumer);
    }

    @Override public int emissionAt(BlockPos pos) { return this.emission.getOrDefault(pos, 0); }
    @Override public BlockState getBlockState(BlockPos pos) {
        return this.blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }
    @Override public FluidState getFluidState(BlockPos pos) { return this.getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    @Override public int getHeight() { return this.height.getHeight(); }
    @Override public int getMinY() { return this.height.getMinY(); }
    @Override public LevelLightEngine getLightEngine() { return null; }
    @Override public int getBrightness(LightLayer layer, BlockPos pos) { return 0; }
}
