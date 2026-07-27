package com.moepus.byepregen.yalight;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class YALightMath {
    private static final int DIRECTION_COUNT = 6;
    static final int ALL_DIRECTIONS = (1 << DIRECTION_COUNT) - 1;

    // Queue metadata keeps the hot fields in low bits and leaves high bits for control flags.
    // RECHECK drops stale re-propagation work; WRITE_LEVEL lets source re-additions publish first.
    static final long FLAG_RECHECK = 1L << 62;
    static final long FLAG_WRITE_LEVEL = 1L << 61;
    static final long FLAG_FRESH_OWNER_TRANSFER = 1L << 58;

    private static final int LEVEL_SHIFT = 0;
    private static final int DIRECTIONS_SHIFT = 4;

    private YALightMath() {
    }

    static long meta(int level, int directions, long flags) {
        return ((long)level & 15L) << LEVEL_SHIFT
                | ((long)directions & 63L) << DIRECTIONS_SHIFT
                | flags;
    }

    static int level(long entry) {
        return (int)(entry >>> LEVEL_SHIFT) & 15;
    }

    static int directions(long entry) {
        return (int)(entry >>> DIRECTIONS_SHIFT) & 63;
    }

    static int withoutOpposite(int directionIndex) {
        return ALL_DIRECTIONS & ~oppositeMask(directionIndex);
    }

    static int only(Direction direction) {
        return 1 << direction.ordinal();
    }

    static int oppositeMask(int directionIndex) {
        return 1 << (directionIndex ^ 1);
    }

    static int stepX(int directionIndex) {
        return switch (directionIndex) {
            case 4 -> -1;
            case 5 -> 1;
            default -> 0;
        };
    }

    static int stepY(int directionIndex) {
        return switch (directionIndex) {
            case 0 -> -1;
            case 1 -> 1;
            default -> 0;
        };
    }

    static int stepZ(int directionIndex) {
        return switch (directionIndex) {
            case 2 -> -1;
            case 3 -> 1;
            default -> 0;
        };
    }

    static boolean isSectionInterior(int x, int y, int z) {
        int localX = x & 15;
        int localY = y & 15;
        int localZ = z & 15;
        return localX > 0 && localX < 15
                && localY > 0 && localY < 15
                && localZ > 0 && localZ < 15;
    }

    static Direction direction(int directionIndex) {
        return switch (directionIndex) {
            case 0 -> Direction.DOWN;
            case 1 -> Direction.UP;
            case 2 -> Direction.NORTH;
            case 3 -> Direction.SOUTH;
            case 4 -> Direction.WEST;
            case 5 -> Direction.EAST;
            default -> throw new IllegalArgumentException();
        };
    }

    static boolean isEmptyShape(BlockState state) {
        return !state.canOcclude() || !state.useShapeForLightOcclusion();
    }
}
