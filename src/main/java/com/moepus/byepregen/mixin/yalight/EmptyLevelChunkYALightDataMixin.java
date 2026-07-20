package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.yalight.YAChunkLightAccess;
import com.moepus.byepregen.yalight.YAChunkLightData;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EmptyLevelChunk.class)
public abstract class EmptyLevelChunkYALightDataMixin implements YAChunkLightAccess {
    @Override
    public YAChunkLightData byepregen$yaLightData(LightLayer layer, boolean create) {
        return null;
    }

    @Override
    public void byepregen$setYALightData(LightLayer layer, YAChunkLightData data) {
    }
}
