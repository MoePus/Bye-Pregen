package com.moepus.byepregen.mixin.worldgen.feature;

import com.moepus.byepregen.ConfigFlag;
import com.moepus.byepregen.MixinGate;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(config = ConfigFlag.DISABLE_WORLDGEN_FEATURES)
@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksDebugDisableFeaturesMixin {
    @Redirect(
            method = "generateFeatures",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;applyBiomeDecoration("
                            + "Lnet/minecraft/world/level/WorldGenLevel;"
                            + "Lnet/minecraft/world/level/chunk/ChunkAccess;"
                            + "Lnet/minecraft/world/level/StructureManager;)V"
            )
    )
    private static void byepregen$skipFeatures(
            ChunkGenerator generator,
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager
    ) {
    }
}
