package com.moepus.byepregen.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NoiseChunk.class)
public interface NoiseChunkAccessor {
    @Invoker("cellWidth")
    int byepregen$cellWidth();

    @Invoker("cellHeight")
    int byepregen$cellHeight();

    @Invoker("getInterpolatedState")
    BlockState byepregen$getInterpolatedState();
}
