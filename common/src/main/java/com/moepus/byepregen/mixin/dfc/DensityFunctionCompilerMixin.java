package com.moepus.byepregen.mixin.dfc;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.dfc.compile.DensitySamplerCompiler;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctionCompiler;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(feature = MixinFeature.DFC)
@Mixin(DensityFunctionCompiler.class)
public abstract class DensityFunctionCompilerMixin {
    @Redirect(method = {"optimizeAndCompile", "prepareCache"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/densityfunction/DensityFunction;compileSampler("
                    + "Lnet/minecraft/world/level/levelgen/densityfunction/DensityFunction$CompileContext;)"
                    + "Lnet/minecraft/world/level/levelgen/densityfunction/DensitySampler;"), require = 2, allow = 2)
    private DensitySampler byepregen$compile(DensityFunction function, DensityFunction.CompileContext context) {
        return DensitySamplerCompiler.compile(function, context);
    }
}
