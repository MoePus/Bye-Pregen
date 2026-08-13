package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.surface.SurfaceRuleSourceAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@MixinGate(feature = MixinFeature.SURFACE_RULE_COMPILER)
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$WaterConditionSource")
public interface SurfaceRulesWaterSourceMixin extends SurfaceRuleSourceAccess.WaterCondition {
    @Override
    @Accessor("offset")
    int byepregen$offset();

    @Override
    @Accessor("surfaceDepthMultiplier")
    int byepregen$surfaceDepthMultiplier();

    @Override
    @Accessor("addStoneDepth")
    boolean byepregen$addStoneDepth();
}
