package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.worldgen.surface.SurfaceTemplateCache;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SurfaceSystem.class)
public abstract class SurfaceSystemRuleCompilerMixin {
    @Unique
    private final SurfaceTemplateCache byepregen$buildSurfaceRules =
            new SurfaceTemplateCache(true);

    @Redirect(
            method = "buildSurface",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;apply(Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private Object byepregen$bindBuildRule(SurfaceRules.RuleSource source, Object context) {
        return this.byepregen$buildSurfaceRules.bind(source, context);
    }
}
