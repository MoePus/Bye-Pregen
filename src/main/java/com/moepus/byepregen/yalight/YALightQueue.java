package com.moepus.byepregen.yalight;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

final class YALightQueue {
    // Coalesce source propagation and block checks by chunk so warm caches stay useful for the whole task.
    private final Long2ObjectLinkedOpenHashMap<ChunkTask> tasks = new Long2ObjectLinkedOpenHashMap<>();
    // ChunkTasks are drained fully every run and never escape, so a simple intrusive free-list is enough.
    private ChunkTask pooledTask;

    void queueCheck(BlockPos pos) {
        this.task(ChunkPos.asLong(pos)).addCheck(pos);
    }

    void queueSource(ChunkPos pos) {
        this.task(pos.toLong()).source = true;
    }

    void queueSourceSection(SectionPos pos) {
        this.task(ChunkPos.asLong(pos.x(), pos.z())).queueSourceSection(pos.y());
    }

    void removeChunk(ChunkPos pos) {
        ChunkTask task = this.tasks.remove(pos.toLong());
        if (task != null) {
            this.recycle(task);
        }
    }

    boolean isEmpty() {
        return this.tasks.isEmpty();
    }

    void enableSourceChunks(YALightStorage storage) {
        for (ChunkTask task : this.tasks.values()) {
            if (task.source || task.hasSourceSections()) {
                int chunkX = task.chunkX();
                int chunkZ = task.chunkZ();
                // Source propagation can leave a chunk and re-enter it through a neighbor.
                // Pre-enabling a one-chunk halo keeps correctness independent of task order.
                for (int dz = -1; dz <= 1; ++dz) {
                    for (int dx = -1; dx <= 1; ++dx) {
                        storage.setLightEnabled(chunkX + dx, chunkZ + dz, true);
                    }
                }
            }
        }
    }

    ChunkTask poll() {
        return this.tasks.isEmpty() ? null : this.tasks.removeFirst();
    }

    void recycle(ChunkTask task) {
        task.nextPooled = this.pooledTask;
        this.pooledTask = task;
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
            }
            task.reset(chunkKey);
            this.tasks.put(chunkKey, task);
        }
        return task;
    }

    static final class ChunkTask {
        private static final int LOCAL_BITS = 4;
        private static final int LOCAL_MASK = (1 << LOCAL_BITS) - 1;
        private static final int Z_SHIFT = LOCAL_BITS;
        private static final int Y_SHIFT = LOCAL_BITS * 2;
        private static final int Y_MASK = (1 << 24) - 1;

        final YAIntQueue checks = new YAIntQueue(16);
        long chunkKey;
        int maxSourceSection;
        int sourceSectionCount;
        boolean source;
        ChunkTask nextPooled;

        void reset(long chunkKey) {
            this.chunkKey = chunkKey;
            this.source = false;
            this.maxSourceSection = Integer.MIN_VALUE;
            this.sourceSectionCount = 0;
            this.clearChecks();
        }

        void addCheck(BlockPos pos) {
            this.checks.add(packCheck(pos));
        }

        long pollCheckPos() {
            return unpackCheck(this.chunkKey, this.checks.poll());
        }

        void queueSourceSection(int sectionY) {
            this.maxSourceSection = Math.max(this.maxSourceSection, sectionY);
            ++this.sourceSectionCount;
        }

        boolean hasSourceSections() {
            return this.sourceSectionCount != 0;
        }

        int chunkX() {
            return ChunkPos.getX(this.chunkKey);
        }

        int chunkZ() {
            return ChunkPos.getZ(this.chunkKey);
        }

        private void clearChecks() {
            this.checks.clear();
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
