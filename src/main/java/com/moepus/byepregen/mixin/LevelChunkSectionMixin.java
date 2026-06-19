package com.moepus.byepregen.mixin;

import com.moepus.byepregen.FastBiomePalettedContainer;
import com.moepus.byepregen.FastBlockStatePalettedContainer;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = LevelChunkSection.class, remap = false)
public abstract class LevelChunkSectionMixin {
    @ModifyVariable(
            method = "<init>(Lnet/minecraft/world/level/chunk/PalettedContainer;Lnet/minecraft/world/level/chunk/PalettedContainerRO;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private static PalettedContainerRO<Holder<Biome>> byepregen$wrapBiomeContainer(PalettedContainerRO<Holder<Biome>> biomes) {
        return FastBiomePalettedContainer.wrap(biomes);
    }
}
