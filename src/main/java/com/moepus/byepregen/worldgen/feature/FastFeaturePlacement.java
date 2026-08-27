package com.moepus.byepregen.worldgen.feature;

@FunctionalInterface
public interface FastFeaturePlacement {
    boolean placeOrigin(int x, int y, int z);
}
