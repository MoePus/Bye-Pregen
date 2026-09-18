package com.moepus.byepregen.arenatest.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.moepus.byepregen.arenatest.ArenaRuntimeVerifier;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class TerrainSnapshotMixin {
    @Shadow @Final private Holder<NoiseGeneratorSettings> settings;

    @ModifyReturnValue(method = "buildTerrain", at = @At("RETURN"))
    private CompletableFuture<ChunkAccess> byepregen$snapshotTerrain(CompletableFuture<ChunkAccess> future) {
        String key = this.settings.unwrapKey().orElseThrow().identifier().toString();
        return future.thenApply(chunk -> {
            ArenaRuntimeVerifier.recordTerrain(key, chunk);
            return chunk;
        });
    }
}
