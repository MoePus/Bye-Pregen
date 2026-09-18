package com.moepus.byepregen.mixin.surface;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.surface.SurfaceBoundedStoneDepthRule;
import com.moepus.byepregen.worldgen.surface.SurfaceTemplateCache;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(feature = MixinFeature.SURFACE_RULE_COMPILER)
@Mixin(MaterialSystem.class)
public abstract class MaterialSystemRuleCompilerMixin {
    @Unique
    private final SurfaceTemplateCache byepregen$surfaceRules = new SurfaceTemplateCache();

    @Redirect(method = {"buildSurface", "topMaterial"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;compile(Lnet/minecraft/world/level/levelgen/material/MaterialRuleContext;)Lnet/minecraft/world/level/levelgen/material/rule/RuleEvaluator;"))
    private RuleEvaluator byepregen$compileMaterial(MaterialRule rule, MaterialRuleContext context,
            @Share("byepregen$boundedStoneDepth") LocalBooleanRef boundedStoneDepth) {
        Object compiled = this.byepregen$surfaceRules.bind(rule, context);
        boundedStoneDepth.set(compiled instanceof SurfaceBoundedStoneDepthRule
                && compiled.getClass().isHidden());
        return (RuleEvaluator) compiled;
    }

    /**
     * The column scan seeds its ceiling candidate with {@code Integer.MAX_VALUE} and only looks for
     * the real value when it is still at or above the current block. A generated rule that never
     * reads the stone depth below has nothing to look for, so seeding the sentinel's lower bound
     * skips the scan; the constant is also the only match in the method.
     */
    @ModifyConstant(method = "buildSurface", constant = @Constant(intValue = Integer.MAX_VALUE), require = 1, allow = 1)
    private int byepregen$boundStoneDepthScan(int original,
            @Share("byepregen$boundedStoneDepth") LocalBooleanRef boundedStoneDepth) {
        return boundedStoneDepth.get() ? DimensionType.WAY_BELOW_MIN_Y : original;
    }
}
