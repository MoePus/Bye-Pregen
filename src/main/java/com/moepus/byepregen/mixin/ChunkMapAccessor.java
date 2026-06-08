package com.moepus.byepregen.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ChunkMap.class, remap = false)
public interface ChunkMapAccessor {
    @Invoker("getVisibleChunkIfPresent")
    ChunkHolder byepregen$invokeGetVisibleChunkIfPresent(long chunkPos);
}
