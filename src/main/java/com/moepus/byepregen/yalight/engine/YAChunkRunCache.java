package com.moepus.byepregen.yalight.engine;

import com.moepus.byepregen.yalight.scheduler.YAFreshLightRequest;
import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import com.moepus.byepregen.yalight.storage.YALightStorage;
import com.moepus.byepregen.yalight.storage.YANibbleArray;

import com.moepus.byepregen.palette.access.BlockStateRawIdAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

import java.util.Arrays;

public class YAChunkRunCache {
    private static final int UNSET = Integer.MIN_VALUE;
    private static final int INITIAL_PINNED_OWNER_CAPACITY = 8;
    // A section origin always has its low four position bits clear.
    private static final long UNBOUND_SECTION = 1L;

    private final ChunkAccess[] chunks = new ChunkAccess[9];
    private final YAChunkLightData[] lightData = new YAChunkLightData[9];
    // Source scans enqueue increases that run later, so their center owner stays fixed for the full layer run.
    private final Long2ObjectOpenHashMap<ChunkAccess> pinnedOwners =
            new Long2ObjectOpenHashMap<>(INITIAL_PINNED_OWNER_CAPACITY);

    private int chunkCenterX = UNSET;
    private int chunkCenterZ = UNSET;
    private int chunkLoadedMask;
    private int lightDataLoadedMask;

    private final YASectionAccess resident = new YASectionAccess();
    private final YASectionAccess neighbor = new YASectionAccess();
    private long residentSectionKey = UNBOUND_SECTION;

    public void clear() {
        this.pinnedOwners.clear();
        Arrays.fill(this.chunks, null);
        Arrays.fill(this.lightData, null);
        this.chunkCenterX = UNSET;
        this.chunkCenterZ = UNSET;
        this.chunkLoadedMask = 0;
        this.lightDataLoadedMask = 0;
        this.residentSectionKey = UNBOUND_SECTION;
        this.resident.clear();
        this.neighbor.clear();
    }

    protected LightChunk chunk(LightChunkGetter chunkGetter, int chunkX, int chunkZ) {
        return this.chunkAccess(chunkGetter, chunkX, chunkZ);
    }

    ChunkAccess chunkAccess(LightChunkGetter chunkGetter, int chunkX, int chunkZ) {
        int index = this.chunkIndex(chunkGetter, chunkX, chunkZ);
        return this.chunks[index];
    }

    public void centerChunks(int chunkX, int chunkZ) {
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

    protected LightChunk enabledChunk(YALightStorage storage, int chunkX, int chunkZ) {
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

    public YASectionAccess prepareSource(YALightStorage storage, long pos) {
        long key = YALightMath.sectionKey(pos);
        if (key != this.residentSectionKey) {
            int slot = this.chunkIndex(storage.chunkGetter(),
                    BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
            this.resident.bind(storage, this.chunks[slot], this.existingLightData(storage, slot),
                    BlockPos.getY(pos) >> 4);
            this.residentSectionKey = this.resident.canReuseBinding() ? key : UNBOUND_SECTION;
        } else {
            this.resident.refresh();
        }
        return this.resident;
    }

    public YASectionAccess verticalNeighbor(YALightStorage storage, YASectionAccess source, int step) {
        this.neighbor.bind(storage, source.owner(), source.data(), source.sectionY() + step);
        return this.neighbor;
    }

    public YASectionAccess horizontalNeighbor(YALightStorage storage, long pos, int direction, int step) {
        int axis = direction >>> 1;
        int chunkX = (BlockPos.getX(pos) >> 4) + (step & -(axis >>> 1));
        int chunkZ = (BlockPos.getZ(pos) >> 4) + (step & -(axis & 1));
        int slot = this.chunkIndex(storage.chunkGetter(), chunkX, chunkZ);
        this.neighbor.bind(storage, this.chunks[slot], this.existingLightData(storage, slot),
                BlockPos.getY(pos) >> 4);
        return this.neighbor;
    }

    YAChunkLightData createLightData(YALightStorage storage, ChunkAccess owner) {
        YAChunkLightData data = storage.data(owner);
        // A previous read may have cached a missing layer. Update only aliases of this exact owner.
        for (int slot = 0; slot < this.chunks.length; ++slot) {
            if (this.chunks[slot] == owner) {
                this.lightData[slot] = data;
            }
        }
        this.residentSectionKey = UNBOUND_SECTION;
        return data;
    }

    private int chunkIndex(LightChunkGetter chunkGetter, int chunkX, int chunkZ) {
        int rx = chunkX - this.chunkCenterX + 1;
        int rz = chunkZ - this.chunkCenterZ + 1;
        if (this.chunkCenterX == UNSET || Integer.compareUnsigned(rx, 3) >= 0 || Integer.compareUnsigned(rz, 3) >= 0) {
            this.loadChunkWindow(chunkX, chunkZ);
            rx = rz = 1;
        }
        int index = rx + 3 * rz;
        this.loadChunkSlot(chunkGetter, index, rx - 1, rz - 1);
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
        // The active source/target references survive a window move; the next item must rebind.
        this.residentSectionKey = UNBOUND_SECTION;
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

    static BlockStateRawIdAccess blockAccessAt(ChunkAccess chunk, int sectionY) {
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

    public static int rawIdAt(ChunkAccess chunk, int x, int y, int z) {
        BlockStateRawIdAccess access = blockAccessAt(chunk, y >> 4);
        if (access == null) {
            return -1;
        }
        return access.getRawId(x & 15, y & 15, z & 15);
    }

}
