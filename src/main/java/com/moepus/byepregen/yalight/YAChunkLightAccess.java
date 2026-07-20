package com.moepus.byepregen.yalight;

import net.minecraft.world.level.LightLayer;

public interface YAChunkLightAccess {
    default YAChunkLightData byepregen$yaLightData(LightLayer layer) {
        return this.byepregen$yaLightData(layer, true);
    }

    YAChunkLightData byepregen$yaLightData(LightLayer layer, boolean create);

    YAChunkLightData byepregen$skyLightData();

    YAChunkLightData byepregen$blockLightData();

    default YANibbleArray[] byepregen$visibleBlock() {
        YAChunkLightData data = this.byepregen$blockLightData();
        return data == null ? YAVisibleLightReader.EMPTY_SECTIONS : data.visibleSections();
    }

    default YANibbleArray[] byepregen$visibleSky() {
        YAChunkLightData data = this.byepregen$skyLightData();
        return data == null ? YAVisibleLightReader.EMPTY_SECTIONS : data.visibleSections();
    }

    void byepregen$setYALightData(LightLayer layer, YAChunkLightData data);
}
