package com.moepus.byepregen.mixin;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GenerationChunkHolder.class, remap = false)
public interface ChunkHolderAccessor {
    @Accessor("currentlyLoading")
    LevelChunk byepregen$getCurrentlyLoading();
}
