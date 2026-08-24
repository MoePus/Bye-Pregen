package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.mixin.accessor.yalight.ChunkMapLightAccessor;
import com.moepus.byepregen.yalight.access.YALightEngineHolder;
import com.moepus.byepregen.yalight.engine.YALightEngine;
import com.moepus.byepregen.yalight.access.YAImmediateChunkAccess;
import com.moepus.byepregen.yalight.scheduler.YAFreshLightRequest;
import com.moepus.byepregen.yalight.scheduler.YAThreadedLightScheduler;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.Util;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(ThreadedLevelLightEngine.class)
public abstract class ThreadedLevelLightEngineYAMixin {
    @Unique
    private static final TicketType byepregen$YA_LIGHT_WORK =
            new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);

    @Unique
    private static final String byepregen$LIGHT_SCHEDULER_WAKE_ON_ADD_PROPERTY = "byepregen.yaLightSchedulerWakeOnAdd";

    @Unique
    private static final boolean byepregen$LIGHT_SCHEDULER_WAKE_ON_ADD =
            Boolean.parseBoolean(System.getProperty(byepregen$LIGHT_SCHEDULER_WAKE_ON_ADD_PROPERTY, "true"));

    @Shadow
    @Final
    private ConsecutiveExecutor consecutiveExecutor;

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
        return ((ChunkMapLightAccessor)this.chunkMap).byepregen$getLevel();
    }

    @Unique
    private void byepregen$acquireTaskTicket(long chunkKey) {
        int previous = this.byepregen$ticketReferences.addTo(chunkKey, 1);
        if (previous == 0) {
            ChunkPos pos = ChunkPos.unpack(chunkKey);
            this.byepregen$level().getChunkSource().addTicketWithRadius(byepregen$YA_LIGHT_WORK, pos, 0);
        }
    }

    @Unique
    private void byepregen$releaseTaskTicket(long chunkKey) {
        ServerLevel level = this.byepregen$level();
        ((ChunkMapLightAccessor)this.chunkMap).byepregen$getMainThreadExecutor().execute(() -> {
            int references = this.byepregen$ticketReferences.get(chunkKey);
            if (references <= 1) {
                this.byepregen$ticketReferences.remove(chunkKey);
                ChunkPos pos = ChunkPos.unpack(chunkKey);
                level.getChunkSource().removeTicketWithRadius(byepregen$YA_LIGHT_WORK, pos, 0);
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
        if (byepregen$LIGHT_SCHEDULER_WAKE_ON_ADD) {
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
        long chunkKey = ChunkPos.pack(chunkX, chunkZ);
        ChunkAccess chunk = ((YAImmediateChunkAccess)this.byepregen$level().getChunkSource())
                .byepregen$getAnyChunkNow(chunkX, chunkZ);
        if (chunk == null || !chunk.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT)) {
            return;
        }
        if (chunk.getPersistedStatus() != ChunkStatus.FULL) {
            this.byepregen$enqueueTask(chunkKey, type, task, false);
            return;
        }
        var mainThreadExecutor = ((ChunkMapLightAccessor)this.chunkMap).byepregen$getMainThreadExecutor();
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
        this.consecutiveExecutor.schedule(() -> {
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
        this.byepregen$enqueueTask(ChunkPos.pack(chunkX, chunkZ), type, task, false);
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
    private void byepregen$addChunkLightPass(
            ChunkPos chunkPos,
            Runnable prepare,
            Runnable complete
    ) {
        this.byepregen$lightScheduler.enqueueLightChunk(chunkPos.pack(), prepare, complete);
        if (byepregen$LIGHT_SCHEDULER_WAKE_ON_ADD) {
            this.byepregen$scheduleDrainWithKnownWork();
        }
    }

    @Unique
    private static void byepregen$materializeNonEmptySections(YALightEngine engine, ChunkAccess chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < chunk.getSectionsCount(); ++i) {
            if (!sections[i].hasOnlyAir()) {
                engine.updateSectionStatus(chunk, chunk.getSectionYFromSectionIndex(i), false);
            }
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
                ChunkPos.pack(pos.x(), pos.z()),
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
     * @reason Initialize and publish chunk-owned YA sections before advancing the chunk pipeline.
     */
    @Overwrite
    public CompletableFuture<ChunkAccess> initializeLight(ChunkAccess chunk, boolean lightEnabled) {
        ChunkPos chunkPos = chunk.getPos();
        YALightEngine engine = this.byepregen$yaEngine();
        CompletableFuture<ChunkAccess> future = new CompletableFuture<>();
        Runnable prepare = Util.name(() -> {
            try {
                byepregen$materializeNonEmptySections(engine, chunk);
                engine.setLightEnabled(chunk, lightEnabled);
            } catch (RuntimeException | Error exception) {
                future.completeExceptionally(exception);
                throw exception;
            }
        }, () -> "YA initializeLight " + chunkPos + " " + lightEnabled);
        this.byepregen$addChunkLightPass(
                chunkPos,
                prepare,
                () -> future.complete(chunk)
        );
        return future;
    }

    /**
     * @author MoePus
     * @reason Route threaded chunk lighting through YA without touching vanilla storage.
     */
    @Overwrite
    public CompletableFuture<ChunkAccess> lightChunk(ChunkAccess chunk, boolean isLighted) {
        ChunkPos chunkPos = chunk.getPos();
        YALightEngine engine = this.byepregen$yaEngine();
        YAFreshLightRequest freshRequest = isLighted ? null : engine.createFreshLightRequest(chunk);
        CompletableFuture<ChunkAccess> future = freshRequest == null ? new CompletableFuture<>() : freshRequest;
        chunk.setLightCorrect(false);
        Runnable prepare = Util.name(() -> {
            engine.setEdgeCheckReady(chunk, false);
            byepregen$materializeNonEmptySections(engine, chunk);
            engine.setLightEnabled(chunk, true);
            if (freshRequest != null) {
                engine.propagateFreshLightSources(freshRequest);
            } else {
                // Light saves omit all-15 sky sections above the surface.
                engine.restoreSavedSkyLight(chunk);
                engine.setEdgeCheckReady(chunk, true);
                engine.checkChunkEdges(chunk);
            }
        }, () -> "YA lightChunk " + chunkPos + " " + isLighted);
        Runnable complete = () -> {
            boolean freshSucceeded = !(future instanceof YAFreshLightRequest request)
                    || request.succeeded() && engine.isCurrentOwner(request);
            if (freshSucceeded) {
                engine.setEdgeCheckReady(chunk, true);
                chunk.setLightCorrect(true);
            }
            future.complete(chunk);
        };
        this.byepregen$addChunkLightPass(chunkPos, prepare, complete);
        return future;
    }

    /**
     * @author MoePus
     * @reason Complete pending-task barriers through YA's scheduler instead of vanilla lightTasks.
     */
    @Overwrite
    public CompletableFuture<?> waitForPendingTasks(int x, int z) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        var mainThreadExecutor = ((ChunkMapLightAccessor)this.chunkMap).byepregen$getMainThreadExecutor();
        this.byepregen$enqueueTask(
                ChunkPos.pack(x, z),
                ThreadedLevelLightEngine.TaskType.POST_UPDATE,
                () -> mainThreadExecutor.execute(() -> future.complete(null)),
                false
        );
        return future;
    }

}
