package com.moepus.byepregen.mixin.accessor;

import java.io.IOException;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RegionFileStorage.class)
public interface RegionFileStorageAccessor {
    @Invoker("getRegionFile")
    RegionFile byepregen$getRegionFile(ChunkPos pos) throws IOException;
}
