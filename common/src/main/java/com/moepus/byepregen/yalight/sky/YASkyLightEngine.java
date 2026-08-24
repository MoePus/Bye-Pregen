package com.moepus.byepregen.yalight.sky;

import com.moepus.byepregen.yalight.engine.YAChunkRunCache;
import com.moepus.byepregen.yalight.engine.YADLongQueue;
import com.moepus.byepregen.yalight.engine.YALightBlockAccess;
import com.moepus.byepregen.yalight.engine.YALightLayerEngine;
import com.moepus.byepregen.yalight.engine.YALightMath;
import com.moepus.byepregen.yalight.engine.YALightQueue;
import com.moepus.byepregen.yalight.scheduler.YAFreshLightRequest;
import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import com.moepus.byepregen.yalight.storage.YALightStorage;
import com.moepus.byepregen.yalight.storage.YANibbleArray;
import com.moepus.byepregen.yalight.storage.YAVisibleLightReader;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightEngine;

public final class YASkyLightEngine extends SkyLightEngine implements YALightLayerEngine {
    final LightChunkGetter chunkGetter;
    final YALightStorage storage;
    final YASkySourceCache sourceCache = new YASkySourceCache();
    final int minLightSection;
    final int maxLightSection;
    final YALightBlockAccess blocks;
    final YASkyOwnerTransfers ownerTransfers;
    final YASkySourcePropagator sources;
    private final YALightQueue lightQueue = new YALightQueue(LightLayer.SKY);
    private final YADLongQueue decreaseQueue = new YADLongQueue();
    private final YADLongQueue increaseQueue = new YADLongQueue();

    public YASkyLightEngine(LightChunkGetter chunkGetter) {
        super(chunkGetter, null);
        this.chunkGetter = chunkGetter;
        BlockGetter levelReader = chunkGetter.getLevel();
        this.storage = new YALightStorage(chunkGetter, chunkGetter.getLevel(), LightLayer.SKY);
        this.minLightSection = this.storage.minLightSection();
        this.maxLightSection = this.storage.maxLightSection();
        this.blocks = new YALightBlockAccess(
                this.sourceCache, chunkGetter, levelReader, new BlockPos.MutableBlockPos());
        this.ownerTransfers = new YASkyOwnerTransfers(this::propagateTransferredEdge);
        this.sources = new YASkySourcePropagator(
                this, this.sourceCache, chunkGetter.getLevel().getMinY() - 1);
    }

    @Override
    public LightChunkGetter chunkGetter() {
        return this.chunkGetter;
    }

    @Override
    public LightLayer lightLayer() {
        return LightLayer.SKY;
    }

    @Override
    public YALightStorage storage() {
        return this.storage;
    }

    @Override
    public YAChunkRunCache runCache() {
        return this.sourceCache;
    }

    @Override
    public YALightQueue lightQueue() {
        return this.lightQueue;
    }

    @Override
    public YADLongQueue decreaseQueue() {
        return this.decreaseQueue;
    }

    @Override
    public YADLongQueue increaseQueue() {
        return this.increaseQueue;
    }

    @Override
    public YALightBlockAccess blockAccess() {
        return this.blocks;
    }

    @Override
    public int sourceLight(long pos, int block) {
        return this.blocks.isFull(block) ? 0 : this.getSourceLight(pos);
    }

    public void setDeclaredFreshOwnerDomain(Iterable<ChunkPos> owners) {
        this.ownerTransfers.setDeclaredDomain(owners);
    }

    public void clearDeclaredFreshOwnerDomain() {
        this.ownerTransfers.clearPersistentState();
    }

    @Override
    public void finishRun() {
        this.ownerTransfers.finishRun();
    }

    @Override
    public void propagateFreshLightSources(YAFreshLightRequest request) {
        this.ownerTransfers.register(request.owner().getPos().pack());
        YALightLayerEngine.super.propagateFreshLightSources(request);
    }

    @Override
    public void clearChunk(ChunkPos pos) {
        YALightLayerEngine.super.clearChunk(pos);
        this.ownerTransfers.remove(pos.pack());
    }

    @Override
    public void checkBlock(BlockPos pos) {
        YALightLayerEngine.super.checkBlock(pos);
    }

    @Override
    public boolean hasLightWork() {
        return YALightLayerEngine.super.hasLightWork();
    }

