package com.moepus.byepregen.arenatest.mixin;

import com.moepus.byepregen.featuretest.FeatureRuntimeVerifier;
import com.moepus.byepregen.worldgen.feature.PredicateMemoizedDiskPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PredicateMemoizedDiskPlacement.class, remap = false)
public abstract class FeaturePlanProbeMixin {
    @Inject(method = "placeOrigin", at = @At("HEAD"))
    private void byepregen$recordDisk(CallbackInfo callback) {
        FeatureRuntimeVerifier.recordMemoizedDisk();
    }
}
