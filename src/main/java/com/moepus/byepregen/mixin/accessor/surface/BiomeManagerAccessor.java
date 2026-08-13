package com.moepus.byepregen.mixin.accessor.surface;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BiomeManager.class)
public interface BiomeManagerAccessor {
    @Accessor("noiseBiomeSource")
    BiomeManager.NoiseBiomeSource byepregen$getNoiseBiomeSource();

    @Accessor("biomeZoomSeed")
    long byepregen$getBiomeZoomSeed();
}
