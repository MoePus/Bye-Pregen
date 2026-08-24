package com.moepus.byepregen.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.mixin.feature.disk.RuleBasedStateProviderMixin;
import java.lang.reflect.Field;
import net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedStateProvider;
import org.junit.jupiter.api.Test;

final class DiskNullableFallbackTest {
    @Test
    void unmatchedRuleWithoutFallbackSkipsPlacement() throws ReflectiveOperationException {
        RuleBasedStateProviderMixin mixin = new RuleBasedStateProviderMixin() {
        };
        setField(mixin, "byepregen$rules", new RuleBasedStateProvider.Rule[0]);

        assertNull(mixin.byepregen$getState(null, null, null));
    }

    @Test
    void skippedStateDoesNotBreakAContinuousPlacementRun() {
        boolean firstStartsRun = FastDiskPlacement.startsPlacementRun(false, FastDiskPlacement.PLACED);
        boolean afterFirstPlacement = FastDiskPlacement.nextPlacedAboveState(false, FastDiskPlacement.PLACED);
        boolean afterSkippedState = FastDiskPlacement.nextPlacedAboveState(
                afterFirstPlacement,
                FastDiskPlacement.PRESERVE_PLACED_ABOVE_STATE
        );
        boolean secondStartsRun = FastDiskPlacement.startsPlacementRun(afterSkippedState, FastDiskPlacement.PLACED);
        boolean afterTargetMiss = FastDiskPlacement.nextPlacedAboveState(afterSkippedState, 0);

        assertTrue(firstStartsRun);
        assertTrue(afterFirstPlacement);
        assertTrue(afterSkippedState);
        assertFalse(secondStartsRun, "second placement must not mark above again");
        assertFalse(afterTargetMiss, "a target miss must end the placement run");
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = RuleBasedStateProviderMixin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
