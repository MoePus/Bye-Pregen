package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.worldgen.surface.SurfaceBoundAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Condition")
public interface SurfaceRulesBoundConditionMixin extends SurfaceBoundAccess.Condition {
}
