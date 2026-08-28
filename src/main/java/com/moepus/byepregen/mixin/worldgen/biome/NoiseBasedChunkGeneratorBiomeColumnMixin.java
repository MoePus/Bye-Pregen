package com.moepus.byepregen.mixin.worldgen.biome;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.biome.BiomeColumnEvaluator;
import com.moepus.byepregen.worldgen.biome.BiomeColumnFiller;
import net.minecraft.core.Holder;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@MixinGate(
        feature = MixinFeature.DFC,
        conflictingMods = {"reterraforged", "terrablender", "blueprint"}
)
@Mixin(value = NoiseBasedChunkGenerator.class, priority = 1100)
public abstract class NoiseBasedChunkGeneratorBiomeColumnMixin extends ChunkGenerator {
    @Shadow @Final private Holder<NoiseGeneratorSettings> settings;

    protected NoiseBasedChunkGeneratorBiomeColumnMixin(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Shadow
    protected abstract NoiseChunk createNoiseChunk(
            ChunkAccess chunk,
            StructureManager structureManager,
            Blender blender,
            RandomState randomState
    );

    @InjectLite(
            method = "doCreateBiomes",
            at = @At("HEAD"),
            cancel = true
    )
    private void byepregen$fillBiomeColumns(
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk
    ) {
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(
                target -> this.createNoiseChunk(target, structureManager, blender, randomState));
        BiomeResolver resolver = BelowZeroRetrogen.getBiomeResolver(
                blender.getBiomeResolver(this.biomeSource), chunk);
        BiomeColumnEvaluator evaluator = (BiomeColumnEvaluator) noiseChunk;
        Climate.Sampler sampler = evaluator.byepregen$climateSampler(
                randomState.router(), this.settings.value().spawnTarget());
        if (BiomeColumnFiller.fill(new BiomeColumnFiller.Options(
                chunk, resolver, this.biomeSource, sampler, evaluator))) {
            return;
        }
        chunk.fillBiomesFromNoise(resolver, sampler);
    }
}
