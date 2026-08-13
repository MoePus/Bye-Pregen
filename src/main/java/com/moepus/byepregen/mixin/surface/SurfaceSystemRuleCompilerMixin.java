package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.moepus.byepregen.worldgen.surface.SurfaceBoundedStoneDepthRule;
import com.moepus.byepregen.worldgen.surface.SurfaceTemplateCache;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(feature = MixinFeature.SURFACE_RULE_COMPILER)
@Mixin(SurfaceSystem.class)
public abstract class SurfaceSystemRuleCompilerMixin {
    @Unique
    private final SurfaceTemplateCache byepregen$buildSurfaceRules =
            new SurfaceTemplateCache();

    @Redirect(
            method = "buildSurface",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;apply(Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private Object byepregen$bindBuildRule(
            SurfaceRules.RuleSource source,
            Object context,
            @Share("byepregen$boundedStoneDepthBelow") LocalBooleanRef boundedStoneDepthBelow
    ) {
        Object rule = this.byepregen$buildSurfaceRules.bind(source, context);
        boundedStoneDepthBelow.set(
                rule instanceof SurfaceBoundedStoneDepthRule && rule.getClass().isHidden()
        );
        return rule;
    }

    @ModifyConstant(
            method = "buildSurface",
            constant = @Constant(intValue = Integer.MAX_VALUE),
            require = 1,
            allow = 1
    )
    private int byepregen$skipExactStoneDepthBelow(
            int original,
            @Share("byepregen$boundedStoneDepthBelow") LocalBooleanRef boundedStoneDepthBelow
    ) {
        return boundedStoneDepthBelow.get() ? DimensionType.WAY_BELOW_MIN_Y : original;
    }
}
