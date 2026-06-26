package com.moepus.byepregen.mixin.compat;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess", remap = false)
public abstract class SodiumLightDataAccessYALightMixin {
    @Redirect(
            method = "compute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I"
            )
    )
    private int byepregen$getSnapshotLightColor(BlockAndTintGetter level, BlockState state, BlockPos pos) {
        int sky = level.getBrightness(LightLayer.SKY, pos);
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        if (state.isAir()) {
            return sky << 20 | block << 4;
        }
        int emission = state.getLightEmission(level, pos);
        if (block < emission) {
            block = emission;
        }
        return sky << 20 | block << 4;
    }
}
