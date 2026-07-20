package com.moepus.byepregen.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Accessor("level")
    ServerLevel byepregen$getLevel();

    @Accessor("mainThreadExecutor")
    BlockableEventLoop<Runnable> byepregen$getMainThreadExecutor();

    @Invoker("getVisibleChunkIfPresent")
    ChunkHolder byepregen$invokeGetVisibleChunkIfPresent(long chunkPos);
}
