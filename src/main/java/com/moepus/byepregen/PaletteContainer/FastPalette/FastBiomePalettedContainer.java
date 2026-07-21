package com.moepus.byepregen.PaletteContainer.FastPalette;

import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.jetbrains.annotations.NotNull;
import com.moepus.byepregen.mixin.accessor.PalettedContainerAccessor;

public final class FastBiomePalettedContainer extends FastPalettedContainer<Holder<Biome>> {
    public FastBiomePalettedContainer(IdMap<Holder<Biome>> idList, Holder<Biome> defaultValue, Strategy strategy) {
        super(idList, defaultValue, strategy);
    }

    public FastBiomePalettedContainer(PalettedContainer<Holder<Biome>> source) {
        super(source);
    }

    public static PalettedContainerRO<Holder<Biome>> wrap(PalettedContainerRO<Holder<Biome>> container) {
        if (container instanceof PalettedContainer<?> palettedContainer) {
            if (palettedContainer.getClass() != PalettedContainer.class) {
                return container;
            }

            @SuppressWarnings("unchecked")
            PalettedContainer<Holder<Biome>> typedContainer = (PalettedContainer<Holder<Biome>>) palettedContainer;
            return new FastBiomePalettedContainer(typedContainer);
        }

        return container;
    }

    @Override
    public @NotNull Holder<Biome> get(int x, int y, int z) {
        return this.getFast((y << 4) | (z << 2) | x);
    }

    @Override
    protected @NotNull Holder<Biome> get(int index) {
        return this.getFast(index);
    }

    @Override
    public @NotNull Holder<Biome> getAndSet(int x, int y, int z, @NotNull Holder<Biome> biome) {
        return this.getAndSetFast((y << 4) | (z << 2) | x, biome);
    }

    @Override
    public @NotNull Holder<Biome> getAndSetUnchecked(int x, int y, int z, @NotNull Holder<Biome> biome) {
        return this.getAndSetFast((y << 4) | (z << 2) | x, biome);
    }

    @Override
    public void set(int x, int y, int z, @NotNull Holder<Biome> biome) {
        this.setFast((y << 4) | (z << 2) | x, biome);
    }

    @Override
    public @NotNull PalettedContainer<Holder<Biome>> recreate() {
        PalettedContainerAccessor<Holder<Biome>> accessor = (PalettedContainerAccessor<Holder<Biome>>) (Object) this;
        return new FastBiomePalettedContainer(
                accessor.byepregen$getRegistry(), this.fastData.palette().valueFor(0), accessor.byepregen$getStrategy());
    }
}
