package com.moepus.byepregen.mixin.accessor.server.tick;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ChunkMap.class, remap = false)
public interface ChunkMapTickAccessor {
    @Invoker("anyPlayerCloseEnoughForSpawning")
    boolean byepregen$anyPlayerCloseEnoughForSpawning(ChunkPos chunkPos);

    @Accessor("visibleChunkMap")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> byepregen$getVisibleChunkMap();
}
