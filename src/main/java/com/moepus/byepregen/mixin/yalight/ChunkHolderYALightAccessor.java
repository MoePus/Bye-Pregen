package com.moepus.byepregen.mixin.yalight;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkHolder.class)
public interface ChunkHolderYALightAccessor {
    @Accessor(value = "currentlyLoading", remap = false)
    LevelChunk byepregen$getCurrentlyLoading();
}
