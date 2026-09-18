package com.moepus.byepregen.mixin.surface.biome;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.biome.SurfaceBiomeManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@MixinGate(feature = MixinFeature.SURFACE_BIOME_CACHE)
@Mixin(MaterialSystem.class)
public abstract class SurfaceSystemBiomeCacheMixin {
    @InjectLite(method = "buildSurface", at = @At("RETURN"))
    private void byepregen$commitSurfaceBiomeProfile(
            RandomState randomState,
            BiomeManager biomeManager
    ) {
        if (SurfaceBiomeManager.profilingEnabled()) {
            SurfaceBiomeManager.commitProfile(biomeManager);
        }
    }
}
