package com.moepus.byepregen.harness;

public record ChunkBounds(int minX, int maxX, int minZ, int maxZ) {
    public ChunkBounds {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Invalid chunk bounds: " + display(minX, maxX, minZ, maxZ));
        }
    }

    public static ChunkBounds fromSystemProperties(String prefix) {
        return new ChunkBounds(
                HarnessProperties.getInt(prefix + ".minChunkX", Integer.MIN_VALUE),
                HarnessProperties.getInt(prefix + ".maxChunkX", Integer.MAX_VALUE),
                HarnessProperties.getInt(prefix + ".minChunkZ", Integer.MIN_VALUE),
                HarnessProperties.getInt(prefix + ".maxChunkZ", Integer.MAX_VALUE)
        );
    }

    public boolean contains(int x, int z) {
        return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
    }

    public boolean isLimited() {
        return this.minX != Integer.MIN_VALUE
                || this.maxX != Integer.MAX_VALUE
                || this.minZ != Integer.MIN_VALUE
                || this.maxZ != Integer.MAX_VALUE;
    }

    public boolean isFullyBounded() {
        return this.minX != Integer.MIN_VALUE
                && this.maxX != Integer.MAX_VALUE
                && this.minZ != Integer.MIN_VALUE
                && this.maxZ != Integer.MAX_VALUE;
    }

    public long expectedChunks() {
        if (!this.isFullyBounded()) {
            throw new IllegalStateException("Complete chunk coverage requires finite bounds");
        }
        long width = (long)this.maxX - this.minX + 1L;
        long depth = (long)this.maxZ - this.minZ + 1L;
        return Math.multiplyExact(width, depth);
    }

    public String display() {
        return display(this.minX, this.maxX, this.minZ, this.maxZ);
    }

    private static String display(int minX, int maxX, int minZ, int maxZ) {
        return "x=" + minX + ".." + maxX + ", z=" + minZ + ".." + maxZ;
    }
}
