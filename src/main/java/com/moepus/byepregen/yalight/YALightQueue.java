package com.moepus.byepregen.yalight;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;

final class YALightQueue {
    // Coalesce source propagation and block checks by chunk so warm caches stay useful for the whole task.
    private final Long2ObjectLinkedOpenHashMap<ChunkTask> tasks = new Long2ObjectLinkedOpenHashMap<>();
    // ChunkTasks are drained fully every run and never escape, so we recycle them and their warm
    // check buffers through a free-list instead of allocating per touched chunk.
    private final ArrayDeque<ChunkTask> pool = new ArrayDeque<>();

    void queueCheck(BlockPos pos) {
        this.task(ChunkPos.asLong(pos)).checks.add(pos.asLong());
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
            this.pool.addFirst(task);
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
        this.pool.addFirst(task);
    }

    private ChunkTask task(long chunkKey) {
        ChunkTask task = this.tasks.get(chunkKey);
        if (task == null) {
            task = this.pool.pollFirst();
            if (task == null) {
                task = new ChunkTask();
            }
            task.reset(chunkKey);
            this.tasks.put(chunkKey, task);
        }
        return task;
    }

    static final class ChunkTask {
        final YALongQueue checks = new YALongQueue(4);
        long chunkKey;
        int maxSourceSection;
        int sourceSectionCount;
        boolean source;

        void reset(long chunkKey) {
            this.chunkKey = chunkKey;
            this.source = false;
            this.maxSourceSection = Integer.MIN_VALUE;
            this.sourceSectionCount = 0;
            this.checks.clear();
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
    }
}
