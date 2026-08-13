package com.moepus.byepregen.yalight;

import com.moepus.byepregen.yalight.access.YAChunkLightAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

import java.util.ArrayList;

public final class YALightStorage {
    private final LightChunkGetter chunkGetter;
    private final LightLayer layer;
    private final int minLightSection;
    private final int maxLightSection;
    private final int lightSectionCount;
    private final ArrayList<YAChunkLightData> dirtyChunks = new ArrayList<>();

    public YALightStorage(LightChunkGetter chunkGetter, LevelHeightAccessor level, LightLayer layer) {
        this.chunkGetter = chunkGetter;
        this.layer = layer;
        this.minLightSection = minLightSection(level);
        this.lightSectionCount = lightSectionCount(level);
        this.maxLightSection = this.minLightSection + this.lightSectionCount - 1;
    }

    public ChunkAccess chunkAccess(int chunkX, int chunkZ) {
        LightChunk chunk = this.chunkGetter.getChunkForLighting(chunkX, chunkZ);
        return chunk == null ? null : (ChunkAccess)chunk;
    }

    LightChunkGetter chunkGetter() {
        return this.chunkGetter;
    }

    int minLightSection() {
        return this.minLightSection;
    }

    int maxLightSection() {
        return this.maxLightSection;
    }

    int lightSectionCount() {
        return this.lightSectionCount;
    }

    int sectionIndex(int sectionY) {
        return sectionY - this.minLightSection;
    }

    public YAChunkLightData data(ChunkAccess chunk) {
        return chunk == null ? null : ((YAChunkLightAccess)chunk).byepregen$yaLightData(this.layer);
    }

    public YAChunkLightData existingData(ChunkAccess chunk) {
        return chunk == null ? null : ((YAChunkLightAccess)chunk).byepregen$yaLightData(this.layer, false);
    }

    public YANibbleArray getSection(int chunkX, int sectionY, int chunkZ) {
        return this.getVisibleSection(this.chunkAccess(chunkX, chunkZ), sectionY);
    }

    public YANibbleArray getVisibleSection(ChunkAccess chunk, int sectionY) {
        YAChunkLightData data = this.existingData(chunk);
        return data == null ? null : data.getVisibleSection(sectionY);
    }

    public YANibbleArray getOrCreateSection(ChunkAccess chunk, int sectionY) {
        return this.getOrCreateSection(this.data(chunk), sectionY);
    }

    YANibbleArray getOrCreateSection(YAChunkLightData data, int sectionY) {
        return data == null ? null : data.getOrCreateUpdatingSection(sectionY);
    }

    public void setSection(SectionPos pos, YANibbleArray nibble) {
        ChunkAccess chunk = this.chunkAccess(pos.x(), pos.z());
        YAChunkLightData data = this.data(chunk);
        if (data == null) {
            if (nibble != null) {
                nibble.discardUnpublished();
            }
            return;
        }
        data.setSection(pos.y(), nibble, this);
    }

    void setFullSection(YAChunkLightData data, int sectionY) {
        if (data != null) {
            data.setFullSection(sectionY, this);
        }
    }

    void markDirtySection(YAChunkLightData data, int sectionY) {
        int index = this.sectionIndex(sectionY);
        if (data != null && index >= 0 && index < this.lightSectionCount) {
            this.markDirty(data, index);
        }
    }

    public void setLightEnabled(ChunkAccess chunk, boolean lightEnabled) {
        YAChunkLightData data = this.data(chunk);
        if (data != null) {
            data.setLightEnabled(lightEnabled);
        }
    }

    public boolean lightOnInSection(SectionPos pos) {
        return this.lightEnabled(pos.x(), pos.z());
    }

    public boolean lightEnabled(int chunkX, int chunkZ) {
        return this.lightEnabled(this.chunkAccess(chunkX, chunkZ));
    }

    public boolean lightEnabled(ChunkAccess chunk) {
        YAChunkLightData data = this.existingData(chunk);
        return data != null && data.lightEnabled();
    }

    public void removeChunk(ChunkPos pos) {
        YAChunkLightData data = this.existingData(this.chunkAccess(pos.x, pos.z));
        if (data != null) {
            data.clear();
        }
    }

    public void markDirty(YAChunkLightData data, int sectionIndex) {
        if (data.markDirty(sectionIndex)) {
            this.dirtyChunks.add(data);
        }
    }

    public int publishDirty(LightChunkGetter chunkGetter, LightLayer layer) {
        int count = 0;
        for (YAChunkLightData data : this.dirtyChunks) {
            count += data.publishDirty(chunkGetter, layer);
        }
        this.dirtyChunks.clear();
        return count;
    }

    public static YANibbleArray[] create(LevelHeightAccessor level) {
        return new YANibbleArray[lightSectionCount(level)];
    }

    public static int minLightSection(LevelHeightAccessor level) {
        return level.getMinSection() - 1;
    }

    public static int lightSectionCount(LevelHeightAccessor level) {
        // Light storage has one sentinel section below and above the block-section range.
        return level.getSectionsCount() + 2;
    }
}
