package com.moepus.byepregen.mixin.accessor;

import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ChunkStorage.class, remap = false)
public interface ChunkStorageAccessor {
    @Invoker("storageInfo")
    RegionStorageInfo byepregen$storageInfo();
}
