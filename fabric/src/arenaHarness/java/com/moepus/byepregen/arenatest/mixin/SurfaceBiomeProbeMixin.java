package com.moepus.byepregen.arenatest.mixin;

import com.moepus.byepregen.worldgen.biome.SurfaceBiomeManager;
import com.moepus.byepregen.worldgentest.SurfaceRuntimeProbe;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SurfaceBiomeManager.class, remap = false)
public abstract class SurfaceBiomeProbeMixin {
    @Inject(method = "wrapForSurface", at = @At("RETURN"))
    private static void byepregen$verifyCache(BiomeManager original, ChunkAccess chunk, WorldGenRegion region,
                                             CallbackInfoReturnable<BiomeManager> callback) {
        SurfaceRuntimeProbe.verify(original, callback.getReturnValue(), chunk);
    }
}
