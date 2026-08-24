package com.moepus.byepregen.yalight.engine;

import com.moepus.byepregen.yalight.scheduler.YAFreshLightRequest;
import com.moepus.byepregen.yalight.scheduler.YASourceHalo;
import com.moepus.byepregen.yalight.sky.YASkyLightEngine;
import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import com.moepus.byepregen.yalight.storage.YANibbleArray;

import com.moepus.byepregen.yalight.access.YAChunkLightAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;

public final class YALightEngine {
    private final LightChunkGetter chunkGetter;
    private final YASkyLightEngine skyEngine;
    private final YABlockLightEngine blockEngine;
    private final YASourceHalo sourceHalo = new YASourceHalo();

    public YALightEngine(
            LightChunkGetter chunkGetter,
            YABlockLightEngine blockEngine,
            YASkyLightEngine skyEngine
    ) {
        this.chunkGetter = chunkGetter;
        this.blockEngine = blockEngine;
        this.skyEngine = skyEngine;
    }

    public void checkBlock(BlockPos pos) {
        if (this.blockEngine != null) {
            this.blockEngine.checkBlock(pos);
        }
        if (this.skyEngine != null) {
            this.skyEngine.checkBlock(pos);
        }
    }

    public boolean hasLightWork() {
        return (this.skyEngine != null && this.skyEngine.hasLightWork())
                || (this.blockEngine != null && this.blockEngine.hasLightWork());
    }

    public int runLightUpdates() {
        this.prepareSourceChunks();
        int ret = 0;
        if (this.blockEngine != null) {
            ret += this.blockEngine.runLightUpdates();
        }
        if (this.skyEngine != null) {
            ret += this.skyEngine.runLightUpdates();
        }
        return ret;
    }

    private void prepareSourceChunks() {
        try {
            if (this.blockEngine != null) {
                this.blockEngine.collectSourceHalo(this.sourceHalo, YASourceHalo.BLOCK_MASK);
            }
            if (this.skyEngine != null) {
                this.skyEngine.collectSourceHalo(this.sourceHalo, YASourceHalo.SKY_MASK);
            }
            for (int i = 0; i < this.sourceHalo.size(); ++i) {
                long chunkKey = this.sourceHalo.keyAt(i);
                ChunkAccess chunk = (ChunkAccess)this.chunkGetter.getChunkForLighting(
                        ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
                byte layerMask = this.sourceHalo.layerMaskAt(i);
                if (this.blockEngine != null && (layerMask & YASourceHalo.BLOCK_MASK) != 0) {
                    this.blockEngine.enableSourceChunk(chunk);
                }
                if (this.skyEngine != null && (layerMask & YASourceHalo.SKY_MASK) != 0) {
                    this.skyEngine.enableSourceChunk(chunk);
                }
            }
        } finally {
            this.sourceHalo.clear();
        }
    }

    public void setDeclaredFreshOwnerDomain(Iterable<ChunkPos> owners) {
        if (this.skyEngine != null) {
            this.skyEngine.setDeclaredFreshOwnerDomain(owners);
        }
    }

    public void clearDeclaredFreshOwnerDomain() {
        if (this.skyEngine != null) {
            this.skyEngine.clearDeclaredFreshOwnerDomain();
        }
    }

    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        ChunkAccess chunk = (ChunkAccess)this.chunkGetter.getChunkForLighting(pos.x(), pos.z());
        this.updateSectionStatus(chunk, pos.y(), isEmpty);
    }

    public void updateSectionStatus(ChunkAccess chunk, int sectionY, boolean isEmpty) {
        if (this.blockEngine != null) {
            this.blockEngine.updateSectionStatus(chunk, sectionY, isEmpty);
        }
        if (this.skyEngine != null) {
            this.skyEngine.updateSectionStatus(chunk, sectionY, isEmpty);
        }
    }

    public void setLightEnabled(ChunkPos pos, boolean lightEnabled) {
        ChunkAccess chunk = (ChunkAccess)this.chunkGetter.getChunkForLighting(pos.x(), pos.z());
        this.setLightEnabled(chunk, lightEnabled);
    }

    public void setLightEnabled(ChunkAccess chunk, boolean lightEnabled) {
        if (this.blockEngine != null) {
            this.blockEngine.setLightEnabled(chunk, lightEnabled);
        }
        if (this.skyEngine != null) {
            this.skyEngine.setLightEnabled(chunk, lightEnabled);
        }
    }

    public void clearChunk(ChunkPos pos) {
        if (this.blockEngine != null) {
            this.blockEngine.clearChunk(pos);
        }
        if (this.skyEngine != null) {
            this.skyEngine.clearChunk(pos);
        }
    }

    public void propagateLightSources(ChunkPos pos) {
        if (this.blockEngine != null) {
            this.blockEngine.propagateLightSources(pos);
        }
        if (this.skyEngine != null) {
            this.skyEngine.propagateLightSources(pos);
        }
    }

    public YAFreshLightRequest createFreshLightRequest(ChunkAccess chunk) {
        return YAFreshLightRequest.create(chunk, this.blockEngine != null, this.skyEngine != null);
    }

    public void propagateFreshLightSources(YAFreshLightRequest request) {
        if (this.blockEngine != null) {
            this.blockEngine.propagateFreshLightSources(request);
        }
        if (this.skyEngine != null) {
            this.skyEngine.propagateFreshLightSources(request);
        }
    }

    public boolean isCurrentOwner(YAFreshLightRequest request) {
        ChunkPos pos = request.owner().getPos();
        LightChunk current = this.chunkGetter.getChunkForLighting(pos.x(), pos.z());
        return current != null && request.matchesOwner((ChunkAccess)current);
    }

