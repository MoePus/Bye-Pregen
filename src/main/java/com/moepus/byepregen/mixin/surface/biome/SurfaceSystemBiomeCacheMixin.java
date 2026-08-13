package com.moepus.byepregen.mixin.surface.biome;

import com.llamalad7.mixinextras.sugar.Local;
import com.moepus.byepregen.worldgen.SurfaceBiomeManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SurfaceSystem.class)
public abstract class SurfaceSystemBiomeCacheMixin {
    @ModifyVariable(method = "buildSurface", at = @At("HEAD"), argsOnly = true)
    private BiomeManager byepregen$cacheSurfaceBiomes(
            BiomeManager biomeManager,
            @Local(argsOnly = true) ChunkAccess chunk
    ) {
        return SurfaceBiomeManager.wrapForSurface(biomeManager, chunk);
    }

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
