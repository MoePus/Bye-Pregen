package com.moepus.byepregen.mixin.surface;

import java.lang.invoke.MethodHandles;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SurfaceRules.class)
public abstract class SurfaceRulesLookupMixin {
    @Unique
    private static MethodHandles.Lookup byepregen$lookup() {
        return MethodHandles.lookup();
    }
}
