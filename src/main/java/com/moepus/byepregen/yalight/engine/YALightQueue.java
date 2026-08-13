package com.moepus.byepregen.yalight.engine;

import com.moepus.byepregen.yalight.scheduler.YAFreshLightRequest;
import com.moepus.byepregen.yalight.scheduler.YASourceHalo;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;

public final class YALightQueue {
    private static final int INITIAL_TASK_CAPACITY = 16;
    private static final int MAX_RETAINED_TASKS = 256;
    private static final int MAX_POOLED_TASKS = 256;

    private final LightLayer layer;

    // Coalesce source propagation and block checks by chunk so warm caches stay useful for the whole task.
    private final Long2ObjectOpenHashMap<ChunkTask> tasks =
            new Long2ObjectOpenHashMap<>(INITIAL_TASK_CAPACITY);
    private ChunkTask firstTask;
    private ChunkTask lastTask;
    // ChunkTasks are drained fully every run and never escape, so a simple intrusive free-list is enough.
    private ChunkTask pooledTask;
    private int pooledTaskCount;

    public YALightQueue(LightLayer layer) {
        this.layer = layer;
    }

    void queueCheck(BlockPos pos) {
        this.task(ChunkPos.asLong(pos)).addCheck(pos);
    }

    void queueCheck(long pos) {
        this.task(ChunkPos.asLong(
                SectionPos.blockToSectionCoord(BlockPos.getX(pos)),
                SectionPos.blockToSectionCoord(BlockPos.getZ(pos)))).addCheck(pos);
    }

    void queueSource(ChunkPos pos) {
        this.task(pos.toLong()).normalSource = true;
    }

    void removeChunk(ChunkPos pos) {
        ChunkTask task = this.tasks.remove(pos.toLong());
        if (task != null) {
            task.cancelFreshSources(this.layer);
            this.unlink(task);
            this.recycle(task);
            if (this.tasks.isEmpty()) {
                this.tasks.trim(MAX_RETAINED_TASKS);
            }
        }
    }

    boolean isEmpty() {
        return this.tasks.isEmpty();
    }

    void queueFreshSource(YAFreshLightRequest request) {
        this.task(request.owner().getPos().toLong()).addFreshOwner(request, this.layer);
    }

    void collectSourceHalo(YASourceHalo halo, byte layerMask) {
        for (ChunkTask task = this.firstTask; task != null; task = task.nextQueued) {
            if (task.hasFreshOwnerConflict() && !task.hasPositionSource()) {
                continue;
            }
            if (task.hasSource()) {
                int chunkX = task.chunkX();
                int chunkZ = task.chunkZ();
                // Source propagation can leave a chunk and re-enter it through a neighbor.
                // Pre-enabling a one-chunk halo keeps correctness independent of task order.
                for (int dz = -1; dz <= 1; ++dz) {
                    for (int dx = -1; dx <= 1; ++dx) {
                        if (dx == 0 && dz == 0 && !task.hasPositionWork()) {
                            continue;
                        }
                        halo.add(ChunkPos.asLong(chunkX + dx, chunkZ + dz), layerMask);
                    }
                }
            }
        }
    }

    ChunkTask poll() {
        ChunkTask task = this.firstTask;
        if (task == null) {
            return null;
        }
        this.unlink(task);
        this.tasks.remove(task.chunkKey);
        if (this.tasks.isEmpty()) {
            this.tasks.trim(MAX_RETAINED_TASKS);
        }
        return task;
    }

    void recycle(ChunkTask task) {
        task.clearForReuse(this.layer);
        if (this.pooledTaskCount >= MAX_POOLED_TASKS) {
            return;
        }
        task.nextPooled = this.pooledTask;
        this.pooledTask = task;
        ++this.pooledTaskCount;
    }

    private ChunkTask task(long chunkKey) {
        ChunkTask task = this.tasks.get(chunkKey);
        if (task == null) {
            task = this.pooledTask;
            if (task == null) {
                task = new ChunkTask();
            } else {
                this.pooledTask = task.nextPooled;
                task.nextPooled = null;
                --this.pooledTaskCount;
            }
            task.reset(chunkKey);
            this.append(task);
            this.tasks.put(chunkKey, task);
        }
        return task;
    }

    private void append(ChunkTask task) {
        task.previousQueued = this.lastTask;
        if (this.lastTask == null) {
            this.firstTask = task;
        } else {
            this.lastTask.nextQueued = task;
        }
        this.lastTask = task;
    }

