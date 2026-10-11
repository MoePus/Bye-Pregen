package com.moepus.byepregen.worldgen.arena;

import com.moepus.byepregen.dfc.runtime.InterpolatedColumn;
import com.moepus.byepregen.palette.arena.Layout;
import java.util.Objects;

/** Expands only requested intervals directly into DFC work arrays; owns no full-height sample array. */
public final class ArenaInterpolationColumn implements InterpolatedColumn {
    private static final int TWO_PAGE_CELL_HEIGHT = Layout.PAGE_HEIGHT * 2;
    private static final double MIDPOINT_FRACTION = 0.5D;
    private final double[] edges;
    private final int cellHeight;
    private final int blockCount;
    private int inCellZ;
    private int cachedPage = -1;
    private long preparedGeneration = Long.MIN_VALUE;
    private double pageLower;
    private double pageUpper;
    private double pageStep;

    public ArenaInterpolationColumn(double[] edges, int cellHeight, int blockCount) {
        this.edges = Objects.requireNonNull(edges, "edges");
        if (cellHeight <= 0 || cellHeight % Layout.PAGE_HEIGHT != 0
                || blockCount <= 0 || blockCount % cellHeight != 0) {
            throw new IllegalArgumentException("Column height must align to Arena cells and pages");
        }
        if (edges.length != (blockCount / cellHeight + 1) * 2) {
            throw new IllegalArgumentException("Unexpected Arena interpolation boundary count");
        }
        this.cellHeight = cellHeight;
        this.blockCount = blockCount;
    }

    public void prepare(int inCellZ) {
        this.inCellZ = inCellZ;
        this.cachedPage = -1;
        this.preparedGeneration++;
    }

    void prepareIfChanged(int inCellZ, long generation) {
        if (this.preparedGeneration == generation) return;
        this.inCellZ = inCellZ;
        this.cachedPage = -1;
        this.preparedGeneration = generation;
    }

    @Override
    public int length() {
        return this.blockCount;
    }

    @Override
    public double valueAt(int index) {
        Objects.checkIndex(index, this.blockCount);
        int page = index / Layout.PAGE_HEIGHT;
        if (page != this.cachedPage) this.preparePage(page);
        return ArenaInterpolationMath.pageValue(
                this.pageLower, this.pageUpper, this.pageStep, index % Layout.PAGE_HEIGHT);
    }

    @Override
    public void fillRange(double[] target, int fromInclusive, int toExclusive) {
        Objects.checkFromToIndex(fromInclusive, toExclusive, this.blockCount);
        if (target.length != this.blockCount) {
            throw new IllegalArgumentException("Interpolation target length mismatch");
        }
        if (fromInclusive == toExclusive) return;
        if (fromInclusive == 0 && toExclusive == this.blockCount) {
            this.fillWholeColumn(target);
            return;
        }
        int firstFull = Math.min(toExclusive,
                (fromInclusive + Layout.PAGE_HEIGHT - 1) / Layout.PAGE_HEIGHT * Layout.PAGE_HEIGHT);
        int endFull = Math.max(firstFull, toExclusive / Layout.PAGE_HEIGHT * Layout.PAGE_HEIGHT);
        this.fillPartialPage(target, fromInclusive, firstFull);
        this.fillFullPages(target, firstFull, endFull);
        this.fillPartialPage(target, endFull, toExclusive);
    }

    private void fillWholeColumn(double[] target) {
        if (this.cellHeight == TWO_PAGE_CELL_HEIGHT) {
            this.fillTwoPageColumn(target);
            return;
        }
        double lower = this.boundary(0);
        int cellCount = this.blockCount / this.cellHeight;
        for (int cell = 0; cell < cellCount; cell++) {
            double upper = this.boundary(cell + 1);
            this.fillCellPages(target, cell * this.cellHeight, 0, this.cellHeight, lower, upper);
            lower = upper;
        }
    }

    private void fillTwoPageColumn(double[] target) {
        double lower = this.boundary(0);
        for (int cell = 0; cell < this.blockCount / TWO_PAGE_CELL_HEIGHT; cell++) {
            double upper = this.boundary(cell + 1);
            double middle = ArenaInterpolationMath.pageLower(lower, upper - lower, MIDPOINT_FRACTION);
            int base = cell * TWO_PAGE_CELL_HEIGHT;
            writeFullPage(target, base + Layout.PAGE_HEIGHT, middle, upper);
            writeFullPage(target, base, lower, middle);
            lower = upper;
        }
    }

    private void fillFullPages(double[] target, int from, int to) {
        if (from == to) return;
        int firstCell = from / this.cellHeight;
        int lastCell = (to - 1) / this.cellHeight;
        double lower = this.boundary(firstCell);
        for (int cell = firstCell; cell <= lastCell; cell++) {
            double upper = this.boundary(cell + 1);
            int base = cell * this.cellHeight;
            this.fillCellPages(target, base, Math.max(from - base, 0),
                    Math.min(to - base, this.cellHeight), lower, upper);
            lower = upper;
        }
    }

    private void fillCellPages(double[] target, int base, int start, int end, double lower, double upper) {
        int page = end - Layout.PAGE_HEIGHT;
        double difference = upper - lower;
        double pageUpper = end == this.cellHeight ? upper : this.pageBoundary(lower, difference, end);
        for (; page >= start; page -= Layout.PAGE_HEIGHT) {
            double pageLower = this.pageBoundary(lower, difference, page);
            writeFullPage(target, base + page, pageLower, pageUpper);
            pageUpper = pageLower;
        }
    }

    private static void writeFullPage(double[] target, int index, double lower, double upper) {
        double step = ArenaInterpolationMath.pageStep(lower, upper);
        double top = upper + step;
        target[index + 3] = top;
        target[index + 2] = top + step;
        target[index + 1] = lower - step;
        target[index] = lower;
    }

    private void fillPartialPage(double[] target, int from, int to) {
        if (from == to) return;
        int page = from / Layout.PAGE_HEIGHT;
        if (page != this.cachedPage) this.preparePage(page);
        int base = page * Layout.PAGE_HEIGHT;
        for (int i = from; i < to; i++) {
            target[i] = ArenaInterpolationMath.pageValue(
                    this.pageLower, this.pageUpper, this.pageStep, i - base);
        }
    }

    private void preparePage(int page) {
        int y = page * Layout.PAGE_HEIGHT;
        int cell = y / this.cellHeight;
        int start = y % this.cellHeight;
        double lower = this.boundary(cell);
        double upper = this.boundary(cell + 1);
        double difference = upper - lower;
        this.pageLower = this.pageBoundary(lower, difference, start);
        this.pageUpper = start + Layout.PAGE_HEIGHT == this.cellHeight ? upper
                : this.pageBoundary(lower, difference, start + Layout.PAGE_HEIGHT);
        this.pageStep = ArenaInterpolationMath.pageStep(this.pageLower, this.pageUpper);
        this.cachedPage = page;
    }

    private double boundary(int cell) {
        int edge = cell * 2;
        return this.edges[edge] + this.inCellZ * this.edges[edge + 1];
    }

    private double pageBoundary(double lower, double difference, int start) {
        return ArenaInterpolationMath.pageLower(lower, difference, start, this.cellHeight);
    }
}
