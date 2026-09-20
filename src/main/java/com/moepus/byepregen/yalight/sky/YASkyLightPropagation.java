package com.moepus.byepregen.yalight.sky;

import com.moepus.byepregen.yalight.engine.YALightBlockAccess;
import com.moepus.byepregen.yalight.engine.YALightMath;
import com.moepus.byepregen.yalight.engine.YASectionAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

final class YASkyLightPropagation {
    private YASkyLightPropagation() {
    }

    static void propagateIncrease(YASkyLightEngine engine, long pos, long meta) {
        int level = YALightMath.level(meta);
        int index = YALightMath.localIndex(pos);
        YASectionAccess source = engine.runCache().prepareSource(engine.storage, pos);
        int stored = source.light(index);
        if (stored != level) {
            if (!YALightMath.canWrite(meta, stored, level)) {
                return;
            }
            source.setLight(engine.runCache(), engine.storage, index, level);
        }
        int directions = YALightMath.directions(meta);
        if (YALightMath.isSectionInterior(index)) {
            if (source.enabled()) {
                increaseInterior(engine, source, pos, index, level, directions,
                        meta & YALightMath.FLAG_FRESH_OWNER_TRANSFER);
            }
        } else {
            increaseBoundary(engine, source, pos, index, level, directions, meta);
        }
    }

    static void propagateSourceEdges(YASkyLightEngine engine, long pos, int directions, long flags) {
        if (directions == 0) {
            return;
        }
        int index = YALightMath.localIndex(pos);
        if (YALightMath.isSectionInterior(index)) {
            YASectionAccess source = engine.runCache().prepareSource(engine.storage, pos);
            if (source.enabled()) {
                increaseInterior(engine, source, pos, index, 15, directions,
                        flags & YALightMath.FLAG_FRESH_OWNER_TRANSFER);
            }
        } else {
            // An outward edge can transfer without touching either source or target light data.
            increaseBoundary(engine, null, pos, index, 15, directions, flags);
        }
    }

    private static void increaseInterior(YASkyLightEngine engine, YASectionAccess source,
                                         long pos, int index, int level, int directions, long flags) {
        int fromBlock = YALightBlockAccess.UNRESOLVED_BLOCK;
        while (directions != 0) {
            int direction = Integer.numberOfTrailingZeros(directions);
            directions &= directions - 1;
            fromBlock = increaseEdge(engine, source, source, pos,
                    index + YALightMath.localStep(direction), level, direction, fromBlock, flags);
        }
    }

    private static void increaseBoundary(YASkyLightEngine engine, YASectionAccess source,
                                         long pos, int index, int level, int directions, long flags) {
        int fromBlock = YALightBlockAccess.UNRESOLVED_BLOCK;
        boolean transfers = YALightMath.transfersFreshOwner(flags);
        long inheritedFlags = flags & YALightMath.FLAG_FRESH_OWNER_TRANSFER;
        while (directions != 0) {
            int direction = Integer.numberOfTrailingZeros(directions);
            directions &= directions - 1;
            int edge = YALightMath.neighbor(index, direction);
            int carry = YALightMath.sectionCarry(edge);
            boolean horizontalExit = carry != 0 && direction >= 2;
            boolean freshExit = transfers && horizontalExit;
            if (freshExit && tryTransferFreshOwnerEdge(engine, pos, level, direction)) {
                continue;
            }
            YASectionAccess target;
            if (horizontalExit) {
                target = engine.runCache().horizontalNeighbor(engine.storage, pos, direction, carry);
            } else {
                if (source == null) {
                    source = engine.runCache().prepareSource(engine.storage, pos);
                }
                target = carry == 0 ? source : engine.runCache().verticalNeighbor(engine.storage, source, carry);
            }
            if (target.enabled()) {
                fromBlock = increaseEdge(engine, source, target, pos, YALightMath.neighborIndex(edge),
                        level, direction, fromBlock, freshExit ? 0L : inheritedFlags);
            }
        }
    }

    private static int increaseEdge(YASkyLightEngine engine, YASectionAccess source, YASectionAccess target,
                                    long pos, int index, int level, int direction, int fromBlock, long childFlags) {
        int current = target.light(index);
        int nextLevel = level - 1;
        if (current >= nextLevel) {
            return fromBlock;
        }
        int toBlock = engine.blocks.blockAt(target, index);
        if (engine.blocks.isFull(toBlock)) {
            return fromBlock;
        }
        if (engine.blocks.isSlow(toBlock)) {
            nextLevel = engine.blocks.slowAttenuatedLevel(level, toBlock, YALightMath.offset(pos, direction));
            if (nextLevel <= current) {
                return fromBlock;
            }
        }
        if (fromBlock == YALightBlockAccess.UNRESOLVED_BLOCK) {
            if (source == null) {
                source = engine.runCache().prepareSource(engine.storage, pos);
            }
            fromBlock = engine.blocks.blockAt(source, YALightMath.localIndex(pos));
        }
        if ((fromBlock | toBlock) != 0 && engine.blocks.shapeOccludes(
                pos, fromBlock, YALightMath.offset(pos, direction), toBlock, YALightMath.direction(direction))) {
            return fromBlock;
        }
        target.setBoundLight(engine.storage, index, nextLevel);
        if (nextLevel > 1) {
            engine.enqueueIncrease(YALightMath.offset(pos, direction), nextLevel,
                    YALightMath.withoutOpposite(direction), childFlags);
        }
        return fromBlock;
    }

