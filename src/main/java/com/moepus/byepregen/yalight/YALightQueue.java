package com.moepus.byepregen.yalight;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public final class YALightQueue {
    private static final int INITIAL_TASK_CAPACITY = 16;
    private static final int MAX_RETAINED_TASKS = 256;
    private static final int MAX_POOLED_TASKS = 256;

    // Coalesce source propagation and block checks by chunk so warm caches stay useful for the whole task.
    private final Long2ObjectOpenHashMap<ChunkTask> tasks =
            new Long2ObjectOpenHashMap<>(INITIAL_TASK_CAPACITY);
    private ChunkTask firstTask;
    private ChunkTask lastTask;
    // ChunkTasks are drained fully every run and never escape, so a simple intrusive free-list is enough.
    private ChunkTask pooledTask;
    private int pooledTaskCount;

    void queueCheck(BlockPos pos) {
        this.task(ChunkPos.asLong(pos)).addCheck(pos);
    }

    void queueSource(ChunkPos pos, boolean fresh) {
        ChunkTask task = this.task(pos.toLong());
        if (fresh) {
            task.freshSource = true;
        } else {
            task.normalSource = true;
        }
    }

    void removeChunk(ChunkPos pos) {
        ChunkTask task = this.tasks.remove(pos.toLong());
        if (task != null) {
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

    void collectSourceHalo(YASourceHalo halo, byte layerMask) {
        for (ChunkTask task = this.firstTask; task != null; task = task.nextQueued) {
            if (task.hasSource()) {
                int chunkX = task.chunkX();
                int chunkZ = task.chunkZ();
                // Source propagation can leave a chunk and re-enter it through a neighbor.
                // Pre-enabling a one-chunk halo keeps correctness independent of task order.
                for (int dz = -1; dz <= 1; ++dz) {
                    for (int dx = -1; dx <= 1; ++dx) {
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
        if (this.pooledTaskCount >= MAX_POOLED_TASKS) {
            return;
        }
        task.clearForReuse();
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
        long chunkKey;
        boolean freshSource;
        boolean normalSource;
        ChunkTask previousQueued;
        ChunkTask nextQueued;
        ChunkTask nextPooled;

        void reset(long chunkKey) {
            this.chunkKey = chunkKey;
            this.freshSource = false;
            this.normalSource = false;
        }

        void addCheck(BlockPos pos) {
            int packed = packCheck(pos);
            if (this.queuedChecks.add(packed)) {
                this.checks.add(packed);
            }
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
            return this.freshSource || this.normalSource;
        }

        private void clearForReuse() {
            this.checks.clear();
            this.queuedChecks.clear();
            this.queuedChecks.trim(MAX_RETAINED_CHECKS);
        }

        private static int packCheck(BlockPos pos) {
            return ((pos.getY() & Y_MASK) << Y_SHIFT)
                    | ((pos.getZ() & LOCAL_MASK) << Z_SHIFT)
                    | (pos.getX() & LOCAL_MASK);
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
