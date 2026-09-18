package com.moepus.byepregen.arenatest.mixin;

import com.moepus.byepregen.dfc.runtime.CompiledDensitySampler;
import com.moepus.byepregen.dfctest.DfcRuntimeVerifier;
import com.moepus.byepregen.dfc.runtime.ColumnSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CompiledDensitySampler.class, remap = false)
public abstract class DfcSamplerProbeMixin {
    @Inject(method = "sampleVolume", at = @At("HEAD"))
    private void byepregen$volume(CallbackInfo callback) { DfcRuntimeVerifier.recordVolume(); }
    @Inject(method = "openColumns", at = @At("HEAD"))
    private void byepregen$column(CallbackInfoReturnable<ColumnSession> callback) { DfcRuntimeVerifier.recordColumn(); }
    @Inject(method = "sampleValue", at = @At("HEAD"))
    private void byepregen$point(CallbackInfoReturnable<Float> callback) { DfcRuntimeVerifier.recordPoint(); }
}
