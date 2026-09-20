package com.moepus.byepregen.yalight.engine;

final class YABlockLightPropagation {
    private YABlockLightPropagation() {
    }

    static void increase(YABlockLightEngine engine, long pos, long meta) {
        int level = YALightMath.level(meta);
        int index = YALightMath.localIndex(pos);
        YASectionAccess source = engine.runCache.prepareSource(engine.storage, pos);
        int stored = source.light(index);
        if (stored != level) {
            if (!YALightMath.canWrite(meta, stored, level)) {
                return;
            }
            source.setLight(engine.runCache, engine.storage, index, level);
        }
        if (YALightMath.isSectionInterior(index)) {
            if (source.enabled()) {
                increaseInterior(engine, source, pos, index, level, YALightMath.directions(meta));
            }
        } else {
            increaseBoundary(engine, source, pos, index, level, YALightMath.directions(meta));
        }
    }

    private static void increaseInterior(YABlockLightEngine engine, YASectionAccess source,
                                         long pos, int index, int level, int directions) {
        int fromBlock = YALightBlockAccess.UNRESOLVED_BLOCK;
        while (directions != 0) {
            int direction = Integer.numberOfTrailingZeros(directions);
            directions &= directions - 1;
            fromBlock = increaseEdge(engine, source, source, pos,
                    index + YALightMath.localStep(direction), level, direction, fromBlock);
        }
    }

    private static void increaseBoundary(YABlockLightEngine engine, YASectionAccess source,
                                         long pos, int index, int level, int directions) {
        int fromBlock = YALightBlockAccess.UNRESOLVED_BLOCK;
        while (directions != 0) {
            int direction = Integer.numberOfTrailingZeros(directions);
            directions &= directions - 1;
            int edge = YALightMath.neighbor(index, direction);
            YASectionAccess target = neighbor(engine, source, pos, direction, YALightMath.sectionCarry(edge));
            if (target.enabled()) {
                fromBlock = increaseEdge(engine, source, target, pos,
                        YALightMath.neighborIndex(edge), level, direction, fromBlock);
            }
        }
    }

    private static int increaseEdge(YABlockLightEngine engine, YASectionAccess source, YASectionAccess target,
                                    long pos, int index, int level, int direction, int fromBlock) {
        int current = target.light(index);
        int nextLevel = level - 1;
        if (current >= nextLevel) {
            return fromBlock;
        }
        YALightBlockAccess blocks = engine.blockAccess();
        int toBlock = blocks.blockAt(target, index);
        if (blocks.isFull(toBlock)) {
            return fromBlock;
        }
        if (blocks.isSlow(toBlock)) {
            nextLevel = blocks.slowAttenuatedLevel(level, toBlock, YALightMath.offset(pos, direction));
            if (nextLevel <= current) {
                return fromBlock;
            }
        }
        if (fromBlock == YALightBlockAccess.UNRESOLVED_BLOCK) {
            fromBlock = blocks.blockAt(source, YALightMath.localIndex(pos));
        }
        if ((fromBlock | toBlock) != 0 && blocks.shapeOccludes(
                pos, fromBlock, YALightMath.offset(pos, direction), toBlock, YALightMath.direction(direction))) {
            return fromBlock;
        }
        target.setLight(engine.runCache, engine.storage, index, nextLevel);
        if (nextLevel > 1) {
            engine.enqueueIncrease(YALightMath.offset(pos, direction), nextLevel,
                    YALightMath.withoutOpposite(direction), 0L);
        }
        return fromBlock;
    }

    static void decrease(YABlockLightEngine engine, long pos, long meta) {
        int directions = YALightMath.directions(meta);
        if (directions == 0) {
            return;
        }
        YASectionAccess source = engine.runCache.prepareSource(engine.storage, pos);
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
            YASectionAccess target = neighbor(engine, source, pos, direction, YALightMath.sectionCarry(edge));
            if (target.enabled()) {
                decreaseEdge(engine, target, pos, YALightMath.neighborIndex(edge), level, direction);
            }
        }
    }

    private static void decreaseInterior(YABlockLightEngine engine, YASectionAccess source,
                                         long pos, int index, int level, int directions) {
        while (directions != 0) {
            int direction = Integer.numberOfTrailingZeros(directions);
            directions &= directions - 1;
            decreaseEdge(engine, source, pos, index + YALightMath.localStep(direction), level, direction);
        }
    }

    private static void decreaseEdge(YABlockLightEngine engine, YASectionAccess target,
                                     long pos, int index, int level, int direction) {
        int current = target.light(index);
        if (current <= 0) {
            return;
        }
        YALightBlockAccess blocks = engine.blockAccess();
        int block = blocks.blockAt(target, index);
        if (blocks.isFull(block)) {
            target.setLight(engine.runCache, engine.storage, index, 0);
            return;
        }
        int remaining = blocks.isSlow(block) ? blocks.slowAttenuatedLevel(level, block, YALightMath.offset(pos, direction)) : level - 1;
        long toPos = YALightMath.offset(pos, direction);
        if (current > remaining) {
            engine.enqueueIncrease(toPos, current, YALightMath.oppositeMask(direction), YALightMath.FLAG_RECHECK);
            return;
        }
        target.setLight(engine.runCache, engine.storage, index, 0);
        if (blocks.isSlow(block)) {
            int emitted = blocks.toState(block).getLightEmission(engine.levelReader, engine.mutablePos.set(toPos));
            if (emitted > 0) {
                engine.enqueueIncrease(toPos, emitted, YALightMath.ALL_DIRECTIONS, YALightMath.FLAG_WRITE_LEVEL);
            }
        }
        engine.enqueueDecrease(toPos, current, YALightMath.withoutOpposite(direction));
    }

    private static YASectionAccess neighbor(YABlockLightEngine engine, YASectionAccess source,
                                            long pos, int direction, int carry) {
        if (carry == 0) {
            return source;
        }
        return direction < 2
                ? engine.runCache.verticalNeighbor(engine.storage, source, carry)
                : engine.runCache.horizontalNeighbor(engine.storage, pos, direction, carry);
    }
}
