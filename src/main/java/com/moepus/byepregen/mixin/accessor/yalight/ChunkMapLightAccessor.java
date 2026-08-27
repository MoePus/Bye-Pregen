package com.moepus.byepregen.mixin.accessor.yalight;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapLightAccessor {
    @Accessor("level")
    ServerLevel byepregen$getLevel();

    @Accessor("mainThreadExecutor")
    BlockableEventLoop<Runnable> byepregen$getMainThreadExecutor();

    @Invoker("releaseLightTicket")
    void byepregen$releaseLightTicket(net.minecraft.world.level.ChunkPos pos);
}
