package com.moepus.byepregen.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DiskNullableFallbackTest {
    @Test
    void skippedStateDoesNotBreakAContinuousPlacementRun() {
        boolean firstStartsRun = FastDiskPlacement.startsPlacementRun(
                false, FastDiskPlacement.PositionResult.PLACED);
        boolean afterFirstPlacement = FastDiskPlacement.nextPlacedAboveState(
                false, FastDiskPlacement.PositionResult.PLACED);
        boolean afterSkippedState = FastDiskPlacement.nextPlacedAboveState(
                afterFirstPlacement,
                FastDiskPlacement.PositionResult.PRESERVED
        );
        boolean secondStartsRun = FastDiskPlacement.startsPlacementRun(
                afterSkippedState, FastDiskPlacement.PositionResult.PLACED);
        boolean afterTargetMiss = FastDiskPlacement.nextPlacedAboveState(
                afterSkippedState, FastDiskPlacement.PositionResult.NOT_REPLACED);

        assertTrue(firstStartsRun);
        assertTrue(afterFirstPlacement);
        assertTrue(afterSkippedState);
        assertFalse(secondStartsRun, "second placement must not mark above again");
        assertFalse(afterTargetMiss, "a target miss must end the placement run");
    }

}
