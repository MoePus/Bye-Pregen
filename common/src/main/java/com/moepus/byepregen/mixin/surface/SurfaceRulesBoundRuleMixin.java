package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.surface.SurfaceBoundAccess;
import org.spongepowered.asm.mixin.Mixin;

@MixinGate(feature = MixinFeature.SURFACE_RULE_COMPILER)
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$SurfaceRule")
public interface SurfaceRulesBoundRuleMixin extends SurfaceBoundAccess.Rule {
}
