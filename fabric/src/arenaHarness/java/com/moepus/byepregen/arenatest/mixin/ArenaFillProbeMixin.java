package com.moepus.byepregen.arenatest.mixin;

import com.moepus.byepregen.arenatest.ArenaRuntimeVerifier;
import com.moepus.byepregen.arenatest.ArenaMetadataVerifier;
import com.moepus.byepregen.worldgen.terrain.ArenaTerrainFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ArenaTerrainFiller.class, remap = false)
public abstract class ArenaFillProbeMixin {
    @Inject(method = "fill", at = @At("RETURN"))
    private static void byepregen$countFill(net.minecraft.world.level.levelgen.NoiseChunk noise,
            net.minecraft.world.level.chunk.ChunkAccess chunk,
            net.minecraft.world.level.levelgen.NoiseGeneratorSettings settings,
            ArenaTerrainFiller.DebugState debugState, CallbackInfoReturnable<Boolean> callback) {
        ArenaRuntimeVerifier.recordArenaFill(callback.getReturnValue());
        if (callback.getReturnValue()) ArenaMetadataVerifier.verify(chunk);
    }
}
