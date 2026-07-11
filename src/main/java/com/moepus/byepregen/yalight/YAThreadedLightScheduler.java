package com.moepus.byepregen.yalight;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ThreadedLevelLightEngine;

public final class YAThreadedLightScheduler {
    // Fixed so one drain fits YASourceHalo's preallocated 8 * 3 * 3 entries.
    public static final int BATCH_SIZE = 8;
    private static final int INITIAL_TASK_CAPACITY = 16;
    private static final int MAX_RETAINED_TASKS = 256;
    private static final int MAX_POOLED_TASKS = 64;

    private final Long2ObjectLinkedOpenHashMap<ChunkTask> tasks =
            new Long2ObjectLinkedOpenHashMap<>(INITIAL_TASK_CAPACITY);
    private ChunkTask pooledTask;
    private int pooledTaskCount;

    public synchronized void enqueue(
            long chunkKey,
            ThreadedLevelLightEngine.TaskType type,
            Runnable runnable
    ) {
        ChunkTask task = this.task(chunkKey);
        if (type == ThreadedLevelLightEngine.TaskType.PRE_UPDATE) {
            task.preOps.add(runnable);
        } else {
            task.postOps.add(runnable);
        }
    }

    public synchronized void enqueueLightChunk(
            long chunkKey,
            Runnable preOp,
            Runnable postOp
    ) {
        ChunkTask task = this.task(chunkKey);
        task.preOps.add(preOp);
        task.postOps.add(postOp);
    }

    public synchronized boolean hasWork() {
        return !this.tasks.isEmpty();
    }

    public int drain(YALightEngine engine, int maxTasks) {
        ChunkTask firstTask = this.pollBatch(Math.max(1, maxTasks));
        if (firstTask == null) {
            return engine.runLightUpdates();
        }
        try {
            for (ChunkTask task = firstTask; task != null; task = task.nextPooled) {
                task.runPreOps();
            }
            int work = engine.runLightUpdates();
            for (ChunkTask task = firstTask; task != null; task = task.nextPooled) {
                task.runPostOps();
            }
            return work;
        } finally {
            this.recycleBatch(firstTask);
        }
    }

    private synchronized ChunkTask pollBatch(int maxTasks) {
        ChunkTask first = null;
        ChunkTask last = null;
        for (int i = 0; i < maxTasks && !this.tasks.isEmpty(); ++i) {
            ChunkTask task = this.tasks.removeFirst();
            if (first == null) {
                first = task;
            } else {
                last.nextPooled = task;
            }
            last = task;
        }
        if (last != null) {
            last.nextPooled = null;
        }
        if (this.tasks.isEmpty()) {
            this.tasks.trim(MAX_RETAINED_TASKS);
        }
        return first;
    }

    private synchronized void recycleBatch(ChunkTask first) {
        ChunkTask task = first;
        while (task != null) {
            ChunkTask next = task.nextPooled;
            if (this.pooledTaskCount < MAX_POOLED_TASKS) {
                task.clearForReuse();
                task.nextPooled = this.pooledTask;
                this.pooledTask = task;
                ++this.pooledTaskCount;
            }
            task = next;
        }
    }

    private ChunkTask task(long chunkKey) {
        ChunkTask task = this.tasks.get(chunkKey);
        if (task != null) {
            return task;
        }
        task = this.pooledTask;
        if (task == null) {
            task = new ChunkTask();
        } else {
            this.pooledTask = task.nextPooled;
            task.nextPooled = null;
            --this.pooledTaskCount;
        }
        this.tasks.put(chunkKey, task);
        return task;
    }

    private static final class ChunkTask {
        private static final int INITIAL_OPERATION_CAPACITY = 4;
        private static final int MAX_RETAINED_OPERATIONS = 16;

        private final ObjectArrayList<Runnable> preOps =
                new ObjectArrayList<>(INITIAL_OPERATION_CAPACITY);
        private final ObjectArrayList<Runnable> postOps =
                new ObjectArrayList<>(INITIAL_OPERATION_CAPACITY);
        private ChunkTask nextPooled;

        private void runPreOps() {
            for (Runnable op : this.preOps) {
                op.run();
            }
        }

        private void runPostOps() {
            for (Runnable op : this.postOps) {
                op.run();
            }
        }

        private void clearForReuse() {
            this.preOps.clear();
            this.postOps.clear();
            this.preOps.trim(MAX_RETAINED_OPERATIONS);
            this.postOps.trim(MAX_RETAINED_OPERATIONS);
        }
    }
}
