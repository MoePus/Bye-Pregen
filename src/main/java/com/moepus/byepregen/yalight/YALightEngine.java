package com.moepus.byepregen.yalight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;

public final class YALightEngine {
    private final LightChunkGetter chunkGetter;
    private final YASkyLightEngine skyEngine;
    private final YABlockLightEngine blockEngine;
    private final boolean hasBlockLight;
    private final boolean hasSkyLight;
    private final int minLightSection;
    private final int maxLightSection;

    public YALightEngine(LightChunkGetter chunkGetter, boolean hasBlockLight, boolean hasSkyLight) {
        this.chunkGetter = chunkGetter;
        this.hasBlockLight = hasBlockLight;
        this.hasSkyLight = hasSkyLight;
        LevelHeightAccessor level = chunkGetter.getLevel();
        this.minLightSection = YALightStorage.minLightSection(level);
        this.maxLightSection = this.minLightSection + YALightStorage.lightSectionCount(level) - 1;
        this.skyEngine = hasSkyLight ? new YASkyLightEngine(chunkGetter, level) : null;
        this.blockEngine = hasBlockLight ? new YABlockLightEngine(chunkGetter, level) : null;
    }

    public boolean hasBlockLight() {
        return this.hasBlockLight;
    }

    public boolean hasSkyLight() {
        return this.hasSkyLight;
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
        int ret = 0;
        if (this.blockEngine != null) {
            ret += this.blockEngine.runLightUpdates();
        }
        if (this.skyEngine != null) {
            ret += this.skyEngine.runLightUpdates();
        }
        return ret;
    }

    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        if (this.blockEngine != null) {
            this.blockEngine.updateSectionStatus(pos, isEmpty);
        }
        if (this.skyEngine != null) {
            this.skyEngine.updateSectionStatus(pos, isEmpty);
        }
    }

    public void setLightEnabled(ChunkPos pos, boolean lightEnabled) {
        if (this.blockEngine != null) {
            this.blockEngine.setLightEnabled(pos, lightEnabled);
        }
        if (this.skyEngine != null) {
            this.skyEngine.setLightEnabled(pos, lightEnabled);
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

    public LayerLightEventListener getLayerListener(LightLayer layer) {
        if (layer == LightLayer.BLOCK) {
            return this.blockEngine != null ? this.blockEngine : LayerLightEventListener.DummyLightLayerEventListener.INSTANCE;
        }
        return this.skyEngine != null ? this.skyEngine : LayerLightEventListener.DummyLightLayerEventListener.INSTANCE;
    }

    public void queueSectionData(LightLayer layer, SectionPos pos, DataLayer dataLayer) {
        YALightLayerEngine engine = layer == LightLayer.BLOCK ? this.blockEngine : this.skyEngine;
        if (engine != null) {
            engine.queueSectionData(pos, dataLayer);
        }
    }

    public void queueOwnedSectionBytes(LightLayer layer, SectionPos pos, byte[] data) {
        YALightLayerEngine engine = layer == LightLayer.BLOCK ? this.blockEngine : this.skyEngine;
        if (engine != null) {
            engine.queueOwnedSectionBytes(pos, data);
        }
    }

    public void queueZeroSectionData(LightLayer layer, SectionPos pos) {
        YALightLayerEngine engine = layer == LightLayer.BLOCK ? this.blockEngine : this.skyEngine;
        if (engine != null) {
            engine.queueZeroSectionData(pos);
        }
    }

    public YANibbleArray getVisibleSection(LightLayer layer, SectionPos pos) {
        YALightLayerEngine engine = layer == LightLayer.BLOCK ? this.blockEngine : this.skyEngine;
        return engine == null ? null : engine.getNibble(pos.x(), pos.y(), pos.z());
    }

    public String getDebugData(LightLayer layer, SectionPos pos) {
        YALightLayerEngine engine = layer == LightLayer.BLOCK ? this.blockEngine : this.skyEngine;
        return engine == null ? "n/a" : engine.getDebugData(pos);
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(LightLayer layer, SectionPos pos) {
        YALightLayerEngine engine = layer == LightLayer.BLOCK ? this.blockEngine : this.skyEngine;
        return engine != null && engine.lightOnInSection(pos)
                ? LayerLightSectionStorage.SectionType.LIGHT_AND_DATA
                : LayerLightSectionStorage.SectionType.EMPTY;
    }

    public void retainData(ChunkPos pos, boolean retainData) {
        // YA light data is chunk-owned; retainData is a vanilla storage side channel.
    }

    public int getRawBrightness(BlockPos pos, int ambientDarkness) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int sectionY = y >> 4;
        int sectionIndex = sectionY - this.minLightSection;
        boolean sectionInRange = sectionIndex >= 0 && sectionY <= this.maxLightSection;
        YAChunkLightAccess access = this.brightnessData(x >> 4, z >> 4);
        int sky = 0;
        if (this.skyEngine != null) {
            if (sectionInRange && access != null) {
                YAChunkLightData skyData = access.byepregen$skyLightData();
                if (skyData == null) {
                    sky = 15;
                } else {
                    YANibbleArray nibble = skyData.getVisibleSectionByIndex(sectionIndex);
                    sky = nibble == null ? (skyData.lightEnabled() ? 0 : 15) : nibble.getVisible(x, y, z);
                }
            } else if (sectionY > this.maxLightSection || sectionInRange) {
                sky = 15;
            }
            sky -= ambientDarkness;
        }
        if (sky == 15) {
            return 15;
        }
        int block = 0;
        if (this.blockEngine != null && sectionInRange && access != null) {
            YAChunkLightData blockData = access.byepregen$blockLightData();
            if (blockData != null) {
                YANibbleArray nibble = blockData.getVisibleSectionByIndex(sectionIndex);
                if (nibble != null) {
                    block = nibble.getVisible(x, y, z);
                }
            }
        }
        return Math.max(sky, block);
    }

    public int getLightColor(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int sectionY = y >> 4;
        int sectionIndex = sectionY - this.minLightSection;
        boolean sectionInRange = sectionIndex >= 0 && sectionY <= this.maxLightSection;
        YAChunkLightAccess access = this.brightnessData(x >> 4, z >> 4);
        int sky = 0;
        if (this.skyEngine != null) {
            if (sectionInRange && access != null) {
                YAChunkLightData skyData = access.byepregen$skyLightData();
                if (skyData == null) {
                    sky = 15;
                } else {
                    YANibbleArray nibble = skyData.getVisibleSectionByIndex(sectionIndex);
                    sky = nibble == null ? (skyData.lightEnabled() ? 0 : 15) : nibble.getVisible(x, y, z);
                }
            } else if (sectionY > this.maxLightSection || sectionInRange) {
                sky = 15;
            }
        }
        int block = 0;
        if (this.blockEngine != null && sectionInRange && access != null) {
            YAChunkLightData blockData = access.byepregen$blockLightData();
            if (blockData != null) {
                YANibbleArray nibble = blockData.getVisibleSectionByIndex(sectionIndex);
                if (nibble != null) {
                    block = nibble.getVisible(x, y, z);
                }
            }
        }
        return sky << 20 | block << 4;
    }

    private YAChunkLightAccess brightnessData(int chunkX, int chunkZ) {
        return (YAChunkLightAccess) this.chunkGetter.getChunkForLighting(chunkX, chunkZ);
    }

    public boolean lightOnInSection(SectionPos pos) {
        boolean block = this.blockEngine == null || this.blockEngine.lightOnInSection(pos);
        return block && (this.skyEngine == null || this.skyEngine.lightOnInSection(pos));
    }
}
