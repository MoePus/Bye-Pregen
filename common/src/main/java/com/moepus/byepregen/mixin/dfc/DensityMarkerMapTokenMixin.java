package com.moepus.byepregen.mixin.dfc;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.arena.InterpolatedMarkerAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Propagates marker provenance through vanilla's default MarkerOrMarked.mapAll. */
@MixinGate(feature = MixinFeature.DFC)
@Mixin(value = DensityFunctions.MarkerOrMarked.class, priority = 500)
public interface DensityMarkerMapTokenMixin {
    @ModifyArg(
            method = "mapAll",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;apply("
                            + "Lnet/minecraft/world/level/levelgen/DensityFunction;)"
                            + "Lnet/minecraft/world/level/levelgen/DensityFunction;"
            ),
            index = 0
    )
    private DensityFunction byepregen$copyInterpolationToken(DensityFunction mapped) {
        if ((Object) this instanceof InterpolatedMarkerAccess source
                && mapped instanceof InterpolatedMarkerAccess target) {
            Object token = source.byepregen$getInterpolationToken();
            if (token != null) target.byepregen$setInterpolationToken(token);
        }
        return mapped;
    }
}
