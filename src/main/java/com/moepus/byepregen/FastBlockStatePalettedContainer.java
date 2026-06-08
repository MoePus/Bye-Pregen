package com.moepus.byepregen;

import net.minecraft.core.IdMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jetbrains.annotations.NotNull;

public final class FastBlockStatePalettedContainer extends FastPalettedContainer<BlockState> {
    public FastBlockStatePalettedContainer(IdMap<BlockState> idList, BlockState defaultValue, Strategy strategy) {
        super(idList, defaultValue, strategy);
    }

    public FastBlockStatePalettedContainer(PalettedContainer<BlockState> source) {
        super(source);
    }

    public static PalettedContainer<BlockState> wrap(PalettedContainer<BlockState> container) {
        return container.getClass() == PalettedContainer.class ? new FastBlockStatePalettedContainer(container) : container;
    }

    @Override
    public @NotNull BlockState get(int x, int y, int z) {
        return this.getFast((y << 8) | (z << 4) | x);
    }

    @Override
    protected @NotNull BlockState get(int index) {
        return this.getFast(index);
    }

    @Override
    public @NotNull BlockState getAndSet(int x, int y, int z, @NotNull BlockState state) {
        return this.getAndSetFast((y << 8) | (z << 4) | x, state);
    }

    @Override
    public @NotNull BlockState getAndSetUnchecked(int x, int y, int z, @NotNull BlockState state) {
        return this.getAndSetFast((y << 8) | (z << 4) | x, state);
    }

    @Override
    public void set(int x, int y, int z, BlockState state) {
        this.setFast((y << 8) | (z << 4) | x, state);
    }

    @Override
    public @NotNull PalettedContainer<BlockState> recreate() {
        return new FastBlockStatePalettedContainer(this.registry, this.fastData.palette().valueFor(0), this.strategy);
    }
}
