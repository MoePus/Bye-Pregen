package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.yalight.YALightEngineHolder;
import com.moepus.byepregen.yalight.YALightEngine;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ThreadedLevelLightEngine.class)
public abstract class ThreadedLevelLightEngineYAMixin {
    @Unique
    private static final int BYEPREGEN_LIGHT_TASK_BATCH_SIZE = 128;

    @Shadow
    @Final
    private ObjectList<Pair<ThreadedLevelLightEngine.TaskType, Runnable>> lightTasks;

    @Shadow
    @Final
    private ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> sorterMailbox;

    @Shadow
    public abstract void tryScheduleUpdate();

    @Unique
    private YALightEngine byepregen$yaEngine() {
        return ((YALightEngineHolder)this).byepregen$getYALightEngine();
    }

    @ModifyConstant(
            method = "runUpdate",
            constant = @Constant(intValue = 1000)
    )
    private int byepregen$limitLightTaskBatch(int vanillaBatchSize) {
        return BYEPREGEN_LIGHT_TASK_BATCH_SIZE;
    }

    /**
     * @author MoePus
     * @reason Wake the threaded light mailbox as soon as a sorter task reaches the light queue.
     */
    @Overwrite
    public void addTask(
            int chunkX,
            int chunkZ,
            IntSupplier queueLevelSupplier,
            ThreadedLevelLightEngine.TaskType type,
            Runnable task
    ) {
        this.sorterMailbox.tell(ChunkTaskPriorityQueueSorter.message(() -> {
            this.lightTasks.add(Pair.of(type, task));
            this.tryScheduleUpdate();
        }, ChunkPos.asLong(chunkX, chunkZ), queueLevelSupplier));
    }

    /**
     * @author MoePus
     * @reason Chunk-owned YA light storage has no retain-data side channel.
     */
    @Overwrite
    public void updateChunkStatus(ChunkPos chunkPos) {
        this.addTask(chunkPos.x, chunkPos.z, () -> 0, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            this.byepregen$yaEngine().setLightEnabled(chunkPos, false);
            ThreadedLevelLightEngine self = (ThreadedLevelLightEngine)(Object)this;
            for (int sectionY = self.getMinLightSection(); sectionY < self.getMaxLightSection(); ++sectionY) {
                SectionPos sectionPos = SectionPos.of(chunkPos, sectionY);
                this.byepregen$yaEngine().queueSectionData(LightLayer.BLOCK, sectionPos, null);
                this.byepregen$yaEngine().queueSectionData(LightLayer.SKY, sectionPos, null);
            }
            for (int sectionY = self.getMinLightSection() + 1; sectionY < self.getMaxLightSection() - 1; ++sectionY) {
                this.byepregen$yaEngine().updateSectionStatus(SectionPos.of(chunkPos, sectionY), true);
            }
        }, () -> "YA updateChunkStatus " + chunkPos + " true"));
    }

    /**
     * @author MoePus
     * @reason Route queued vanilla section data into YA storage.
     */
    @Overwrite
    public void queueSectionData(LightLayer layer, SectionPos pos, @Nullable DataLayer dataLayer) {
        this.addTask(
                pos.x(),
                pos.z(),
                () -> 0,
                ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
                Util.name(() -> this.byepregen$yaEngine().queueSectionData(layer, pos, dataLayer), () -> "YA queueData " + pos)
        );
    }

    /**
     * @author MoePus
     * @reason YA light data is chunk-owned; retainData is a vanilla storage side channel.
     */
    @Overwrite
    public void retainData(ChunkPos pos, boolean retain) {
        // No-op for vanilla/cross-mod callers that still issue retainData.
    }

    /**
     * @author MoePus
     * @reason YA initializes chunk-owned light state in lightChunk; no separate initialization future is needed.
     */
    @Overwrite
    public CompletableFuture<ChunkAccess> initializeLight(ChunkAccess chunk, boolean lightEnabled) {
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * @author MoePus
     * @reason Route threaded chunk lighting through YA without touching vanilla storage.
     */
    @Overwrite
    public CompletableFuture<ChunkAccess> lightChunk(ChunkAccess chunk, boolean isLighted) {
        ChunkPos chunkPos = chunk.getPos();
        chunk.setLightCorrect(false);
        this.addTask(chunkPos.x, chunkPos.z, () -> 0, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < chunk.getSectionsCount(); ++i) {
                LevelChunkSection section = sections[i];
                if (!section.hasOnlyAir()) {
                    this.byepregen$yaEngine().updateSectionStatus(SectionPos.of(chunkPos, chunk.getSectionYFromSectionIndex(i)), false);
                }
            }
            this.byepregen$yaEngine().setLightEnabled(chunkPos, true);
            if (!isLighted) {
                this.byepregen$yaEngine().propagateLightSources(chunkPos);
            }
        }, () -> "YA lightChunk " + chunkPos + " " + isLighted));
        return CompletableFuture.supplyAsync(() -> {
            chunk.setLightCorrect(true);
            return chunk;
        }, task -> this.addTask(chunkPos.x, chunkPos.z, () -> 0, ThreadedLevelLightEngine.TaskType.POST_UPDATE, task));
    }

}
