package com.moepus.byepregen.yalight;

import com.moepus.byepregen.PaletteContainer.BlockStateRawIdAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

import java.util.Arrays;

public class YAChunkRunCache {
    private static final int UNSET = Integer.MIN_VALUE;
    private static final int INITIAL_PINNED_OWNER_CAPACITY = 8;

    private final ChunkAccess[] chunks = new ChunkAccess[9];
    private final YAChunkLightData[] lightData = new YAChunkLightData[9];
    // Source scans enqueue increases that run later, so their center owner stays fixed for the full layer run.
    private final Long2ObjectOpenHashMap<ChunkAccess> pinnedOwners =
            new Long2ObjectOpenHashMap<>(INITIAL_PINNED_OWNER_CAPACITY);

    private int chunkCenterX = UNSET;
    private int chunkCenterZ = UNSET;
    private int chunkLoadedMask;
    private int lightDataLoadedMask;

    private YAChunkLightData residentLightData;
    private BlockStateRawIdAccess residentBlockAccess;
    private int residentChunkIndex;
    private int residentStorageIndex = -1;
    private boolean residentBlockResolved;

    void clear() {
        this.pinnedOwners.clear();
        Arrays.fill(this.chunks, null);
        Arrays.fill(this.lightData, null);
        this.chunkCenterX = UNSET;
        this.chunkCenterZ = UNSET;
        this.chunkLoadedMask = 0;
        this.lightDataLoadedMask = 0;
        this.clearResidentSection();
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

    ChunkAccess enableChunk(YALightStorage storage, int chunkX, int chunkZ) {
        int index = this.chunkIndex(storage.chunkGetter(), chunkX, chunkZ);
        YAChunkLightData data = this.writableLightData(storage, index);
        if (data == null) {
            return null;
        }
        data.setLightEnabled(true);
        return this.chunks[index];
    }

    boolean enableOwnedChunk(YALightStorage storage, ChunkAccess owner) {
        if (!this.pinOwner(owner)) {
            return false;
        }
        int index = this.chunkIndex(storage.chunkGetter(), owner.getPos().x, owner.getPos().z);
        YAChunkLightData data = this.writableLightData(storage, index);
        if (data == null) {
            return false;
        }
        data.setLightEnabled(true);
        return true;
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

    int getEnabledUpdatingLight(YALightStorage storage, int x, int y, int z) {
        int storageIndex = storage.sectionIndex(y >> 4);
        if (storageIndex < 0 || storageIndex >= storage.lightSectionCount()) {
            return -1;
        }
        int index = this.chunkIndex(storage.chunkGetter(), x >> 4, z >> 4);
        YAChunkLightData data = this.existingLightData(storage, index);
        if (data == null || !data.lightEnabled()) {
            return -1;
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
            int nibbleIndex = YANibbleArray.index(x, y, z);
            if (nibble.setUpdatingAndGetDirtyTransition(nibbleIndex, value)) {
                storage.markDirty(data, storageIndex);
            }
        }
    }

    LightChunk enabledChunk(YALightStorage storage, int chunkX, int chunkZ) {
        int index = this.chunkIndex(storage.chunkGetter(), chunkX, chunkZ);
        YAChunkLightData data = this.existingLightData(storage, index);
        return data == null || !data.lightEnabled() ? null : this.chunks[index];
    }

    boolean lightEnabled(YALightStorage storage, int chunkX, int chunkZ) {
        return this.enabledChunk(storage, chunkX, chunkZ) != null;
    }

    int getRawId(LightChunkGetter chunkGetter, int x, int y, int z) {
        int index = this.chunkIndex(chunkGetter, x >> 4, z >> 4);
        return rawIdAt(this.chunks[index], x, y, z);
    }

    void prepareResidentSection(YALightStorage storage, int x, int y, int z) {
        int index = this.chunkIndex(storage.chunkGetter(), x >> 4, z >> 4);
        this.residentLightData = this.existingLightData(storage, index);
        this.residentBlockAccess = null;
        this.residentChunkIndex = index;
        int storageIndex = storage.sectionIndex(y >> 4);
        this.residentStorageIndex = storageIndex >= 0 && storageIndex < storage.lightSectionCount()
                ? storageIndex
                : -1;
        this.residentBlockResolved = false;
    }

    int getResidentUpdatingLight(int x, int y, int z) {
        YAChunkLightData data = this.residentLightData;
        int index = this.residentStorageIndex;
        if (data == null || index < 0) {
            return 0;
        }
        YANibbleArray nibble = data.getUpdatingSectionByIndex(index);
        return nibble == null ? 0 : nibble.getUpdating(x, y, z);
    }

    int getEnabledResidentUpdatingLight(int x, int y, int z) {
        YAChunkLightData data = this.residentLightData;
        if (data == null || !data.lightEnabled()) {
            return -1;
        }
        int index = this.residentStorageIndex;
        if (index < 0) {
            return -1;
        }
        YANibbleArray nibble = data.getUpdatingSectionByIndex(index);
        return nibble == null ? 0 : nibble.getUpdating(x, y, z);
    }

    void setResidentUpdatingLight(YALightStorage storage, int x, int y, int z, int value) {
        YAChunkLightData data = this.residentLightData;
        if (data == null) {
            data = this.writableLightData(storage, this.residentChunkIndex);
            this.residentLightData = data;
        }
        int index = this.residentStorageIndex;
        if (data != null && index >= 0) {
            YANibbleArray nibble = data.getOrCreateUpdatingSectionByIndex(index);
            if (nibble.setUpdatingAndGetDirtyTransition(YANibbleArray.index(x, y, z), value)) {
                storage.markDirty(data, index);
            }
        }
    }

    int getResidentRawId(int x, int y, int z) {
        if (!this.residentBlockResolved) {
            this.residentBlockAccess = blockAccessAt(this.chunks[this.residentChunkIndex], y >> 4);
            this.residentBlockResolved = true;
        }
        BlockStateRawIdAccess access = this.residentBlockAccess;
        return access == null ? -1 : access.getRawId(x & 15, y & 15, z & 15);
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
        this.resetChunkWindow(centerX, centerZ);
    }

    private boolean pinOwner(ChunkAccess owner) {
        long chunkKey = owner.getPos().toLong();
        ChunkAccess existing = this.pinnedOwners.get(chunkKey);
        if (existing != null) {
            return YAFreshLightRequest.sameOwner(existing, owner);
        }
        this.pinnedOwners.put(chunkKey, owner);
        this.resetChunkWindow(owner.getPos().x, owner.getPos().z);
        return true;
    }

    private void resetChunkWindow(int centerX, int centerZ) {
        Arrays.fill(this.chunks, null);
        Arrays.fill(this.lightData, null);
        this.chunkCenterX = centerX;
        this.chunkCenterZ = centerZ;
        this.chunkLoadedMask = 0;
        this.lightDataLoadedMask = 0;
        this.clearResidentSection();
    }

    private void loadChunkSlot(LightChunkGetter chunkGetter, int index, int dx, int dz) {
        int bit = 1 << index;
        if ((this.chunkLoadedMask & bit) != 0) {
            return;
        }
        int chunkX = this.chunkCenterX + dx;
        int chunkZ = this.chunkCenterZ + dz;
        ChunkAccess pinned = this.pinnedOwners.get(ChunkPos.asLong(chunkX, chunkZ));
        LightChunk chunk = pinned == null ? chunkGetter.getChunkForLighting(chunkX, chunkZ) : pinned;
        this.chunks[index] = chunk == null ? null : (ChunkAccess)chunk;
        this.chunkLoadedMask |= bit;
    }

    private YAChunkLightData existingLightData(YALightStorage storage, int index) {
        int bit = 1 << index;
        if ((this.lightDataLoadedMask & bit) == 0) {
            YAChunkLightData data = storage.existingData(this.chunks[index]);
            this.lightData[index] = data;
            this.lightDataLoadedMask |= bit;
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
        }
        return data;
    }

    private static BlockStateRawIdAccess blockAccessAt(ChunkAccess chunk, int sectionY) {
        if (chunk == null) {
            return null;
        }
        int sectionIndex = chunk.getSectionIndexFromSectionY(sectionY);
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= sections.length || sections[sectionIndex] == null) {
            return null;
        }
        return sections[sectionIndex].getStates() instanceof BlockStateRawIdAccess access ? access : null;
    }

    private void clearResidentSection() {
        this.residentLightData = null;
        this.residentBlockAccess = null;
        this.residentStorageIndex = -1;
        this.residentBlockResolved = false;
    }

    public static int rawIdAt(ChunkAccess chunk, int x, int y, int z) {
        BlockStateRawIdAccess access = blockAccessAt(chunk, y >> 4);
        if (access == null) {
            return -1;
        }
        return access.getRawId(x & 15, y & 15, z & 15);
    }

    private static int chunkWindowIndex(int dx, int dz) {
        return (dz + 1) * 3 + dx + 1;
    }

}
