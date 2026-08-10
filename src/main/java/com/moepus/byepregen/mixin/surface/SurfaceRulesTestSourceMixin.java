package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.worldgen.surface.SurfaceRuleSourceAccess;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$TestRuleSource")
public interface SurfaceRulesTestSourceMixin extends SurfaceRuleSourceAccess.Test {
    @Override
    @Accessor("ifTrue")
    SurfaceRules.ConditionSource byepregen$condition();

    @Override
    @Accessor("thenRun")
    SurfaceRules.RuleSource byepregen$followup();
}
