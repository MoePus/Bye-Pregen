package com.moepus.byepregen.mixin.surface.compat.fastnoise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.config.ConfigManager;
import net.minecraft.core.Registry;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@MixinGate(requiredMods = "zfastnoise")
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class FastNoiseSurfaceOwnershipMixin {
    @Unique
    private static final int byepregen$SURFACE_WRAP_ORDER = 1100;

    @WrapOperation(
            method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;"
                    + "Lnet/minecraft/world/level/StructureManager;"
                    + "Lnet/minecraft/world/level/levelgen/RandomState;"
                    + "Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseBasedChunkGenerator;"
                            + "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;"
                            + "Lnet/minecraft/world/level/levelgen/WorldGenerationContext;"
                            + "Lnet/minecraft/world/level/levelgen/RandomState;"
                            + "Lnet/minecraft/world/level/StructureManager;"
                            + "Lnet/minecraft/world/level/biome/BiomeManager;"
                            + "Lnet/minecraft/core/Registry;"
                            + "Lnet/minecraft/world/level/levelgen/blending/Blender;)V"
            ),
            order = byepregen$SURFACE_WRAP_ORDER
    )
    private static void byepregen$selectSurfaceImplementation(
            NoiseBasedChunkGenerator generator,
            ChunkAccess chunk,
            WorldGenerationContext context,
            RandomState randomState,
            StructureManager structureManager,
            BiomeManager biomeManager,
            Registry<Biome> biomeRegistry,
            Blender blender,
            Operation<Void> original
    ) {
        Config.Surface surface = ConfigManager.getConfig().worldgen().surface();
        if (!surface.ruleCompiler() && !surface.biomeCache()) {
            // Preserve the wrapper chain so FastNoise can optimize when our surface paths are disabled.
            original.call(
                    generator,
                    chunk,
                    context,
                    randomState,
                    structureManager,
                    biomeManager,
                    biomeRegistry,
                    blender
            );
            return;
        }

        generator.buildSurface(
                chunk,
                context,
                randomState,
                structureManager,
                biomeManager,
                biomeRegistry,
                blender
        );
    }
}
