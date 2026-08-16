package com.moepus.byepregen.test;

import net.minecraft.server.level.ServerLevel;

final class BlackoutLightFuzzFixture implements LightFuzzFixture {
    private final LightBlackoutFuzz fuzz;

    BlackoutLightFuzzFixture(ServerLevel level, long seed) {
        this.fuzz = new LightBlackoutFuzz(level, seed);
    }

    @Override
    public int loadRadius() {
        return LightBlackoutFuzz.LOAD_RADIUS;
    }

    @Override
    public void clearVolume() {
        this.fuzz.clearVolume();
    }

    @Override
    public void buildFixture() {
        this.fuzz.buildFixture();
    }

    @Override
    public void applyUpdate(int round) {
        this.fuzz.applyUpdate();
    }

    @Override
    public int updateRounds() {
        return LightBlackoutFuzz.ROUNDS;
    }

    @Override
    public String updateStageName(int round) {
        return "blackout round " + round;
    }

    @Override
    public void verifyUpdate(int round) {
        this.fuzz.verify(round);
    }

    @Override
    public void releaseLoadedChunks() {
        this.fuzz.releaseLoadedChunks();
    }

    boolean reloadRoundTripWhenUnloaded() {
        return this.fuzz.reloadRoundTripWhenUnloaded();
    }

    void acceptReconciledRoundTrip() {
        this.fuzz.acceptReconciledRoundTrip();
    }

    boolean verifyRoundTrip() {
        return this.fuzz.verifyRoundTrip();
    }
}
