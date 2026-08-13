package com.moepus.byepregen.mixin.accessor.arena;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@MixinGate(feature = MixinFeature.ARENA)
@Mixin(Aquifer.FluidStatus.class)
public interface AquiferFluidStatusAccessor {
    @Accessor("fluidLevel")
    int byepregen$getFluidLevel();
}
