package com.moepus.byepregen.worldgen.arena;

import com.moepus.byepregen.palette.arena.Layout;

/** Shared rounding order for scalar Arena stepping and compiled column inputs. */
public final class ArenaInterpolationMath {
    private static final double INVERSE_PAGE_HEIGHT = 1.0D / Layout.PAGE_HEIGHT;

    private ArenaInterpolationMath() {
    }

    public static double pageLower(double cellLower, double cellDifference, double fraction) {
        return fraction == 0.0D ? cellLower : cellLower + fraction * cellDifference;
    }

    public static double pageLower(double cellLower, double cellDifference, int pageStart, int cellHeight) {
        return pageStart == 0 ? cellLower : cellLower + (double) pageStart / cellHeight * cellDifference;
    }

    public static double pageStep(double pageLower, double pageUpper) {
        return (pageLower - pageUpper) * INVERSE_PAGE_HEIGHT;
    }

    public static double pageValue(double lower, double upper, double step, int offset) {
        return switch (offset) {
            case 0 -> lower;
            case 1 -> lower - step;
            case 2 -> (upper + step) + step;
            case 3 -> upper + step;
            default -> throw new IndexOutOfBoundsException("Arena page offset: " + offset);
        };
    }
}
