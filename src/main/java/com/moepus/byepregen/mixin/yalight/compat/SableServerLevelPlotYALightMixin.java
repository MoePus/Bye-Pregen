package com.moepus.byepregen.mixin.yalight.compat;

import com.llamalad7.mixinextras.sugar.Local;
import com.moepus.byepregen.yalight.compat.SableYALightCompat;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = ServerLevelPlot.class, remap = false)
public abstract class SableServerLevelPlotYALightMixin {
    @Shadow
    @Final
    protected LevelLightEngine lightEngine;

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;<init>(Lnet/minecraft/world/level/chunk/LightChunkGetter;ZZ)V"
            ),
            index = 1
    )
    private boolean byepregen$useParentYABlockLayer(boolean original, @Local(ordinal = 0) LevelLightEngine parentLightEngine) {
        return SableYALightCompat.hasBlockLight(parentLightEngine, original);
    }

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;<init>(Lnet/minecraft/world/level/chunk/LightChunkGetter;ZZ)V"
            ),
            index = 2
    )
    private boolean byepregen$useParentYASkyLayer(boolean original, @Local(ordinal = 0) LevelLightEngine parentLightEngine) {
        return SableYALightCompat.hasSkyLight(parentLightEngine, original);
    }

    @ModifyVariable(
            method = "load",
            at = @At("STORE"),
            index = 28,
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;blockEngine:Lnet/minecraft/world/level/lighting/LightEngine;"
                    ),
                    to = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;skyEngine:Lnet/minecraft/world/level/lighting/LightEngine;"
                    )
            )
    )
    private boolean byepregen$loadYABlockLight(boolean original, @Local(name = "sectionTag") CompoundTag sectionTag) {
        return original || (SableYALightCompat.hasBlockLight(this.lightEngine, false)
                && sectionTag.contains("BlockLight", Tag.TAG_BYTE_ARRAY));
    }

    @ModifyVariable(
            method = "load",
            at = @At("STORE"),
            index = 29,
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;skyEngine:Lnet/minecraft/world/level/lighting/LightEngine;"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;retainData(Lnet/minecraft/world/level/ChunkPos;Z)V"
                    )
            )
    )
    private boolean byepregen$loadYASkyLight(
            boolean original,
            @Local(name = "sectionTag") CompoundTag sectionTag,
            @Local(name = "level") ServerLevel level
    ) {
        return original || (SableYALightCompat.hasSkyLight(this.lightEngine, false)
                && level.dimensionType().hasSkyLight()
                && sectionTag.contains("SkyLight", Tag.TAG_BYTE_ARRAY));
    }
}
