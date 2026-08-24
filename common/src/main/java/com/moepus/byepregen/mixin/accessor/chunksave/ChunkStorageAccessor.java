package com.moepus.byepregen.mixin.accessor.chunksave;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@MixinGate(feature = MixinFeature.GC_FREE_RAW_CHUNK_IO)
@Mixin(value = SimpleRegionStorage.class, remap = false)
public interface ChunkStorageAccessor {
    @Invoker("storageInfo")
    RegionStorageInfo byepregen$storageInfo();
}
