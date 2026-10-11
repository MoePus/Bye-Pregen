package com.moepus.byepregen.dfc.runtime;

/**
 * A reusable view of one interpolated input at the active column's sample positions.
 * Owned by one column evaluation; callers must not retain it across column changes.
 */
public interface InterpolatedColumn {
    int length();

    double valueAt(int index);

    /** Writes only [fromInclusive, toExclusive) into an equal-length consumer array. */
    void fillRange(double[] target, int fromInclusive, int toExclusive);
}
