package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.yalight.YAChunkLightAccess;
import com.moepus.byepregen.yalight.YAChunkLightData;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ImposterProtoChunk.class)
public abstract class ImposterProtoChunkYALightDataMixin implements YAChunkLightAccess {
    @Shadow
    @Final
    private LevelChunk wrapped;

    @Override
    public YAChunkLightData byepregen$yaLightData(LightLayer layer, boolean create) {
        return ((YAChunkLightAccess)this.wrapped).byepregen$yaLightData(layer, create);
    }

    @Override
    public void byepregen$setYALightData(LightLayer layer, YAChunkLightData data) {
        ((YAChunkLightAccess)this.wrapped).byepregen$setYALightData(layer, data);
    }
}
