package com.moepus.byepregen.worldgen.feature;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.BitSet;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;

final class KnownFalseDiskPredicateCache {
    private final Vec3i[] dependencies;
    private final int minimumY;
    private final int maximumY;
    private final Long2ObjectOpenHashMap<BitSet> columns = new Long2ObjectOpenHashMap<>();

    KnownFalseDiskPredicateCache(Vec3i[] dependencies, int minimumY, int maximumY) {
        this.dependencies = dependencies.clone();
        this.minimumY = minimumY;
        this.maximumY = maximumY;
    }

    boolean contains(int x, int y, int z) {
        BitSet column = this.columns.get(ChunkPos.pack(x, z));
        return column != null && this.contains(column, y);
    }

    void add(int x, int y, int z) {
        this.add(this.selectColumn(x, z), y);
    }

    BitSet selectColumn(int x, int z) {
        return this.columns.computeIfAbsent(ChunkPos.pack(x, z), ignored -> new BitSet());
    }

    boolean contains(BitSet column, int y) {
        int bit = this.bitIndex(y);
        return bit >= 0 && column.get(bit);
    }

    void add(BitSet column, int y) {
        int bit = this.bitIndex(y);
        if (bit >= 0) {
            column.set(bit);
        }
    }

    void invalidate(int changedX, int changedY, int changedZ) {
        for (Vec3i dependency : this.dependencies) {
            int x = changedX - dependency.getX();
            int y = changedY - dependency.getY();
            int z = changedZ - dependency.getZ();
            BitSet column = this.columns.get(ChunkPos.pack(x, z));
            int bit = this.bitIndex(y);
            if (column != null && bit >= 0) {
                column.clear(bit);
            }
        }
    }

    void clear() {
        this.columns.clear();
    }

    private int bitIndex(int y) {
        return y < this.minimumY || y >= this.maximumY ? -1 : y - this.minimumY;
    }
}
