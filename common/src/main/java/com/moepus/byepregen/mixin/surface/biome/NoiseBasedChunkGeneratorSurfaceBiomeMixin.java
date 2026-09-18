package com.moepus.byepregen.mixin.surface.biome;

import com.llamalad7.mixinextras.sugar.Local;
import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.biome.SurfaceBiomeManager;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@MixinGate(feature = MixinFeature.SURFACE_BIOME_CACHE)
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorSurfaceBiomeMixin {
    @ModifyVariable(method = "buildTerrain", at = @At("HEAD"), argsOnly = true)
    private BiomeManager byepregen$cacheSurfaceBiomes(BiomeManager manager,
            @Local(argsOnly = true) ChunkAccess chunk, @Local(argsOnly = true) WorldGenRegion region) {
        return SurfaceBiomeManager.wrapForSurface(manager, chunk, region);
    }
}
