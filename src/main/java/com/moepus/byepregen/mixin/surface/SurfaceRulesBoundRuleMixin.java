package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.worldgen.surface.SurfaceBoundAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$SurfaceRule")
public interface SurfaceRulesBoundRuleMixin extends SurfaceBoundAccess.Rule {
}
