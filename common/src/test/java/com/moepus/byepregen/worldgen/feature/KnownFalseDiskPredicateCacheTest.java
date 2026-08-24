package com.moepus.byepregen.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

class KnownFalseDiskPredicateCacheTest {
    @Test
    void invalidatesEveryPredicatePositionThatReadsTheChangedBlock() {
        KnownFalseDiskPredicateCache cache = new KnownFalseDiskPredicateCache(new Vec3i[]{
                Vec3i.ZERO,
                new Vec3i(0, 1, 0),
                new Vec3i(-2, 0, 1)
        }, -64, 320);
        cache.add(10, 20, 30);
        cache.add(10, 19, 30);
        cache.add(12, 20, 29);
        cache.add(50, 60, 70);

        cache.invalidate(10, 20, 30);

        assertFalse(cache.contains(10, 20, 30));
        assertFalse(cache.contains(10, 19, 30));
        assertFalse(cache.contains(12, 20, 29));
        assertTrue(cache.contains(50, 60, 70));
    }

    @Test
    void clearDropsAllKnownResults() {
        KnownFalseDiskPredicateCache cache = new KnownFalseDiskPredicateCache(
                new Vec3i[]{Vec3i.ZERO},
                -64,
                320
        );
        cache.add(1, 2, 3);

        cache.clear();

        assertFalse(cache.contains(1, 2, 3));
    }
}