    static void propagateTransferredEdge(YASkyLightEngine engine, long pos, int level, int direction, long flags) {
        int edge = YALightMath.neighbor(YALightMath.localIndex(pos), direction);
        int carry = YALightMath.sectionCarry(edge);
        YASectionAccess source = null;
        YASectionAccess target;
        if (carry != 0 && direction >= 2) {
            target = engine.runCache().horizontalNeighbor(engine.storage, pos, direction, carry);
        } else {
            source = engine.runCache().prepareSource(engine.storage, pos);
            target = carry == 0 ? source : engine.runCache().verticalNeighbor(engine.storage, source, carry);
        }
        if (target.enabled()) {
            // The inbox already chose this owner. Do not transfer this edge back into an inbox.
            increaseEdge(engine, source, target, pos, YALightMath.neighborIndex(edge), level,
                    direction, YALightBlockAccess.UNRESOLVED_BLOCK, flags);
        }
    }

    private static boolean tryTransferFreshOwnerEdge(YASkyLightEngine engine, long pos, int level, int direction) {
        int x = BlockPos.getX(pos) + YALightMath.stepX(direction);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos) + YALightMath.stepZ(direction);
        long targetOwner = ChunkPos.asLong(x >> 4, z >> 4);
        byte state = engine.ownerTransfers.state(targetOwner);
        if (!engine.ownerTransfers.isTransferTarget(state)) {
            return false;
        }
        if (engine.ownerTransfers.isInitialized(state)) {
            int current = engine.getEnabledCachedUpdatingLight(x, y, z);
            if (current < 0 || current >= level - 1) {
                return true;
            }
        }
        engine.ownerTransfers.enqueue(targetOwner, pos, level, direction);
        return true;
    }

    static void propagateDecrease(YASkyLightEngine engine, long pos, long meta) {
        int directions = YALightMath.directions(meta);
        if (directions == 0) {
            return;
        }
        YASectionAccess source = engine.runCache().prepareSource(engine.storage, pos);
        int index = YALightMath.localIndex(pos);
        int level = YALightMath.level(meta);
        if (YALightMath.isSectionInterior(index)) {
            if (source.enabled()) {
                decreaseInterior(engine, source, pos, index, level, directions);
            }
            return;
        }
        while (directions != 0) {
            int direction = Integer.numberOfTrailingZeros(directions);
            directions &= directions - 1;
            int edge = YALightMath.neighbor(index, direction);
            int carry = YALightMath.sectionCarry(edge);
            YASectionAccess target = source;
            if (carry != 0) {
                target = direction < 2
                        ? engine.runCache().verticalNeighbor(engine.storage, source, carry)
                        : engine.runCache().horizontalNeighbor(engine.storage, pos, direction, carry);
            }
            if (target.enabled()) {
                decreaseEdge(engine, target, pos, YALightMath.neighborIndex(edge), level, direction);
            }
        }
    }

    private static void decreaseInterior(YASkyLightEngine engine, YASectionAccess source,
                                         long pos, int index, int level, int directions) {
        while (directions != 0) {
            int direction = Integer.numberOfTrailingZeros(directions);
            directions &= directions - 1;
            decreaseEdge(engine, source, pos, index + YALightMath.localStep(direction), level, direction);
        }
    }

    private static void decreaseEdge(YASkyLightEngine engine, YASectionAccess target,
                                     long pos, int index, int level, int direction) {
        int current = target.light(index);
        if (current <= 0) {
            return;
        }
        int block = engine.blocks.blockAt(target, index);
        if (engine.blocks.isFull(block)) {
            target.clearNonzeroLight(engine.storage, index);
            return;
        }
        int remaining = engine.blocks.isSlow(block) ? engine.blocks.slowAttenuatedLevel(level, block, YALightMath.offset(pos, direction)) : level - 1;
        long toPos = YALightMath.offset(pos, direction);
        if (current > remaining) {
            engine.enqueueIncrease(toPos, current, YALightMath.oppositeMask(direction), YALightMath.FLAG_RECHECK);
            return;
        }
        target.clearNonzeroLight(engine.storage, index);
        int source = engine.getSourceLight(target.owner(), toPos);
        if (source > 0) {
            engine.enqueueIncrease(toPos, source, engine.sources.skySourceDirections(
                    BlockPos.getX(toPos), BlockPos.getY(toPos), BlockPos.getZ(toPos)), YALightMath.FLAG_WRITE_LEVEL);
        }
        engine.enqueueDecrease(toPos, current, YALightMath.withoutOpposite(direction));
    }
}
