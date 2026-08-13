package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.yalight.YAChunkLightAccess;
import com.moepus.byepregen.yalight.YAChunkLightData;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(ImposterProtoChunk.class)
public abstract class ImposterProtoChunkYALightDataMixin implements YAChunkLightAccess {
    @Shadow
    @Final
    private LevelChunk wrapped;

    @Override
    public YAChunkLightData byepregen$yaLightData(LightLayer layer, boolean create) {
        return ((YAChunkLightAccess)this.wrapped).byepregen$yaLightData(layer, create);
    }

    // getChunkForLighting returns an imposter for every full chunk (replaceProtoChunk swaps the
    // pre-full futures), so the fast getters must follow the wrapped chunk like the layer
    // accessor does or server-side getRawBrightness/getLightColor read no data at all.
    @Override
    public YAChunkLightData byepregen$skyLightData() {
        return ((YAChunkLightAccess)this.wrapped).byepregen$skyLightData();
    }

    @Override
    public YAChunkLightData byepregen$blockLightData() {
        return ((YAChunkLightAccess)this.wrapped).byepregen$blockLightData();
    }

    @Override
    public void byepregen$setYALightData(LightLayer layer, YAChunkLightData data) {
        ((YAChunkLightAccess)this.wrapped).byepregen$setYALightData(layer, data);
    }
}
