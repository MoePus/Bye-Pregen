package com.moepus.byepregen.yalight.engine;

import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import com.moepus.byepregen.yalight.storage.YALightStorage;
import com.moepus.byepregen.yalight.storage.YANibbleArray;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

final class YAChunkEdgeChecker {
    private static final int SECTION_SIZE = 16;
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private final YALightLayerEngine engine;
    private final YALightStorage storage;
    private final YAChunkLightData centerData;
    private final ChunkPos centerPos;
    private YAChunkLightData neighborData;
    private int neighborStepX;
    private int neighborStepZ;
    private int edgeStartX;
    private int edgeStartZ;
    private int edgeStepX;
    private int edgeStepZ;

    private YAChunkEdgeChecker(YALightLayerEngine engine, ChunkAccess center) {
        this.engine = engine;
        this.storage = engine.storage();
        this.centerData = this.storage.existingData(center);
        this.centerPos = center.getPos();
    }

    static void check(YALightLayerEngine engine, ChunkAccess center) {
        if (center == null || !engine.storage().lightEnabled(center)) {
            return;
        }
        new YAChunkEdgeChecker(engine, center).checkLoadedNeighbors();
    }

    private void checkLoadedNeighbors() {
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            this.checkDirection(direction);
        }
        this.engine.clearRunCache();
    }

    private void checkDirection(Direction direction) {
        this.neighborStepX = direction.getStepX();
        this.neighborStepZ = direction.getStepZ();
        ChunkAccess neighbor = this.storage.chunkAccess(
                this.centerPos.x() + this.neighborStepX,
                this.centerPos.z() + this.neighborStepZ);
        if (!this.storage.lightEnabled(neighbor)) {
            return;
        }
        this.neighborData = this.storage.existingData(neighbor);
        if (this.neighborData == null || !this.neighborData.edgeCheckReady()) {
            return;
        }
        this.prepareEdge(direction);
        for (int sectionY = this.storage.maxLightSection();
             sectionY >= this.storage.minLightSection();
             --sectionY) {
            if (this.hasLayer(sectionY)) {
                this.checkSection(sectionY);
            }
        }
    }

    private void prepareEdge(Direction direction) {
        this.edgeStartX = this.centerPos.getMinBlockX();
        this.edgeStartZ = this.centerPos.getMinBlockZ();
        if (direction == Direction.EAST) {
            this.edgeStartX = this.centerPos.getMaxBlockX();
        } else if (direction == Direction.SOUTH) {
            this.edgeStartZ = this.centerPos.getMaxBlockZ();
        }
        boolean xEdge = direction.getAxis() == Direction.Axis.X;
        this.edgeStepX = xEdge ? 0 : 1;
        this.edgeStepZ = xEdge ? 1 : 0;
    }

    private boolean hasLayer(int sectionY) {
        return hasLayer(this.centerData, sectionY) || hasLayer(this.neighborData, sectionY);
    }

    private static boolean hasLayer(YAChunkLightData data, int sectionY) {
        if (data == null) {
            return false;
        }
        YANibbleArray nibble = data.getUpdatingSectionByIndex(sectionY - data.minLightSection());
        return nibble != null && !nibble.isNullUpdating();
    }

    private void checkSection(int sectionY) {
        int minY = sectionY << 4;
        for (int localY = 0; localY < SECTION_SIZE; ++localY) {
            int x = this.edgeStartX;
            int z = this.edgeStartZ;
            for (int along = 0; along < SECTION_SIZE; ++along) {
                this.checkPosition(x, minY + localY, z);
                x += this.edgeStepX;
                z += this.edgeStepZ;
            }
        }
    }

    private void checkPosition(int x, int y, int z) {
        long centerBlock = BlockPos.asLong(x, y, z);
        long neighborBlock = BlockPos.asLong(x + this.neighborStepX, y, z + this.neighborStepZ);
        this.queueIfInconsistent(centerBlock);
        this.queueIfInconsistent(neighborBlock);
    }

    private void queueIfInconsistent(long pos) {
        int current = this.engine.getEnabledCachedUpdatingLight(
                BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
        if (current >= 0 && this.engine.calculateLightValue(pos, current) != current) {
            this.engine.lightQueue().queueCheck(pos);
        }
    }
}
