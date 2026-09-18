package com.moepus.byepregen.mixin.accessor.arena;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = LevelChunkSection.class, remap = false)
public interface LevelChunkSectionAccessor {
    @Accessor("nonEmptyBlockCount")
    void byepregen$setNonEmptyBlockCount(short count);

    @Accessor("fluidCount")
    void byepregen$setFluidCount(short count);

    @Accessor("tickingBlockCount")
    void byepregen$setTickingBlockCount(short count);

    @Accessor("tickingFluidCount")
    void byepregen$setTickingFluidCount(short count);

    @Mutable
    @Accessor("states")
    void byepregen$setStates(PalettedContainer<BlockState> states);

    @Accessor("biomes")
    void byepregen$setBiomes(PalettedContainerRO<Holder<Biome>> biomes);
}
