package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.yalight.YAChunkSkyLightSources;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ChunkAccess.class, remap = false)
public abstract class ChunkAccessYASkyLightSourcesMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/world/level/LevelHeightAccessor;)Lnet/minecraft/world/level/lighting/ChunkSkyLightSources;"
            )
    )
    private ChunkSkyLightSources byepregen$createYASkyLightSources(LevelHeightAccessor level) {
        return new YAChunkSkyLightSources(level);
    }
}