    private void unlink(ChunkTask task) {
        if (task.previousQueued == null) {
            this.firstTask = task.nextQueued;
        } else {
            task.previousQueued.nextQueued = task.nextQueued;
        }
        if (task.nextQueued == null) {
            this.lastTask = task.previousQueued;
        } else {
            task.nextQueued.previousQueued = task.previousQueued;
        }
        task.previousQueued = null;
        task.nextQueued = null;
    }

    static final class ChunkTask {
        private static final int LOCAL_BITS = 4;
        private static final int LOCAL_MASK = (1 << LOCAL_BITS) - 1;
        private static final int Z_SHIFT = LOCAL_BITS;
        private static final int Y_SHIFT = LOCAL_BITS * 2;
        private static final int Y_MASK = (1 << 24) - 1;
        private static final int INITIAL_CHECK_CAPACITY = 16;
        private static final int MAX_RETAINED_CHECKS = 256;

        final YAIntQueue checks = new YAIntQueue(INITIAL_CHECK_CAPACITY, MAX_RETAINED_CHECKS);
        // Vanilla dedupes repeated checkBlock calls through a LongOpenHashSet; without this a
        // batch touching the same position twice would repeat the whole column update.
        private final IntOpenHashSet queuedChecks = new IntOpenHashSet(INITIAL_CHECK_CAPACITY);
        private YAFreshLightRequest firstFreshOwner;
        private boolean freshOwnerConflict;
        long chunkKey;
        boolean normalSource;
        ChunkTask previousQueued;
        ChunkTask nextQueued;
        ChunkTask nextPooled;

        void reset(long chunkKey) {
            this.chunkKey = chunkKey;
            this.normalSource = false;
            this.freshOwnerConflict = false;
        }

        void addCheck(BlockPos pos) {
            this.addPackedCheck(packCheck(pos.getX(), pos.getY(), pos.getZ()));
        }

        void addCheck(long pos) {
            this.addPackedCheck(packCheck(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos)));
        }

        private void addPackedCheck(int packed) {
            if (this.queuedChecks.add(packed)) {
                this.checks.add(packed);
            }
        }

        void addFreshOwner(YAFreshLightRequest request, LightLayer layer) {
            if (this.firstFreshOwner != null && !YAFreshLightRequest.sameOwner(
                    this.firstFreshOwner.owner(), request.owner())) {
                this.freshOwnerConflict = true;
            }
            request.setNextQueued(layer, this.firstFreshOwner);
            this.firstFreshOwner = request;
        }

        long pollCheckPos() {
            return unpackCheck(this.chunkKey, this.checks.poll());
        }

        int chunkX() {
            return ChunkPos.getX(this.chunkKey);
        }

        int chunkZ() {
            return ChunkPos.getZ(this.chunkKey);
        }

        boolean hasSource() {
            return this.firstFreshOwner != null || this.hasPositionSource();
        }

        boolean hasPositionSource() {
            return this.normalSource;
        }

        boolean hasPositionWork() {
            return this.normalSource || !this.checks.isEmpty();
        }

        boolean hasFreshOwnerConflict() {
            return this.freshOwnerConflict;
        }

        void failFreshSources(LightLayer layer) {
            for (YAFreshLightRequest request = this.firstFreshOwner;
                    request != null;
                    request = request.nextQueued(layer)) {
                request.markFailed();
            }
        }

        YAFreshLightRequest firstFreshOwner() {
            return this.firstFreshOwner;
        }

        private void clearForReuse(LightLayer layer) {
            YAFreshLightRequest request = this.firstFreshOwner;
            while (request != null) {
                YAFreshLightRequest next = request.nextQueued(layer);
                request.setNextQueued(layer, null);
                request = next;
            }
            this.firstFreshOwner = null;
            this.freshOwnerConflict = false;
            this.checks.clear();
            this.queuedChecks.clear();
            this.queuedChecks.trim(MAX_RETAINED_CHECKS);
        }

        private void cancelFreshSources(LightLayer layer) {
            for (YAFreshLightRequest request = this.firstFreshOwner;
                    request != null;
                    request = request.nextQueued(layer)) {
                request.cancel();
            }
        }

        private static int packCheck(int x, int y, int z) {
            return ((y & Y_MASK) << Y_SHIFT)
                    | ((z & LOCAL_MASK) << Z_SHIFT)
                    | (x & LOCAL_MASK);
        }

        private static long unpackCheck(long chunkKey, int packed) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            int x = (chunkX << LOCAL_BITS) | (packed & LOCAL_MASK);
            int y = packed >> Y_SHIFT;
            int z = (chunkZ << LOCAL_BITS) | ((packed >>> Z_SHIFT) & LOCAL_MASK);
            return BlockPos.asLong(x, y, z);
        }
    }
}
