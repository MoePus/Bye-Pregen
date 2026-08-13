package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import java.lang.invoke.MethodHandles;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@MixinGate(feature = MixinFeature.SURFACE_RULE_COMPILER)
@Mixin(SurfaceRules.class)
public abstract class SurfaceRulesLookupMixin {
    @Unique
    private static MethodHandles.Lookup byepregen$lookup() {
        return MethodHandles.lookup();
    }
}
