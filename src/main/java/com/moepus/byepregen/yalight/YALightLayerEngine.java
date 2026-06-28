package com.moepus.byepregen.yalight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;

abstract class YALightLayerEngine implements LayerLightEventListener {
    protected final LightChunkGetter chunkGetter;
    protected final BlockGetter levelReader;
    protected final LightLayer layer;
    protected final YALightStorage storage;
    protected final int minLightSection;
    protected final int maxLightSection;
    private final YALightQueue queue = new YALightQueue();
    private final YALongQueue decreaseQueue = new YALongQueue();
    private final YALongQueue increaseQueue = new YALongQueue();
    protected final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    YALightLayerEngine(LightChunkGetter chunkGetter, LevelHeightAccessor level, LightLayer layer) {
        this.chunkGetter = chunkGetter;
        this.levelReader = chunkGetter.getLevel();
        this.layer = layer;
        this.storage = new YALightStorage(chunkGetter, level, layer);
        this.minLightSection = this.storage.minLightSection();
        this.maxLightSection = this.storage.maxLightSection();
    }

    @Override
    public final void checkBlock(BlockPos pos) {
        this.queue.queueCheck(pos);
    }

    @Override
    public final boolean hasLightWork() {
        return !this.queue.isEmpty() || !this.decreaseQueue.isEmpty() || !this.increaseQueue.isEmpty();
    }

    @Override
    public final int runLightUpdates() {
        try {
            int work = 0;
            this.queue.enableSourceChunks(this.storage);
            this.clearRunCache();
            YALightQueue.ChunkTask task;
            while ((task = this.queue.poll()) != null) {
                work += this.runTask(task);
                this.queue.recycle(task);
            }
            // Removal is processed before additions so stale light is cleared before sources re-add it.
            work += this.propagateDecreases();
            work += this.propagateIncreases();
            work += this.storage.publishDirty(this.chunkGetter, this.layer);
            return work;
        } finally {
            this.clearRunCache();
        }
    }

    private int runTask(YALightQueue.ChunkTask task) {
        int work = 0;
        if (task.source) {
            this.propagateLightSourcesInternal(task.chunkX(), task.chunkZ());
            ++work;
        } else if (task.hasSourceSections()) {
            work += this.propagateLightSectionsInternal(task.chunkX(), task.maxSourceSection, task.sourceSectionCount, task.chunkZ());
        }
        while (!task.checks.isEmpty()) {
            this.checkBlockInternal(task.pollCheckPos());
            ++work;
        }
        return work;
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        if (!isEmpty) {
            this.storage.getOrCreateSection(pos.x(), pos.y(), pos.z());
        }
        this.queue.queueSourceSection(pos);
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean lightEnabled) {
        this.storage.setLightEnabled(pos, lightEnabled);
        this.clearRunCache();
    }

    @Override
    public void propagateLightSources(ChunkPos pos) {
        this.queue.queueSource(pos);
    }

    @Override
    public DataLayer getDataLayerData(SectionPos pos) {
        YANibbleArray nibble = this.getNibble(pos.x(), pos.y(), pos.z());
        return nibble == null ? null : nibble.toVanilla();
    }

    @Override
    public abstract int getLightValue(BlockPos pos);

    public final void queueSectionData(SectionPos pos, DataLayer dataLayer) {
        this.queueNibble(pos, YANibbleArray.fromVanilla(dataLayer));
    }

    public final void queueOwnedSectionBytes(SectionPos pos, byte[] data) {
        this.queueNibble(pos, YANibbleArray.fromOwnedBytes(data));
    }

    public final void queueZeroSectionData(SectionPos pos) {
        this.queueNibble(pos, new YANibbleArray());
    }

    private void queueNibble(SectionPos pos, YANibbleArray nibble) {
        this.storage.setSection(pos, nibble);
    }

    public String getDebugData(SectionPos pos) {
        YANibbleArray nibble = this.getNibble(pos.x(), pos.y(), pos.z());
        return nibble == null || !nibble.hasVisibleLayer() ? "n/a" : "YA";
    }

    public boolean lightOnInSection(SectionPos pos) {
        return this.storage.lightOnInSection(pos);
    }

    public void clearChunk(ChunkPos pos) {
        this.queue.removeChunk(pos);
        this.storage.removeChunk(pos);
    }

    public void retainData(ChunkPos pos, boolean retainData) {
        // YA light data is chunk-owned; retainData is a vanilla storage side channel.
    }

    protected void clearRunCache() {
    }

    protected abstract void checkBlockInternal(long pos);

    protected abstract void propagateLightSourcesInternal(int chunkX, int chunkZ);

    protected abstract void propagateLightSectionInternal(int chunkX, int sectionY, int chunkZ);

    protected int propagateLightSectionsInternal(int chunkX, int maxSectionY, int sectionCount, int chunkZ) {
        this.propagateLightSectionInternal(chunkX, maxSectionY, chunkZ);
        return sectionCount;
    }

    protected abstract int getSourceLight(long pos, BlockState state);

    protected abstract void propagateIncrease(long pos, long meta);

    protected abstract void propagateDecrease(long pos, long meta);

    protected boolean canUseSection(int chunkX, int sectionY, int chunkZ) {
        return sectionY >= this.minLightSection
                && sectionY <= this.maxLightSection
                && this.storage.lightEnabled(chunkX, chunkZ);
    }

    protected final YANibbleArray getNibble(int chunkX, int sectionY, int chunkZ) {
        return this.storage.getSection(chunkX, sectionY, chunkZ);
    }

    protected final void enqueueDecrease(long pos, int level, int directions) {
        this.decreaseQueue.add(pos);
        this.decreaseQueue.add(YALightMath.meta(level, directions, 0L));
    }

    protected final void enqueueIncrease(long pos, int level, int directions, long flags) {
        this.increaseQueue.add(pos);
        this.increaseQueue.add(YALightMath.meta(level, directions, flags));
    }

    private int propagateDecreases() {
        int work = 0;
        while (!this.decreaseQueue.isEmpty()) {
            long pos = this.decreaseQueue.poll();
            long meta = this.decreaseQueue.poll();
            this.propagateDecrease(pos, meta);
            ++work;
        }
        this.decreaseQueue.clear();
        return work;
    }

    private int propagateIncreases() {
        int work = 0;
        while (!this.increaseQueue.isEmpty()) {
            long pos = this.increaseQueue.poll();
            long meta = this.increaseQueue.poll();
            this.propagateIncrease(pos, meta);
            ++work;
        }
        this.increaseQueue.clear();
        return work;
    }

}
