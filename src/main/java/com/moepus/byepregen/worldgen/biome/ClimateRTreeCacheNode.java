package com.moepus.byepregen.worldgen.biome;

/** Stable per-tree index used by the lazy column distance cache. */
public interface ClimateRTreeCacheNode {
    int byepregen$cacheIndex();

    void byepregen$setCacheIndex(int index);
}
