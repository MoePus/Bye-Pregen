package com.moepus.byepregen.mixin.worldgen.noise;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$CacheAllInCell", priority = 1200)
public abstract class NoiseChunkCellCacheBypassMixin {
    // Arena never drives vanilla's fillingCell/arrayIndex lifecycle, and the dedicated
    // column graph removes this wrapper too. One unconditional delegate path prevents
    // either context from observing stale cell-cache entries.
    @ModifyExpressionValue(
            method = "compute",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseChunk$CacheAllInCell;this$0:"
                            + "Lnet/minecraft/world/level/levelgen/NoiseChunk;",
                    ordinal = 0
            )
    )
    private NoiseChunk byepregen$bypassCellCache(NoiseChunk owner) {
        return null;
    }
}
