package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.surface.SurfaceRuleSourceAccess;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@MixinGate(feature = MixinFeature.SURFACE_RULE_COMPILER)
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$StoneDepthCheck")
public interface SurfaceRulesStoneDepthSourceMixin extends SurfaceRuleSourceAccess.StoneDepthCondition {
    @Override
    @Accessor("offset")
    int byepregen$offset();

    @Override
    @Accessor("addSurfaceDepth")
    boolean byepregen$addSurfaceDepth();

    @Override
    @Accessor("secondaryDepthRange")
    int byepregen$secondaryDepthRange();

    @Override
    @Accessor("surfaceType")
    CaveSurface byepregen$surfaceType();
}
