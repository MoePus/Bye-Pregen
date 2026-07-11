package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.yalight.YALightEngineHolder;
import com.moepus.byepregen.yalight.YALightEngine;
import com.moepus.byepregen.yalight.YAThreadedLightScheduler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ProcessorMailbox;
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

@Mixin(ThreadedLevelLightEngine.class)
public abstract class ThreadedLevelLightEngineYAMixin {
    @Unique
    private static final String BYEPREGEN_LIGHT_SCHEDULER_WAKE_ON_ADD_PROPERTY = "byepregen.yaLightSchedulerWakeOnAdd";

    @Unique
    private static final boolean BYEPREGEN_LIGHT_SCHEDULER_WAKE_ON_ADD =
            Boolean.parseBoolean(System.getProperty(BYEPREGEN_LIGHT_SCHEDULER_WAKE_ON_ADD_PROPERTY, "true"));

    @Shadow
    @Final
    private ProcessorMailbox<Runnable> taskMailbox;

    @Shadow
    @Final
    private AtomicBoolean scheduled;

    @Unique
    private final YAThreadedLightScheduler byepregen$lightScheduler = new YAThreadedLightScheduler();

    @Unique
    private YALightEngine byepregen$yaEngine() {
        return ((YALightEngineHolder)this).byepregen$getYALightEngine();
    }

    @Unique
    private boolean byepregen$scheduleDrain() {
        if (!this.byepregen$hasScheduledWork() || !this.scheduled.compareAndSet(false, true)) {
            return false;
        }
        this.byepregen$tellDrainTask();
        return true;
    }

    @Unique
    private boolean byepregen$scheduleDrainWithKnownWork() {
        if (!this.scheduled.compareAndSet(false, true)) {
            return false;
        }
        this.byepregen$tellDrainTask();
        return true;
    }

    @Unique
    private void byepregen$tellDrainTask() {
        this.taskMailbox.tell(() -> {
            try {
                this.byepregen$drainUntilIdle();
            } finally {
                this.scheduled.set(false);
                this.byepregen$scheduleDrain();
            }
        });
    }

    @Unique
    private boolean byepregen$hasScheduledWork() {
        return this.byepregen$lightScheduler.hasWork() || this.byepregen$yaEngine().hasLightWork();
    }

    @Unique
    private void byepregen$drainUntilIdle() {
        while (this.byepregen$hasScheduledWork()) {
            this.byepregen$lightScheduler.drain(this.byepregen$yaEngine(), YAThreadedLightScheduler.BATCH_SIZE);
        }
    }

    /**
     * @author MoePus
     * @reason Route accepted light tasks through YA's top-level scheduler.
     */
    @Overwrite
    public void addTask(
            int chunkX,
            int chunkZ,
            IntSupplier queueLevelSupplier,
            ThreadedLevelLightEngine.TaskType type,
            Runnable task
    ) {
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        this.byepregen$lightScheduler.enqueue(chunkKey, type, task);
        if (BYEPREGEN_LIGHT_SCHEDULER_WAKE_ON_ADD) {
            this.byepregen$scheduleDrainWithKnownWork();
        }
    }

    @Unique
    private void byepregen$addLightChunkTask(
            int chunkX,
            int chunkZ,
            Runnable prepare,
            Runnable complete
    ) {
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        this.byepregen$lightScheduler.enqueueLightChunk(chunkKey, prepare, complete);
        if (BYEPREGEN_LIGHT_SCHEDULER_WAKE_ON_ADD) {
            this.byepregen$scheduleDrainWithKnownWork();
        }
    }

    /**
     * @author MoePus
     * @reason YA has its own top-level scheduler and still runs on vanilla's light executor.
     */
    @Overwrite
    public void tryScheduleUpdate() {
        this.byepregen$scheduleDrain();
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
        CompletableFuture<ChunkAccess> future = new CompletableFuture<>();
        chunk.setLightCorrect(false);
        Runnable prepare = Util.name(() -> {
            YALightEngine engine = this.byepregen$yaEngine();
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < chunk.getSectionsCount(); ++i) {
                LevelChunkSection section = sections[i];
                if (!section.hasOnlyAir()) {
                    engine.updateSectionStatus(chunk, chunk.getSectionYFromSectionIndex(i), false);
                }
            }
            engine.setLightEnabled(chunk, true);
            if (!isLighted) {
                engine.propagateFreshLightSources(chunkPos);
            } else {
                // Light saved by other engines omits the all-15 sky sections above the surface.
                engine.restoreSavedSkyLight(chunk);
            }
        }, () -> "YA lightChunk " + chunkPos + " " + isLighted);
        Runnable complete = () -> {
            chunk.setLightCorrect(true);
            future.complete(chunk);
        };
        this.byepregen$addLightChunkTask(chunkPos.x, chunkPos.z, prepare, complete);
        return future;
    }

    /**
     * @author MoePus
     * @reason Complete pending-task barriers through YA's scheduler instead of vanilla lightTasks.
     */
    @Overwrite
    public CompletableFuture<?> waitForPendingTasks(int x, int z) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.addTask(x, z, () -> 0, ThreadedLevelLightEngine.TaskType.POST_UPDATE, () -> future.complete(null));
        return future;
    }

}
