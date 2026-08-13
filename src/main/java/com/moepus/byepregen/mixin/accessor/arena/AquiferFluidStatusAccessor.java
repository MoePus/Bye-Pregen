package com.moepus.byepregen.mixin.accessor.arena;

import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Aquifer.FluidStatus.class)
public interface AquiferFluidStatusAccessor {
    @Accessor("fluidLevel")
    int byepregen$getFluidLevel();
}
