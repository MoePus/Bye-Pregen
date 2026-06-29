package com.moepus.byepregen.yalight;

import com.moepus.byepregen.PaletteContainer.BlockStateRawIdAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

import java.util.Arrays;

public class YAChunkRunCache {
    private static final int UNSET = Integer.MIN_VALUE;

    private final ChunkAccess[] chunks = new ChunkAccess[9];
    private final YAChunkLightData[] lightData = new YAChunkLightData[9];

    private int chunkCenterX = UNSET;
    private int chunkCenterZ = UNSET;
    private int chunkLoadedMask;
    private int lightDataLoadedMask;
    private int chunkEnabledMask;

    void clear() {
        Arrays.fill(this.chunks, null);
        Arrays.fill(this.lightData, null);
        this.chunkCenterX = UNSET;
        this.chunkCenterZ = UNSET;
        this.chunkLoadedMask = 0;
        this.lightDataLoadedMask = 0;
        this.chunkEnabledMask = 0;
    }

    LightChunk chunk(LightChunkGetter chunkGetter, int chunkX, int chunkZ) {
        return this.chunkAccess(chunkGetter, chunkX, chunkZ);
    }

    ChunkAccess chunkAccess(LightChunkGetter chunkGetter, int chunkX, int chunkZ) {
        int index = this.chunkIndex(chunkGetter, chunkX, chunkZ);
        return this.chunks[index];
    }

    void centerChunks(int chunkX, int chunkZ) {
        this.loadChunkWindow(chunkX, chunkZ);
    }

    int getUpdatingLight(YALightStorage storage, int x, int y, int z) {
        int index = this.chunkIndex(storage.chunkGetter(), x >> 4, z >> 4);
        YAChunkLightData data = this.existingLightData(storage, index);
        if (data == null) {
            return 0;
        }
        int storageIndex = storage.sectionIndex(y >> 4);
        if (storageIndex < 0 || storageIndex >= storage.lightSectionCount()) {
            return 0;
        }
        YANibbleArray nibble = data.getUpdatingSectionByIndex(storageIndex);
        return nibble == null ? 0 : nibble.getUpdating(x, y, z);
    }

    void setUpdatingLight(YALightStorage storage, int x, int y, int z, int value) {
        int sectionY = y >> 4;
        int index = this.chunkIndex(storage.chunkGetter(), x >> 4, z >> 4);
        YAChunkLightData data = this.writableLightData(storage, index);
        if (data == null) {
            return;
        }
        int storageIndex = storage.sectionIndex(sectionY);
        if (storageIndex >= 0 && storageIndex < storage.lightSectionCount()) {
            YANibbleArray nibble = data.getOrCreateUpdatingSectionByIndex(storageIndex);
            nibble.setUpdating(x, y, z, value);
            storage.markDirty(data, storageIndex);
        }
    }

    boolean enabled(YALightStorage storage, LightChunkGetter chunkGetter, int chunkX, int chunkZ) {
        int index = this.chunkIndex(chunkGetter, chunkX, chunkZ);
        this.existingLightData(storage, index);
        return (this.chunkEnabledMask & (1 << index)) != 0;
    }

    boolean lightEnabled(YALightStorage storage, int chunkX, int chunkZ) {
        return this.enabled(storage, storage.chunkGetter(), chunkX, chunkZ);
    }

    int getRawId(LightChunkGetter chunkGetter, int x, int y, int z) {
        int index = this.chunkIndex(chunkGetter, x >> 4, z >> 4);
        return rawIdAt(this.chunks[index], x, y, z);
    }

    private int chunkIndex(LightChunkGetter chunkGetter, int chunkX, int chunkZ) {
        if (this.chunkCenterX == UNSET || Math.abs(chunkX - this.chunkCenterX) > 1 || Math.abs(chunkZ - this.chunkCenterZ) > 1) {
            this.loadChunkWindow(chunkX, chunkZ);
        }
        int dx = chunkX - this.chunkCenterX;
        int dz = chunkZ - this.chunkCenterZ;
        int index = chunkWindowIndex(dx, dz);
        this.loadChunkSlot(chunkGetter, index, dx, dz);
        return index;
    }

    private void loadChunkWindow(int centerX, int centerZ) {
        if (this.chunkCenterX == centerX && this.chunkCenterZ == centerZ) {
            return;
        }
        Arrays.fill(this.chunks, null);
        Arrays.fill(this.lightData, null);
        this.chunkCenterX = centerX;
        this.chunkCenterZ = centerZ;
        this.chunkLoadedMask = 0;
        this.lightDataLoadedMask = 0;
        this.chunkEnabledMask = 0;
    }

    private void loadChunkSlot(LightChunkGetter chunkGetter, int index, int dx, int dz) {
        int bit = 1 << index;
        if ((this.chunkLoadedMask & bit) != 0) {
            return;
        }
        LightChunk chunk = chunkGetter.getChunkForLighting(this.chunkCenterX + dx, this.chunkCenterZ + dz);
        this.chunks[index] = chunk == null ? null : (ChunkAccess) chunk;
        this.chunkLoadedMask |= bit;
    }

    private YAChunkLightData existingLightData(YALightStorage storage, int index) {
        int bit = 1 << index;
        if ((this.lightDataLoadedMask & bit) == 0) {
            YAChunkLightData data = storage.existingData(this.chunks[index]);
            this.lightData[index] = data;
            this.lightDataLoadedMask |= bit;
            this.chunkEnabledMask = setMaskBit(this.chunkEnabledMask, index, data != null && data.lightEnabled());
        }
        return this.lightData[index];
    }

    private YAChunkLightData writableLightData(YALightStorage storage, int index) {
        int bit = 1 << index;
        YAChunkLightData data = (this.lightDataLoadedMask & bit) == 0 ? null : this.lightData[index];
        if (data == null) {
            data = storage.data(this.chunks[index]);
            this.lightData[index] = data;
            this.lightDataLoadedMask |= bit;
            this.chunkEnabledMask = setMaskBit(this.chunkEnabledMask, index, data != null && data.lightEnabled());
        }
        return data;
    }

    public static int rawIdAt(ChunkAccess chunk, int x, int y, int z) {
        if (chunk == null) {
            return -1;
        }
        int sectionIndex = chunk.getSectionIndexFromSectionY(y >> 4);
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return -1;
        }
        LevelChunkSection section = sections[sectionIndex];
        if (section == null) {
            return -1;
        }
        if (!(section.getStates() instanceof BlockStateRawIdAccess access)) {
            return -1;
        }
        return access.getRawId(x & 15, y & 15, z & 15);
    }

    private static int chunkWindowIndex(int dx, int dz) {
        return (dz + 1) * 3 + dx + 1;
    }

    private static int setMaskBit(int mask, int index, boolean value) {
        int bit = 1 << index;
        return value ? mask | bit : mask & ~bit;
    }

}
