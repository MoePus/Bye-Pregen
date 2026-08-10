package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.worldgen.surface.SurfaceRuleSourceAccess;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$NotConditionSource")
public interface SurfaceRulesNotSourceMixin extends SurfaceRuleSourceAccess.NotCondition {
    @Override
    @Accessor("target")
    SurfaceRules.ConditionSource byepregen$target();
}
