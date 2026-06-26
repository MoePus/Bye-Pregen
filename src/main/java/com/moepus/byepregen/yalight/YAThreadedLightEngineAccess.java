package com.moepus.byepregen.yalight;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;

public interface YAThreadedLightEngineAccess {
    void byepregen$queueOwnedSectionBytes(LightLayer layer, SectionPos pos, byte[] data);
}
