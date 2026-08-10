package com.moepus.byepregen.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.moepus.byepregen.worldgen.SurfaceBiomeManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SurfaceSystem.class)
public abstract class SurfaceSystemBiomeCacheMixin {
    @ModifyVariable(method = "buildSurface", at = @At("HEAD"), argsOnly = true)
    private BiomeManager byepregen$cacheSurfaceBiomes(
            BiomeManager biomeManager,
            @Local(argsOnly = true) ChunkAccess chunk
    ) {
        return SurfaceBiomeManager.wrapForSurface(biomeManager, chunk);
    }

    @Inject(method = "buildSurface", at = @At("RETURN"))
    private void byepregen$commitSurfaceBiomeProfile(
            CallbackInfo callbackInfo,
            @Local(argsOnly = true) BiomeManager biomeManager
    ) {
        if (SurfaceBiomeManager.profilingEnabled()) {
            SurfaceBiomeManager.commitProfile(biomeManager);
        }
    }
}
