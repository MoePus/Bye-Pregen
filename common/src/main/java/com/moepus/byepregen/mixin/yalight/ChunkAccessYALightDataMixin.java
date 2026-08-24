package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.yalight.access.YAChunkLightAccess;
import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(value = ChunkAccess.class, remap = false)
public abstract class ChunkAccessYALightDataMixin implements YAChunkLightAccess {
    @Shadow
    @Final
    protected ChunkPos chunkPos;

    @Shadow
    @Final
    protected LevelHeightAccessor levelHeightAccessor;

    @Unique
    private volatile YAChunkLightData byepregen$blockLightData;

    @Unique
    private volatile YAChunkLightData byepregen$skyLightData;

    @Override
    public YAChunkLightData byepregen$yaLightData(LightLayer layer, boolean create) {
        if (layer == LightLayer.BLOCK) {
            YAChunkLightData data = this.byepregen$blockLightData;
            if (data == null && create) {
                synchronized (this) {
                    data = this.byepregen$blockLightData;
                    if (data == null) {
                        data = new YAChunkLightData(this.chunkPos, this.levelHeightAccessor);
                        this.byepregen$blockLightData = data;
                    }
                }
            }
            return data;
        }
        YAChunkLightData data = this.byepregen$skyLightData;
        if (data == null && create) {
            synchronized (this) {
                data = this.byepregen$skyLightData;
                if (data == null) {
                    data = new YAChunkLightData(this.chunkPos, this.levelHeightAccessor);
                    this.byepregen$skyLightData = data;
                }
            }
        }
        return data;
    }

    @Override
    public YAChunkLightData byepregen$skyLightData() {
        return this.byepregen$skyLightData;
    }

    @Override
    public YAChunkLightData byepregen$blockLightData() {
        return this.byepregen$blockLightData;
    }

    @Override
    public void byepregen$setYALightData(LightLayer layer, YAChunkLightData data) {
        if (layer == LightLayer.BLOCK) {
            YAChunkLightData old = this.byepregen$blockLightData;
            if (old != null && old != data) {
                old.clear();
            }
            this.byepregen$blockLightData = data;
            return;
        }
        YAChunkLightData old = this.byepregen$skyLightData;
        if (old != null && old != data) {
            old.clear();
        }
        this.byepregen$skyLightData = data;
    }
}
