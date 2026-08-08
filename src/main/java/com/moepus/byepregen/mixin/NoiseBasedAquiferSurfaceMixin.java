package com.moepus.byepregen.mixin;

import com.moepus.byepregen.worldgen.AquiferSurfaceShortcutAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class NoiseBasedAquiferSurfaceMixin {
    @Shadow protected boolean shouldScheduleFluidUpdate;

    @Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
    private void byepregen$skipHighAirAquifer(
            DensityFunction.FunctionContext context,
            double density,
            CallbackInfoReturnable<BlockState> callback
    ) {
        if (density <= 0.0D
                && context instanceof AquiferSurfaceShortcutAccess shortcut
                && shortcut.byepregen$canSkipAquifer(context.blockY())) {
            // Every candidate FluidStatus is AIR here. Their pair pressure is non-positive,
            // so the full aquifer search cannot change the result away from AIR.
            this.shouldScheduleFluidUpdate = false;
            callback.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
