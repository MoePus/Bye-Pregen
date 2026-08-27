package com.moepus.byepregen.mixin.worldgen.noise;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(feature = MixinFeature.ARENA_OR_DFC)
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$CacheAllInCell", priority = 1200)
public abstract class NoiseChunkCellCacheBypassMixin {
    // Arena never drives vanilla's fillingCell/arrayIndex lifecycle, and the dedicated
    // column graph removes this wrapper too. One unconditional delegate path prevents
    // either context from observing stale cell-cache entries.
    @Redirect(
            method = "compute",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseChunk$CacheAllInCell;this$0:"
                            + "Lnet/minecraft/world/level/levelgen/NoiseChunk;",
                    ordinal = 0
            )
    )
    private NoiseChunk byepregen$bypassCellCache(@Coerce Object cache) {
        return null;
    }
}
