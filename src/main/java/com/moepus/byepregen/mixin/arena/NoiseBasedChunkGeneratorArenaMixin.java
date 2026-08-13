package com.moepus.byepregen.mixin.arena;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.llamalad7.mixinextras.sugar.Local;
import com.moepus.byepregen.mixin.accessor.arena.AquiferFluidStatusAccessor;
import com.moepus.byepregen.worldgen.arena.ArenaNoiseFiller;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
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
@MixinGate(feature = MixinFeature.ARENA)
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorArenaMixin {
    @Shadow
    @Final
    private Holder<NoiseGeneratorSettings> settings;

    @Shadow
    @Final
    private Supplier<Aquifer.FluidPicker> globalFluidPicker;

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
        NoiseGeneratorSettings generatorSettings = this.settings.value();
        ArenaNoiseFiller.Request request = new ArenaNoiseFiller.Request(
                chunk,
                generatorSettings.defaultBlock(),
                Math.floorDiv(noiseSettings.minY(), cellHeight),
                Math.floorDiv(noiseSettings.height(), cellHeight),
                this.byepregen$globalFluidUpperBound()
        );
        ArenaNoiseFiller.TargetSections targets = ArenaNoiseFiller.targetSections(request, cellHeight);
        return () -> {
            ChunkAccess targetChunk = request.chunk();
            if (SharedConstants.debugVoidTerrain(targetChunk.getPos())) {
                return targetChunk;
            }
            if (!ArenaNoiseFiller.hasFreshAirTargets(request, targets)) {
                return original.get();
            }
            NoiseChunk noiseChunk = targetChunk.getOrCreateNoiseChunk(
                    target -> this.createNoiseChunk(target, structureManager, blender, randomState)
            );
            return ArenaNoiseFiller.fill(noiseChunk, request, targets);
        };
    }

    private int byepregen$globalFluidUpperBound() {
        Aquifer.FluidPicker fluidPicker = this.globalFluidPicker.get();
        if (!byepregen$isVanillaFluidPicker(fluidPicker)) {
            return Integer.MAX_VALUE;
        }
        Aquifer.FluidStatus lower = fluidPicker.computeFluid(0, Integer.MIN_VALUE, 0);
        Aquifer.FluidStatus upper = fluidPicker.computeFluid(0, Integer.MAX_VALUE, 0);
        return Math.max(
                ((AquiferFluidStatusAccessor) (Object) lower).byepregen$getFluidLevel(),
                ((AquiferFluidStatusAccessor) (Object) upper).byepregen$getFluidLevel()
        );
    }

    private static boolean byepregen$isVanillaFluidPicker(Aquifer.FluidPicker fluidPicker) {
        Class<?> type = fluidPicker.getClass();
        return type.isHidden()
                && type.isSynthetic()
                && type.getNestHost() == NoiseBasedChunkGenerator.class;
    }
}
