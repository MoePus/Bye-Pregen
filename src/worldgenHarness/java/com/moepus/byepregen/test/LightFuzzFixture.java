package com.moepus.byepregen.test;

interface LightFuzzFixture {
    int DEFAULT_LOAD_RADIUS = 3;

    default int loadRadius() {
        return DEFAULT_LOAD_RADIUS;
    }

    void clearVolume();

    void buildFixture();

    void applyUpdate(int round);

    default int updateRounds() {
        return 1;
    }

    default String updateStageName(int round) {
        return "update round " + round;
    }

    default void verifyUpdate(int round) {
    }

    default void releaseLoadedChunks() {
    }
}
