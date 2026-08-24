package com.moepus.byepregen.mixin.accessor.server.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ChunkMap.class, remap = false)
public interface ChunkMapUnloadAccessor {
    @Accessor("pendingUnloads")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> byepregen$getPendingUnloads();
}