    public void restoreSavedSkyLight(ChunkAccess chunk) {
        if (this.skyEngine != null) {
            this.skyEngine.restoreSavedSkyLight(chunk);
        }
    }

    public void checkChunkEdges(ChunkAccess chunk) {
        if (this.blockEngine != null) {
            this.blockEngine.checkChunkEdges(chunk);
        }
        if (this.skyEngine != null) {
            this.skyEngine.checkChunkEdges(chunk);
        }
    }

    public void setEdgeCheckReady(ChunkAccess chunk, boolean ready) {
        if (this.blockEngine != null) {
            this.blockEngine.storage().data(chunk).setEdgeCheckReady(ready);
        }
        if (this.skyEngine != null) {
            this.skyEngine.storage().data(chunk).setEdgeCheckReady(ready);
        }
    }

    public LayerLightEventListener getLayerListener(LightLayer layer) {
        if (layer == LightLayer.BLOCK) {
            return this.blockEngine != null ? this.blockEngine : LayerLightEventListener.DummyLightLayerEventListener.INSTANCE;
        }
        return this.skyEngine != null ? this.skyEngine : LayerLightEventListener.DummyLightLayerEventListener.INSTANCE;
    }

    public void queueSectionData(LightLayer layer, SectionPos pos, DataLayer dataLayer) {
        YALightLayerEngine engine = this.layerEngine(layer);
        if (engine != null) {
            engine.queueSectionData(pos, dataLayer);
        }
    }

    public void queueOwnedSectionBytes(LightLayer layer, SectionPos pos, byte[] data) {
        YALightLayerEngine engine = this.layerEngine(layer);
        if (engine != null) {
            engine.queueOwnedSectionBytes(pos, data);
        }
    }

    public void queueZeroSectionData(LightLayer layer, SectionPos pos) {
        YALightLayerEngine engine = this.layerEngine(layer);
        if (engine != null) {
            engine.queueZeroSectionData(pos);
        }
    }

    public YANibbleArray getVisibleSection(LightLayer layer, SectionPos pos) {
        YALightLayerEngine engine = this.layerEngine(layer);
        return engine == null ? null : engine.getNibble(pos.x(), pos.y(), pos.z());
    }

    public String getDebugData(LightLayer layer, SectionPos pos) {
        YALightLayerEngine engine = this.layerEngine(layer);
        return engine == null ? "n/a" : engine.getDebugData(pos);
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(LightLayer layer, SectionPos pos) {
        YALightLayerEngine engine = this.layerEngine(layer);
        return engine != null && engine.lightOnInSection(pos)
                ? LayerLightSectionStorage.SectionType.LIGHT_AND_DATA
                : LayerLightSectionStorage.SectionType.EMPTY;
    }

    public void retainData(ChunkPos pos, boolean retainData) {
        // YA light data is chunk-owned; retainData is a vanilla storage side channel.
    }

    public int getRawBrightness(BlockPos pos, int ambientDarkness) {
        return this.getRawBrightness(pos, ambientDarkness, this.brightnessData(pos));
    }

    public int getRawBrightness(BlockPos pos, int ambientDarkness, YAChunkLightAccess access) {
        int sky = this.getVisibleSkyLight(pos, access);
        if (this.skyEngine != null) {
            sky -= ambientDarkness;
        }
        if (sky == 15) {
            return 15;
        }
        return Math.max(sky, this.getVisibleBlockLight(pos, access));
    }

    public int getBrightness(LightLayer layer, BlockPos pos, YAChunkLightAccess access) {
        return layer == LightLayer.BLOCK
                ? this.getVisibleBlockLight(pos, access)
                : this.getVisibleSkyLight(pos, access);
    }

    public int getLightColor(BlockPos pos) {
        return this.getLightColor(pos, this.brightnessData(pos));
    }

    public int getLightColor(BlockPos pos, YAChunkLightAccess access) {
        int sky = this.getVisibleSkyLight(pos, access);
        int block = this.getVisibleBlockLight(pos, access);
        return sky << 20 | block << 4;
    }

    private int getVisibleSkyLight(BlockPos pos, YAChunkLightAccess access) {
        if (this.skyEngine == null) {
            return 0;
        }
        YAChunkLightData skyData = access == null ? null : access.byepregen$skyLightData();
        return this.skyEngine.getVisibleLightValue(pos, skyData);
    }

    private int getVisibleBlockLight(BlockPos pos, YAChunkLightAccess access) {
        if (this.blockEngine == null) {
            return 0;
        }
        YAChunkLightData blockData = access == null ? null : access.byepregen$blockLightData();
        return this.blockEngine.getVisibleLightValue(pos, blockData);
    }

    private YAChunkLightAccess brightnessData(BlockPos pos) {
        return (YAChunkLightAccess)this.chunkGetter.getChunkForLighting(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public boolean lightOnInSection(SectionPos pos) {
        boolean block = this.blockEngine == null || this.blockEngine.lightOnInSection(pos);
        return block && (this.skyEngine == null || this.skyEngine.lightOnInSection(pos));
    }

    public boolean lightOnInColumn(long columnPos) {
        int chunkX = SectionPos.x(columnPos);
        int chunkZ = SectionPos.z(columnPos);
        boolean block = this.blockEngine == null || this.blockEngine.storage().lightEnabled(chunkX, chunkZ);
        return block && (this.skyEngine == null || this.skyEngine.storage().lightEnabled(chunkX, chunkZ));
    }

    private YALightLayerEngine layerEngine(LightLayer layer) {
        if (layer == LightLayer.BLOCK) {
            return this.blockEngine;
        }
        return this.skyEngine;
    }
}
