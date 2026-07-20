package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.mixin.ChunkMapAccessor;
import com.moepus.byepregen.yalight.YALightEngineHolder;
import com.moepus.byepregen.yalight.YALightEngine;
import com.moepus.byepregen.yalight.YAImmediateChunkAccess;
import com.moepus.byepregen.yalight.YAThreadedLightScheduler;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
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
    private static final TicketType<ChunkPos> YA_LIGHT_WORK =
            TicketType.create("ya_light_work", Comparator.comparingLong(ChunkPos::toLong));

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

    @Shadow
    @Final
    private ChunkMap chunkMap;

    @Unique
    private final Long2IntOpenHashMap byepregen$ticketReferences = new Long2IntOpenHashMap();

    @Unique
    private final YAThreadedLightScheduler byepregen$lightScheduler = new YAThreadedLightScheduler(
            this::byepregen$acquireTaskTicket,
            this::byepregen$releaseTaskTicket
    );

    @Unique
    private YALightEngine byepregen$yaEngine() {
        return ((YALightEngineHolder)this).byepregen$getYALightEngine();
    }

    @Unique
    private ServerLevel byepregen$level() {
        return ((ChunkMapAccessor)this.chunkMap).byepregen$getLevel();
    }

    @Unique
    private void byepregen$acquireTaskTicket(long chunkKey) {
        int previous = this.byepregen$ticketReferences.addTo(chunkKey, 1);
        if (previous == 0) {
            ChunkPos pos = new ChunkPos(chunkKey);
            this.byepregen$level().getChunkSource().addRegionTicket(YA_LIGHT_WORK, pos, 0, pos);
        }
    }

    @Unique
    private void byepregen$releaseTaskTicket(long chunkKey) {
        ServerLevel level = this.byepregen$level();
        ((ChunkMapAccessor)this.chunkMap).byepregen$getMainThreadExecutor().execute(() -> {
            int references = this.byepregen$ticketReferences.get(chunkKey);
            if (references <= 1) {
                this.byepregen$ticketReferences.remove(chunkKey);
                ChunkPos pos = new ChunkPos(chunkKey);
                level.getChunkSource().removeRegionTicket(YA_LIGHT_WORK, pos, 0, pos);
            } else {
                this.byepregen$ticketReferences.put(chunkKey, references - 1);
            }
        });
    }

    @Unique
    private void byepregen$enqueueTask(
            long chunkKey,
            ThreadedLevelLightEngine.TaskType type,
            Runnable task,
            boolean ticketed
    ) {
        this.byepregen$lightScheduler.enqueue(chunkKey, type, task, ticketed);
        if (BYEPREGEN_LIGHT_SCHEDULER_WAKE_ON_ADD) {
            this.byepregen$scheduleDrainWithKnownWork();
        }
    }

    @Unique
    private void byepregen$enqueueRuntimeTask(
            int chunkX,
            int chunkZ,
            ThreadedLevelLightEngine.TaskType type,
            Runnable task
    ) {
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        ChunkAccess chunk = ((YAImmediateChunkAccess)this.byepregen$level().getChunkSource())
                .byepregen$getAnyChunkNow(chunkX, chunkZ);
        if (chunk == null || !chunk.getStatus().isOrAfter(ChunkStatus.LIGHT)) {
            return;
        }
        if (chunk.getStatus() != ChunkStatus.FULL) {
            this.byepregen$enqueueTask(chunkKey, type, task, false);
            return;
        }
        var mainThreadExecutor = ((ChunkMapAccessor)this.chunkMap).byepregen$getMainThreadExecutor();
        if (!mainThreadExecutor.isSameThread()) {
            mainThreadExecutor.execute(() -> this.byepregen$enqueueRuntimeTask(chunkX, chunkZ, type, task));
            return;
        }
        this.byepregen$enqueueTask(chunkKey, type, task, true);
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
    private void addTask(
            int chunkX,
            int chunkZ,
            IntSupplier queueLevelSupplier,
            ThreadedLevelLightEngine.TaskType type,
            Runnable task
    ) {
        this.byepregen$enqueueTask(ChunkPos.asLong(chunkX, chunkZ), type, task, false);
    }

    /**
     * @author MoePus
     * @reason Protect runtime block-light work without ticketing chunk-load internals.
     */
    @Overwrite
    public void checkBlock(BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        this.byepregen$enqueueRuntimeTask(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()),
                ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
                Util.name(() -> this.byepregen$yaEngine().checkBlock(immutablePos), () -> "YA checkBlock " + immutablePos)
        );
    }

    /**
     * @author MoePus
     * @reason Protect runtime section changes without ticketing chunk-load internals.
     */
    @Overwrite
    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        this.byepregen$enqueueRuntimeTask(
                pos.x(),
                pos.z(),
                ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
                Util.name(() -> this.byepregen$yaEngine().updateSectionStatus(pos, isEmpty),
                        () -> "YA updateSectionStatus " + pos + " " + isEmpty)
        );
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
        // YA light data belongs to the chunk object. A delayed position-based unload task can
        // otherwise disable a replacement chunk that has already loaded at the same position.
    }

    /**
     * @author MoePus
     * @reason Route queued vanilla section data into YA storage.
     */
    @Overwrite
    public void queueSectionData(LightLayer layer, SectionPos pos, @Nullable DataLayer dataLayer) {
        this.byepregen$enqueueTask(
                ChunkPos.asLong(pos.x(), pos.z()),
                ThreadedLevelLightEngine.TaskType.PRE_UPDATE,
                Util.name(() -> this.byepregen$yaEngine().queueSectionData(layer, pos, dataLayer), () -> "YA queueData " + pos),
                false
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
            engine.setEdgeCheckReady(chunk, false);
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
                // Light saves omit all-15 sky sections above the surface.
                engine.restoreSavedSkyLight(chunk);
                engine.setEdgeCheckReady(chunk, true);
                engine.checkChunkEdges(chunk);
            }
        }, () -> "YA lightChunk " + chunkPos + " " + isLighted);
        Runnable complete = () -> {
            this.byepregen$yaEngine().setEdgeCheckReady(chunk, true);
            chunk.setLightCorrect(true);
            future.complete(chunk);
        };
        this.byepregen$addLightChunkTask(chunkPos.x, chunkPos.z, prepare, complete);
        return future;
    }

}
