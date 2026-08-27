package com.moepus.byepregen.mixin.accessor.worldgen.feature;

import java.util.BitSet;
import net.minecraft.world.level.chunk.CarvingMask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CarvingMask.class)
public interface CarvingMaskAccessor {
    @Accessor("minY")
    int byepregen$getMinY();

    @Accessor("mask")
    BitSet byepregen$getMask();
}
