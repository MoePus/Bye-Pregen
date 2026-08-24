package com.moepus.byepregen.mixin.accessor.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(value = ChunkMap.class, remap = false)
public interface ChunkMapLightAccessor {
    @Accessor("level")
    ServerLevel byepregen$getLevel();

    @Accessor("mainThreadExecutor")
    BlockableEventLoop<Runnable> byepregen$getMainThreadExecutor();
}
