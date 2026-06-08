package com.moepus.byepregen.mixin;

import com.moepus.byepregen.FastBiomePalettedContainer;
import com.moepus.byepregen.FastBlockStatePalettedContainer;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = LevelChunkSection.class, remap = false)
public abstract class LevelChunkSectionMixin {
    @ModifyVariable(
            method = "<init>(Lnet/minecraft/world/level/chunk/PalettedContainer;Lnet/minecraft/world/level/chunk/PalettedContainerRO;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1
    )
    private static PalettedContainer<BlockState> byepregen$wrapStateContainer(PalettedContainer<BlockState> states) {
        return FastBlockStatePalettedContainer.wrap(states);
    }

    @ModifyVariable(
            method = "<init>(Lnet/minecraft/world/level/chunk/PalettedContainer;Lnet/minecraft/world/level/chunk/PalettedContainerRO;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private static PalettedContainerRO<Holder<Biome>> byepregen$wrapBiomeContainer(PalettedContainerRO<Holder<Biome>> biomes) {
        return FastBiomePalettedContainer.wrap(biomes);
    }

    @Redirect(
            method = "<init>(Lnet/minecraft/core/Registry;)V",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/core/IdMap;Ljava/lang/Object;Lnet/minecraft/world/level/chunk/PalettedContainer$Strategy;)Lnet/minecraft/world/level/chunk/PalettedContainer;",
                    ordinal = 0
            )
    )
    private PalettedContainer<BlockState> byepregen$createStateContainer(
            IdMap<BlockState> idList,
            Object defaultValue,
            PalettedContainer.Strategy strategy
    ) {
        return new FastBlockStatePalettedContainer(idList, (BlockState) defaultValue, strategy);
    }

    @Redirect(
            method = "<init>(Lnet/minecraft/core/Registry;)V",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/core/IdMap;Ljava/lang/Object;Lnet/minecraft/world/level/chunk/PalettedContainer$Strategy;)Lnet/minecraft/world/level/chunk/PalettedContainer;",
                    ordinal = 1
            )
    )
    @SuppressWarnings("unchecked")
    private PalettedContainer<Holder<Biome>> byepregen$createBiomeContainer(
            IdMap<Holder<Biome>> idList,
            Object defaultValue,
            PalettedContainer.Strategy strategy
    ) {
        return new FastBiomePalettedContainer(idList, (Holder<Biome>) defaultValue, strategy);
    }
}
