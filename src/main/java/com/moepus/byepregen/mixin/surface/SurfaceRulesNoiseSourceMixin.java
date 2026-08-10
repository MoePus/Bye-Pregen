package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.worldgen.surface.SurfaceRuleSourceAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$NoiseThresholdConditionSource")
public interface SurfaceRulesNoiseSourceMixin extends SurfaceRuleSourceAccess.NoiseCondition {
    @Override
    @Accessor("noise")
    ResourceKey<NormalNoise.NoiseParameters> byepregen$noise();

    @Override
    @Accessor("minThreshold")
    double byepregen$minimum();

    @Override
    @Accessor("maxThreshold")
    double byepregen$maximum();
}
