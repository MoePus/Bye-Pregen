package com.moepus.byepregen.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.moepus.byepregen.worldgen.ArenaNoiseFiller;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Batches vanilla noise writes directly into Arena palettes.
 * The batching approach is inspired by ZenXArch's FastNoise's noise-generation optimization;
 * Have to make this because there would be conflicts which were difficult to handle.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorArenaMixin {
    @Shadow
    @Final
    private Holder<NoiseGeneratorSettings> settings;

    @Shadow
    protected abstract NoiseChunk createNoiseChunk(
            ChunkAccess chunk,
            StructureManager structureManager,
            Blender blender,
            RandomState randomState
    );

    @ModifyArg(
            method = "fillFromNoise",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/Util;wrapThreadWithTaskName(Ljava/lang/String;Ljava/util/function/Supplier;)Ljava/util/function/Supplier;"
            ),
            index = 1
    )
    private Supplier<ChunkAccess> byepregen$replaceNoiseSupplier(
            Supplier<ChunkAccess> original,
            @Local(argsOnly = true) Blender blender,
            @Local(argsOnly = true) RandomState randomState,
            @Local(argsOnly = true) StructureManager structureManager,
            @Local(argsOnly = true) ChunkAccess chunk
    ) {
        if (((Object) this).getClass() != NoiseBasedChunkGenerator.class) {
            return original;
        }

        NoiseSettings noiseSettings = this.settings.value()
                .noiseSettings()
                .clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
        int cellHeight = noiseSettings.getCellHeight();
        ArenaNoiseFiller.Request request = new ArenaNoiseFiller.Request(
                chunk,
                this.settings.value().defaultBlock(),
                Math.floorDiv(noiseSettings.minY(), cellHeight),
                Math.floorDiv(noiseSettings.height(), cellHeight)
        );
        return () -> {
            ChunkAccess targetChunk = request.chunk();
            if (SharedConstants.debugVoidTerrain(targetChunk.getPos())) {
                return targetChunk;
            }
            NoiseChunk noiseChunk = targetChunk.getOrCreateNoiseChunk(
                    target -> this.createNoiseChunk(target, structureManager, blender, randomState)
            );
            return ArenaNoiseFiller.fill(noiseChunk, request);
        };
    }
}
