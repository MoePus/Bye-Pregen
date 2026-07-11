package com.moepus.byepregen.mixin.compat;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.PaletteContainer.FastPalette.FastPalettedContainerAccess;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@MixinGate(requiredMods = "zfastnoise")
@Mixin(targets = "org.codeberg.zenxarch.fastnoise.noise.FastBiomeGen", remap = false)
public abstract class FastNoiseFastBiomeGenMixin {
    @Inject(
            method = "populateBiomes",
            at = @At("RETURN"),
            remap = false
    )
    private static void byepregen$syncFastBiomePalettedContainer(
            LevelChunkSection section,
            BiomeResolver biomeSupplier,
            Climate.Sampler sampler,
            int x,
            int y,
            int z,
            Holder<Biome>[] biomes,
            byte[] storage,
            CallbackInfo ci
    ) {
        PalettedContainerRO<Holder<Biome>> container = section.getBiomes();
        if (container instanceof PalettedContainer<?> palettedContainer
                && palettedContainer instanceof FastPalettedContainerAccess<?> access) {
            @SuppressWarnings("unchecked")
            PalettedContainer<Holder<Biome>> typedContainer = (PalettedContainer<Holder<Biome>>) palettedContainer;
            @SuppressWarnings("unchecked")
            FastPalettedContainerAccess<Holder<Biome>> typedAccess =
                    (FastPalettedContainerAccess<Holder<Biome>>) access;
            typedAccess.byepregen$updateFastData(typedContainer.data);
        }
    }
}
