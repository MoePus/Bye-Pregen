package com.moepus.byepregen.yalight.storage;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

import java.util.Arrays;

public final class YAChunkLightData {
    private final ChunkPos pos;
    private final int minLightSection;
    private final YANibbleArray[] updating;
    private final boolean[] dirtySections;
    private int[] dirtyIndices;
    private int dirtyCount;
    private boolean queuedDirty;
    private volatile YANibbleArray[] visible;
    private volatile boolean lightEnabled;
    private boolean edgeCheckReady;
    private final YALightSaveState saveState;
    private boolean pendingPublication;

    public YAChunkLightData(ChunkPos pos, LevelHeightAccessor level) {
        this(pos, level, new YALightSaveState());
    }

    public YAChunkLightData(ChunkPos pos, LevelHeightAccessor level, YALightSaveState saveState) {
        this.saveState = saveState;
        this.pos = pos;
        this.minLightSection = YALightStorage.minLightSection(level);
        this.updating = YALightStorage.create(level);
        this.visible = this.updating.clone();
        this.dirtySections = new boolean[this.updating.length];
        this.dirtyIndices = new int[Math.min(8, this.updating.length)];
    }

    public boolean lightEnabled() {
        return this.lightEnabled;
    }

    public void setLightEnabled(boolean lightEnabled) {
        this.lightEnabled = lightEnabled;
    }

    public void finishPublication(boolean success) {
        if (this.pendingPublication) {
            this.pendingPublication = false;
            this.saveState.finish(success);
        }
    }

    boolean hasDirtySections() {
        return this.dirtyCount != 0;
    }

    public boolean edgeCheckReady() {
        return this.edgeCheckReady;
    }

    public void setEdgeCheckReady(boolean edgeCheckReady) {
        this.edgeCheckReady = edgeCheckReady;
    }

    public YANibbleArray getVisibleSection(int sectionY) {
        int index = this.index(sectionY);
        if (index < 0) {
            return null;
        }
        return this.getVisibleSectionByIndex(index);
    }

    public YANibbleArray[] visibleSections() {
        return this.visible;
    }

    public int minLightSection() {
        return this.minLightSection;
    }

    public YANibbleArray getVisibleSectionByIndex(int index) {
        YANibbleArray[] visible = this.visible;
        return visible[index];
    }

    public YANibbleArray getUpdatingSectionByIndex(int index) {
        return this.updating[index];
    }

    public YANibbleArray getOrCreateUpdatingSection(int sectionY) {
        int index = this.index(sectionY);
        if (index < 0) {
            return null;
        }
        return this.getOrCreateUpdatingSectionByIndex(index);
    }

    public YANibbleArray getOrCreateUpdatingSectionByIndex(int index) {
        YANibbleArray nibble = this.updating[index];
        if (nibble == null || nibble.isNullUpdating()) {
            if (nibble != null) {
                nibble.retirePublished();
            }
            nibble = new YANibbleArray();
            this.updating[index] = nibble;
        }
        return nibble;
    }

    public void setSection(int sectionY, YANibbleArray nibble, YALightStorage storage) {
        if (nibble == null || nibble.isNullVisible()) {
            if (nibble != null) {
                nibble.discardUnpublished();
            }
            this.removeSection(sectionY, storage);
            return;
        }
        int index = this.index(sectionY);
        if (index < 0) {
            nibble.discardUnpublished();
            return;
        }
        this.replacePublished(index, nibble);
        storage.markDirty(this, index);
    }

    public void loadInitialSection(int sectionY, YANibbleArray nibble) {
        if (nibble == null || nibble.isNullVisible()) {
            if (nibble != null) {
                nibble.discardUnpublished();
            }
            return;
        }
        int index = this.index(sectionY);
        if (index < 0) {
            nibble.discardUnpublished();
            return;
        }
        this.replaceUnpublished(index, nibble);
    }

    public void finishInitialLoad() {
        this.visible = this.updating.clone();
    }

    public void setFullSection(int sectionY, YALightStorage storage) {
        int index = this.index(sectionY);
        if (index < 0) {
            return;
        }
        YANibbleArray old = this.updating[index];
        if (old != null && old.isFullUpdating()) {
            return;
        }
        this.replacePublished(index, YANibbleArray.fullArray());
        storage.markDirty(this, index);
    }

    public void removeSection(int sectionY, YALightStorage storage) {
        int index = this.index(sectionY);
        if (index < 0) {
            return;
        }
        YANibbleArray old = this.updating[index];
        if (old == null) {
            return;
        }
        old.retirePublished();
        this.updating[index] = null;
        storage.markDirty(this, index);
    }

    public boolean markDirty(int index) {
        if (!this.pendingPublication) {
            this.saveState.begin();
            this.pendingPublication = true;
        }
        if (!this.dirtySections[index]) {
            this.dirtySections[index] = true;
            if (this.dirtyCount >= this.dirtyIndices.length) {
                this.dirtyIndices = Arrays.copyOf(this.dirtyIndices, Math.max(this.dirtyIndices.length << 1, 1));
            }
            this.dirtyIndices[this.dirtyCount++] = index;
        }
        if (this.queuedDirty) {
            return false;
        }
        this.queuedDirty = true;
        return true;
    }

    public int publishDirty(LightChunkGetter chunkGetter, LightLayer layer) {
        for (int i = 0; i < this.dirtyCount; ++i) {
            int index = this.dirtyIndices[i];
            this.dirtySections[index] = false;
            YANibbleArray nibble = this.updating[index];
            if (nibble != null && nibble.isDirty()) {
                nibble.publish();
            }
        }
        if (this.dirtyCount != 0) {
            /*
             * Publish every section and the chunk-level visible array before exposing any callback.
             * The shared save state remains pending until the top-level pass finishes BOTH layers,
             * including writes to halo chunks. Save capture validates its short read interval and
             * retains immutable COW bytes; this is not a world-wide or disk-I/O transaction.
             */
            this.visible = this.updating.clone();
        }
        int count = this.dirtyCount;
        for (int i = 0; i < count; ++i) {
            int index = this.dirtyIndices[i];
            chunkGetter.onLightUpdate(layer, SectionPos.of(this.pos, this.sectionY(index)));
        }
        this.dirtyCount = 0;
        this.queuedDirty = false;
        return count;
    }

    public void clear() {
        this.saveState.begin();
        for (YANibbleArray nibble : this.updating) {
            if (nibble != null) {
                nibble.retirePublished();
            }
        }
        Arrays.fill(this.updating, null);
        Arrays.fill(this.dirtySections, false);
        this.visible = this.updating.clone();
        this.dirtyCount = 0;
        this.queuedDirty = false;
        this.lightEnabled = false;
        this.edgeCheckReady = false;
        this.finishPublication(true);
        this.saveState.finish(true);
    }

    private void replacePublished(int index, YANibbleArray nibble) {
        YANibbleArray old = this.updating[index];
        if (old != null && old != nibble) {
            old.retirePublished();
        }
        this.updating[index] = nibble;
    }

    private void replaceUnpublished(int index, YANibbleArray nibble) {
        YANibbleArray old = this.updating[index];
        if (old != null && old != nibble) {
            old.discardUnpublished();
        }
        this.updating[index] = nibble;
    }

    private int index(int sectionY) {
        int index = sectionY - this.minLightSection;
        return index >= 0 && index < this.updating.length ? index : -1;
    }

    private int sectionY(int index) {
        return this.minLightSection + index;
    }
}
