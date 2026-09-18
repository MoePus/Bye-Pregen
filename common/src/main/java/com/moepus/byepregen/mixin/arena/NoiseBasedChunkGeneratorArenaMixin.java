package com.moepus.byepregen.mixin.arena;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.terrain.ArenaTerrainFiller;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retains vanilla's terrain lifetime, locks, surface generation and carvers. */
@MixinGate(feature = MixinFeature.ARENA)
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorArenaMixin {
    @Shadow @Final private Holder<NoiseGeneratorSettings> settings;
    @Shadow protected abstract BlockState debugPreliminarySurfaceLevel(
            NoiseChunk noiseChunk, int x, int y, int z, BlockState state);

    @Inject(method = "doFill", at = @At("HEAD"), cancellable = true)
    private void byepregen$fillArena(NoiseChunk noiseChunk, ChunkAccess chunk, CallbackInfo callback) {
        if (((Object) this).getClass() == NoiseBasedChunkGenerator.class
                && ArenaTerrainFiller.fill(noiseChunk, chunk, this.settings.value(),
                        (x, y, z, state) -> this.debugPreliminarySurfaceLevel(noiseChunk, x, y, z, state))) {
            callback.cancel();
        }
    }
}
