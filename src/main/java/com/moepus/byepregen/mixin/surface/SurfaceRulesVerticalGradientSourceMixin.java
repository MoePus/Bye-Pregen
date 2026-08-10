package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.worldgen.surface.SurfaceRuleSourceAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

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
