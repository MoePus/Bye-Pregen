package com.moepus.byepregen.yalight.engine;

import com.moepus.byepregen.palette.access.BlockStateRawIdAccess;
import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import com.moepus.byepregen.yalight.storage.YALightStorage;
import com.moepus.byepregen.yalight.storage.YANibbleArray;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Writer-local access for one propagation item or edge, never a published section snapshot. */
public final class YASectionAccess {
    private ChunkAccess owner;
    private YAChunkLightData data;
    private int sectionY;
    private int storageIndex = -1;
    private YANibbleArray nibble;
    private BlockStateRawIdAccess blocks;
    private boolean blocksResolved;
    private boolean enabled;

    void bind(YALightStorage storage, ChunkAccess owner, YAChunkLightData data, int sectionY) {
        this.owner = owner;
        this.data = data;
        this.sectionY = sectionY;
        int index = storage.sectionIndex(sectionY);
        this.storageIndex = Integer.compareUnsigned(index, storage.lightSectionCount()) < 0 ? index : -1;
        this.refresh();
    }

    void refresh() {
        this.nibble = this.data != null && this.storageIndex >= 0
                ? this.data.getUpdatingSectionByIndex(this.storageIndex) : null;
        this.enabled = this.data != null && this.storageIndex >= 0 && this.data.lightEnabled();
        this.blocks = null;
        this.blocksResolved = false;
    }

    void clear() {
        this.owner = null;
        this.data = null;
        this.storageIndex = -1;
        this.refresh();
    }

    boolean canReuseBinding() {
        return this.owner != null && this.data != null && this.storageIndex >= 0;
    }

    public ChunkAccess owner() {
        return this.owner;
    }

    YAChunkLightData data() {
        return this.data;
    }

    int sectionY() {
        return this.sectionY;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public int light(int localIndex) {
        YANibbleArray current = this.nibble;
        return current == null ? 0 : current.getUpdating(localIndex);
    }

    public void setLight(YAChunkRunCache cache, YALightStorage storage, int localIndex, int value) {
        if (this.storageIndex < 0) {
            return;
        }
        if (this.data == null) {
            this.data = cache.createLightData(storage, this.owner);
            if (this.data == null) {
                return;
            }
            this.enabled = this.data.lightEnabled();
        }
        YANibbleArray current = this.nibble;
        if (current == null || current.isNullUpdating()) {
            this.nibble = current = this.data.getOrCreateUpdatingSectionByIndex(this.storageIndex);
        }
        if (current.setUpdatingAndGetDirtyTransition(localIndex, value)) {
            storage.markDirty(this.data, this.storageIndex);
        }
    }

    int rawId(int localIndex) {
        if (!this.blocksResolved) {
            this.blocks = YAChunkRunCache.blockAccessAt(this.owner, this.sectionY);
            this.blocksResolved = true;
        }
        BlockStateRawIdAccess access = this.blocks;
        return access == null ? -1 : access.getRawId(localIndex & 15, localIndex >>> 8, (localIndex >>> 4) & 15);
    }
}
