package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.yalight.YASkyLightEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(value = SkyLightEngine.class, remap = false)
public abstract class SkyLightEngineYASuperMixin {
    @Unique
    private static final BlockPos.MutableBlockPos byepregen$dummyMutablePos = new BlockPos.MutableBlockPos();

    @Unique
    private static ChunkSkyLightSources byepregen$dummyEmptySources;

    @Redirect(
            method = "<init>(Lnet/minecraft/world/level/chunk/LightChunkGetter;Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;)V",
            at = @At(value = "NEW", target = "()Lnet/minecraft/core/BlockPos$MutableBlockPos;")
    )
    private BlockPos.MutableBlockPos byepregen$skipMutablePos() {
        return (Object)this instanceof YASkyLightEngine ? byepregen$dummyMutablePos : new BlockPos.MutableBlockPos();
    }

    @Redirect(
            method = "<init>(Lnet/minecraft/world/level/chunk/LightChunkGetter;Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;)V",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/world/level/LevelHeightAccessor;)Lnet/minecraft/world/level/lighting/ChunkSkyLightSources;"
            )
    )
    private ChunkSkyLightSources byepregen$skipEmptySources(LevelHeightAccessor level) {
        return (Object)this instanceof YASkyLightEngine
                ? byepregen$dummyEmptySources(level)
                : new ChunkSkyLightSources(level);
    }

    @Unique
    private static synchronized ChunkSkyLightSources byepregen$dummyEmptySources(LevelHeightAccessor level) {
        if (byepregen$dummyEmptySources == null) {
            byepregen$dummyEmptySources = new ChunkSkyLightSources(level);
        }
        return byepregen$dummyEmptySources;
    }
}