    @Override
    public int runLightUpdates() {
        return YALightLayerEngine.super.runLightUpdates();
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        YALightLayerEngine.super.updateSectionStatus(pos, isEmpty);
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean lightEnabled) {
        YALightLayerEngine.super.setLightEnabled(pos, lightEnabled);
    }

    @Override
    public void propagateLightSources(ChunkPos pos) {
        YALightLayerEngine.super.propagateLightSources(pos);
    }

    @Override
    public DataLayer getDataLayerData(SectionPos pos) {
        return YALightLayerEngine.super.getDataLayerData(pos);
    }

    @Override
    public int getLightValue(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        YAChunkLightData data = this.storage.existingData(this.storage.chunkAccess(chunkX, chunkZ));
        return this.getVisibleLightValue(pos, data);
    }

    public int getVisibleLightValue(BlockPos pos, YAChunkLightData data) {
        YANibbleArray[] sections = data == null
                ? YAVisibleLightReader.EMPTY_SECTIONS
                : data.visibleSections();
        int sectionIndex = (pos.getY() >> 4) - this.minLightSection;
        return YAVisibleLightReader.skyLight(sections, sectionIndex, pos);
    }

    @Override
    public void queueSectionData(long sectionPos, @Nullable DataLayer data) {
        this.queueSectionData(SectionPos.of(sectionPos), data);
    }

    @Override
    public void retainData(ChunkPos pos, boolean retainData) {
        // YA light data is chunk-owned.
    }

    @Override
    public String getDebugData(long sectionPos) {
        return this.getDebugData(SectionPos.of(sectionPos));
    }

    @Override
    public LayerLightSectionStorage.SectionType getDebugSectionType(long sectionPos) {
        return this.lightOnInSection(SectionPos.of(sectionPos))
                ? LayerLightSectionStorage.SectionType.LIGHT_AND_DATA
                : LayerLightSectionStorage.SectionType.EMPTY;
    }

    @Override
    public void checkBlockInternal(long pos) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        this.sources.repairDirtyColumn(x, z);

        int source = this.getSourceLight(pos);
        int current = this.getCachedUpdatingLight(x, y, z);
        if (source == 15 && current < 15) {
            this.setCachedUpdatingLight(x, y, z, 15);
            this.sources.enqueueSkySourceIncrease(x, y, z, this.sources.skySourceDirections(x, y, z), false);
        } else if (current > source) {
            this.setCachedUpdatingLight(x, y, z, source);
            this.enqueueDecrease(pos, current, YALightMath.ALL_DIRECTIONS);
        } else if (current > 0) {
            this.enqueueDecrease(pos, current, YALightMath.ALL_DIRECTIONS);
            this.enqueueIncrease(pos, current, YALightMath.ALL_DIRECTIONS, YALightMath.FLAG_RECHECK);
        } else {
            this.enqueueDecrease(pos, 1, YALightMath.ALL_DIRECTIONS);
        }
    }

    @Override
    public void propagateLightSourcesInternal(ChunkAccess chunk, boolean fresh) {
        if (this.sources.propagateLightSources(chunk, fresh) && fresh) {
            this.ownerTransfers.markInitialized(chunk.getPos().pack());
        }
    }

    @Override
    public void propagateIncrease(long pos, long meta) {
        YASkyLightPropagation.propagateIncrease(this, pos, meta);
    }

    int tryPropagateIncreaseEdge(
            int x,
            int y,
            int z,
            int level,
            int directionIndex,
            int fromBlock,
            long flags
    ) {
        return YASkyLightPropagation.tryPropagateIncreaseEdge(
                this, x, y, z, level, directionIndex, fromBlock, flags);
    }

    @Override
    public int propagateAfterIncreases() {
        return this.ownerTransfers.drain();
    }

    @Override
    public void propagateDecrease(long pos, long meta) {
        YASkyLightPropagation.propagateDecrease(this, pos, meta);
    }

    int getSourceLight(long pos) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        int sourceCode = this.sourceCache.enabledSourceCode(this.storage, x, z);
        return this.sources.sourceYCode(y) >= sourceCode ? 15 : 0;
    }

    public void restoreSavedSkyLight(ChunkAccess chunk) {
        this.sources.restoreSavedSkyLight(chunk);
    }

    private void propagateTransferredEdge(long fromPos, int level, int directionIndex, long flags) {
        YASkyLightPropagation.propagateTransferredEdge(this, fromPos, level, directionIndex, flags);
    }
}
