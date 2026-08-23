package com.moepus.byepregen.server.tick;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChunkTickPermutationIteratorTest {
    private static final int[] SIZES = {0, 1, 2, 3, 31, 32, 33, 100, 1000};
    private static final long[] SEEDS = {0L, 1L, 42L, Long.MAX_VALUE, Long.MIN_VALUE};

    @Test
    void visitsEveryValueExactlyOnce() {
        ChunkTickPermutationIterator iterator = new ChunkTickPermutationIterator();
        for (int size : SIZES) {
            List<Integer> values = IntStream.range(0, size).boxed().toList();
            for (long seed : SEEDS) {
                iterator.reset(values, RandomSource.create(seed));
                Set<Integer> visited = new HashSet<>();
                while (iterator.hasNext()) {
                    assertTrue(visited.add((Integer) iterator.next()), "duplicate for size=" + size + ", seed=" + seed);
                }
                assertEquals(new HashSet<>(values), visited, "coverage for size=" + size + ", seed=" + seed);
                assertThrows(NoSuchElementException.class, iterator::next);
            }
        }
    }

    @Test
    void canBeResetAndReused() {
        ChunkTickPermutationIterator iterator = new ChunkTickPermutationIterator();
        iterator.reset(List.of(1, 2, 3), RandomSource.create(1L));
        while (iterator.hasNext()) {
            iterator.next();
        }

        List<Integer> replacement = List.of(7, 8);
        iterator.reset(replacement, RandomSource.create(2L));
        assertEquals(Set.copyOf(replacement), Set.of((Integer) iterator.next(), (Integer) iterator.next()));
        assertFalse(iterator.hasNext());
    }
}
