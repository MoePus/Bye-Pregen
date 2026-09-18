package com.moepus.byepregen.mixin.surface;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.config.ConfigManager;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Decides who runs the surface step. 26.3 hands it from the generator's own buildSurface to
 * {@link MaterialSystem#buildSurface}, which is where a wrapper installed by another mod would sit: while
 * our surface paths are enabled we call the material system directly so that wrapper cannot replace our
 * work, and while they are disabled we leave the call exactly as it was.
 */
@MixinGate
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class SurfaceBuildOwnershipMixin {
    @Unique
    private static final int byepregen$SURFACE_WRAP_ORDER = 1100;

    @WrapOperation(
            method = "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;"
                    + "Lnet/minecraft/world/level/levelgen/NoiseChunk;"
                    + "Lnet/minecraft/world/level/levelgen/RandomState;"
                    + "Lnet/minecraft/world/level/biome/BiomeManager;"
                    + "Ljava/util/Set;"
                    + "Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/material/MaterialSystem;"
                            + "buildSurface(Lnet/minecraft/world/level/levelgen/RandomState;"
                            + "Lnet/minecraft/world/level/biome/BiomeManager;"
                            + "Lnet/minecraft/world/level/levelgen/WorldGenerationContext;"
                            + "Lnet/minecraft/world/level/chunk/ChunkAccess;"
                            + "Lnet/minecraft/world/level/levelgen/NoiseChunk;"
                            + "Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;"
                            + "Ljava/util/Set;)V"
            ),
            order = byepregen$SURFACE_WRAP_ORDER
    )
    private void byepregen$ownSurfaceBuild(
            MaterialSystem materialSystem,
            RandomState randomState,
            BiomeManager biomeManager,
            WorldGenerationContext context,
            ChunkAccess chunk,
            NoiseChunk noiseChunk,
            MaterialRule rule,
            Set<Holder<Biome>> possibleBiomes,
            Operation<Void> original
    ) {
        Config.Surface surface = ConfigManager.getConfig().worldgen().surface();
        if (!surface.ruleCompiler() && !surface.biomeCache()) {
            original.call(
                    materialSystem,
                    randomState,
                    biomeManager,
                    context,
                    chunk,
                    noiseChunk,
                    rule,
                    possibleBiomes
            );
            return;
        }

        materialSystem.buildSurface(
                randomState,
                biomeManager,
                context,
                chunk,
                noiseChunk,
                rule,
                possibleBiomes
        );
    }
}
