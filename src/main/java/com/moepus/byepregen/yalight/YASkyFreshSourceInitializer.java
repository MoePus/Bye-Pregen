package com.moepus.byepregen.yalight;

import net.minecraft.core.Direction;

final class YASkyFreshSourceInitializer {
    private static final int DOWN = YALightMath.only(Direction.DOWN);
    private static final int NORTH = YALightMath.only(Direction.NORTH);
    private static final int SOUTH = YALightMath.only(Direction.SOUTH);
    private static final int WEST = YALightMath.only(Direction.WEST);
    private static final int EAST = YALightMath.only(Direction.EAST);

    private final YASkyLightEngine engine;
    private final YASkySourcePropagator sources;
    private final int sourceMinY;

    YASkyFreshSourceInitializer(YASkyLightEngine engine, YASkySourcePropagator sources, int sourceMinY) {
        this.engine = engine;
        this.sources = sources;
        this.sourceMinY = sourceMinY;
    }

    void initializePartialSection(YAChunkLightData data, int sectionY) {
        YANibbleArray nibble = this.engine.storage.getOrCreateSection(data, sectionY);
        if (nibble == null) {
            return;
        }
        int bottom = sectionY << 4;
        int top = bottom | 15;
        for (int dz = 0; dz < 16; ++dz) {
            for (int dx = 0; dx < 16; ++dx) {
                this.fillColumn(nibble, dx, dz, bottom, top);
            }
        }
        this.engine.storage.markDirtySection(data, sectionY);
    }

    void emitFrontier(int chunkX, int chunkZ, int minY, int maxY, long flags) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (int dz = 0; dz < 16; ++dz) {
            for (int dx = 0; dx < 16; ++dx) {
                this.emitColumnFrontier(minX + dx, minZ + dz, dx, dz, minY, maxY, flags);
            }
        }
    }

    private void fillColumn(YANibbleArray nibble, int localX, int localZ, int bottom, int top) {
        int yStart = this.sourceCodeRunStart(this.sources.sourceCode(localX, localZ), bottom);
        if (yStart > top) {
            return;
        }
        nibble.fillColumnRun(localX, localZ, yStart & 15, top & 15, 15);
    }

    private void emitColumnFrontier(
            int x,
            int z,
            int localX,
            int localZ,
            int minY,
            int maxY,
            long flags
    ) {
        int lowest = this.sources.sourceCode(localX, localZ);
        if (lowest == YAChunkSkyLightSources.NO_SOURCE_CODE) {
            return;
        }
        int north = this.sources.sourceCode(localX, localZ - 1);
        int south = this.sources.sourceCode(localX, localZ + 1);
        int west = this.sources.sourceCode(localX - 1, localZ);
        int east = this.sources.sourceCode(localX + 1, localZ);
        int startY = Math.max(minY, this.sourceY(lowest, minY));
        int endY = Math.max(startY, this.horizontalEndY(north, south, west, east, maxY));
        for (int y = startY; y <= Math.min(endY, maxY); ++y) {
            int yCode = this.sources.sourceYCode(y);
            int directions = this.horizontalDirections(yCode, north, south, west, east);
            if (y == startY) {
                directions |= DOWN;
            }
            this.emitEdges(x, y, z, directions, flags);
        }
    }

    private void emitEdges(int x, int y, int z, int directions, long flags) {
        int fromBlock = Integer.MIN_VALUE;
        while (directions != 0) {
            int directionIndex = Integer.numberOfTrailingZeros(directions);
            directions &= directions - 1;
            fromBlock = this.engine.tryPropagateIncreaseEdge(
                    x, y, z, 15, directionIndex, fromBlock, flags);
        }
    }

    private int horizontalDirections(int yCode, int north, int south, int west, int east) {
        int directions = 0;
        directions |= yCode < north ? NORTH : 0;
        directions |= yCode < south ? SOUTH : 0;
        directions |= yCode < west ? WEST : 0;
        directions |= yCode < east ? EAST : 0;
        return directions;
    }

    private int horizontalEndY(int north, int south, int west, int east, int maxY) {
        int maxCode = Math.max(Math.max(north, south), Math.max(west, east));
        return maxCode == YAChunkSkyLightSources.NO_SOURCE_CODE
                ? maxY
                : this.sourceMinY + maxCode - 1;
    }

    private int sourceY(int code, int minY) {
        return code == YAChunkSkyLightSources.NEGATIVE_INFINITY_CODE ? minY : this.sourceMinY + code;
    }

    private int sourceCodeRunStart(int code, int bottom) {
        return code <= this.sources.sourceYCode(bottom) ? bottom : this.sourceMinY + code;
    }
}
