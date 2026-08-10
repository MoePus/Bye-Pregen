package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.worldgen.surface.SurfaceRuleSourceAccess;
import java.util.List;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$SequenceRuleSource")
public interface SurfaceRulesSequenceSourceMixin extends SurfaceRuleSourceAccess.Sequence {
    @Override
    @Accessor("sequence")
    List<SurfaceRules.RuleSource> byepregen$sequence();
}
