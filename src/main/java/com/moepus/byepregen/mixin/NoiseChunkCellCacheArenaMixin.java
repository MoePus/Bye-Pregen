package com.moepus.byepregen.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moepus.byepregen.worldgen.ArenaCellCacheAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$CacheAllInCell", priority = 1200)
public abstract class NoiseChunkCellCacheArenaMixin implements ArenaCellCacheAccess {
    @Unique private boolean byepregen$arenaPassthrough;

    @Unique
    @Override
    public void byepregen$setArenaPassthrough(boolean enabled) {
        this.byepregen$arenaPassthrough = enabled;
    }

    @ModifyExpressionValue(
            method = "compute",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseChunk$CacheAllInCell;this$0:"
                            + "Lnet/minecraft/world/level/levelgen/NoiseChunk;",
                    ordinal = 0
            )
    )
    private NoiseChunk byepregen$useArenaPassthrough(NoiseChunk owner) {
        return this.byepregen$arenaPassthrough ? null : owner;
    }
}
