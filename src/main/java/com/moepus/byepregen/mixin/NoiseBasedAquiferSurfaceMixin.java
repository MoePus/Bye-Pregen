package com.moepus.byepregen.mixin;

import com.moepus.byepregen.worldgen.AquiferSurfaceShortcutAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.mixinlite.injector.InjectLite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class NoiseBasedAquiferSurfaceMixin {
    @Shadow protected boolean shouldScheduleFluidUpdate;

    @InjectLite(method = "computeSubstance", at = @At("HEAD"), cancel = true, cancelOnNonNull = true)
    private BlockState byepregen$skipHighAirAquifer(
            DensityFunction.FunctionContext context,
            double density
    ) {
        if (density <= 0.0D
                && context instanceof AquiferSurfaceShortcutAccess shortcut
                && shortcut.byepregen$canSkipAquifer(context.blockY())) {
            // Every candidate FluidStatus is AIR here. Their pair pressure is non-positive,
            // so the full aquifer search cannot change the result away from AIR.
            this.shouldScheduleFluidUpdate = false;
            return Blocks.AIR.defaultBlockState();
        }
        return null;
    }
}
