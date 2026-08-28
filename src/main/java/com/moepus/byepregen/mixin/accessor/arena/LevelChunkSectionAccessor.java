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
    @Mutable
    @Accessor("states")
    void byepregen$setStates(PalettedContainer<BlockState> states);

    @Accessor("biomes")
    void byepregen$setBiomes(PalettedContainerRO<Holder<Biome>> biomes);
}
