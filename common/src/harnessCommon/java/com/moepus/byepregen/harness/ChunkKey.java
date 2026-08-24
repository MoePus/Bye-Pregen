package com.moepus.byepregen.harness;

public record ChunkKey(int x, int z) implements Comparable<ChunkKey> {
    @Override
    public int compareTo(ChunkKey other) {
        int zOrder = Integer.compare(this.z, other.z);
        return zOrder != 0 ? zOrder : Integer.compare(this.x, other.x);
    }

    @Override
    public String toString() {
        return "(" + this.x + "," + this.z + ")";
    }
}
