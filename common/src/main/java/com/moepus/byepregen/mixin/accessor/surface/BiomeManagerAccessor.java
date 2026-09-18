package com.moepus.byepregen.mixin.accessor.surface;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@MixinGate(feature = MixinFeature.SURFACE_BIOME_CACHE)
@Mixin(BiomeManager.class)
public interface BiomeManagerAccessor {
    @Accessor("noiseBiomeSource")
    BiomeResolver byepregen$getNoiseBiomeSource();

    @Accessor("biomeZoomSeed")
    long byepregen$getBiomeZoomSeed();
}
