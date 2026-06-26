package com.moepus.byepregen.yalight;

import net.minecraft.world.level.LightLayer;

public interface YAChunkLightAccess {
    default YAChunkLightData byepregen$yaLightData(LightLayer layer) {
        return this.byepregen$yaLightData(layer, true);
    }

    YAChunkLightData byepregen$yaLightData(LightLayer layer, boolean create);

    YAChunkLightData byepregen$skyLightData();

    YAChunkLightData byepregen$blockLightData();

    void byepregen$setYALightData(LightLayer layer, YAChunkLightData data);
}
