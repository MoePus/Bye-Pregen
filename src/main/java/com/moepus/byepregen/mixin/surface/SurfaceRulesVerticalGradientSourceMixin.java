package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.surface.SurfaceRuleSourceAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@MixinGate(feature = MixinFeature.SURFACE_RULE_COMPILER)
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$VerticalGradientConditionSource")
public interface SurfaceRulesVerticalGradientSourceMixin
        extends SurfaceRuleSourceAccess.VerticalGradientCondition {
    @Override
    @Accessor("randomName")
    ResourceLocation byepregen$randomName();

    @Override
    @Accessor("trueAtAndBelow")
    VerticalAnchor byepregen$trueAtAndBelow();

    @Override
    @Accessor("falseAtAndAbove")
    VerticalAnchor byepregen$falseAtAndAbove();
}
