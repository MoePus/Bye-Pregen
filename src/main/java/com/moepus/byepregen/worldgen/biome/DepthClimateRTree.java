package com.moepus.byepregen.worldgen.biome;

/** R-tree lookup for a column where only the depth coordinate changes. */
public interface DepthClimateRTree<T> {
    void byepregen$beginDepthColumn(long[] target);

    T byepregen$searchDepth(long depth);
}
