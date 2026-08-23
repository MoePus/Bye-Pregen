package com.moepus.byepregen.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

class PredicateMemoizedDiskExecutionTest {
    private static final int WORLD_SIZE = 48;
    private static final int VISITS = 400;
    private static final int TRIALS = 5_000;
    private static final int SOURCE = 1;
    private static final int OUTPUT = 2;
    private static final Vec3i[] DEPENDENCIES = {
            Vec3i.ZERO,
            new Vec3i(0, 1, 0),
            new Vec3i(0, -2, 0)
    };

    @Test
    void memoizedFalseResultsMatchEventByEventExecution() {
        Random random = new Random(0xD15C_CAC4EL);
        for (int trial = 0; trial < TRIALS; trial++) {
            Scenario scenario = randomScenario(random);
            Result reference = executeReference(scenario);
            Result memoized = executeMemoized(scenario);
            assertArrayEquals(reference.states(), memoized.states(), "states in trial " + trial);
            assertEquals(reference.placements(), memoized.placements(), "placements in trial " + trial);
        }
    }

    private static Scenario randomScenario(Random random) {
        int[] states = new int[WORLD_SIZE];
        for (int index = 0; index < states.length; index++) {
            states[index] = random.nextInt(4);
        }
        int[] visits = new int[VISITS];
        for (int index = 0; index < visits.length; index++) {
            visits[index] = 2 + random.nextInt(WORLD_SIZE - 4);
        }
        return new Scenario(states, visits);
    }

    private static Result executeReference(Scenario scenario) {
        int[] states = scenario.initialStates().clone();
        int placements = 0;
        for (int position : scenario.visits()) {
            if (matches(states, position)) {
                states[position] = OUTPUT;
                placements++;
            }
        }
        return new Result(states, placements);
    }

    private static Result executeMemoized(Scenario scenario) {
        int[] states = scenario.initialStates().clone();
        KnownFalseDiskPredicateCache cache = new KnownFalseDiskPredicateCache(DEPENDENCIES, 0, WORLD_SIZE);
        int placements = 0;
        for (int position : scenario.visits()) {
            if (cache.contains(0, position, 0)) {
                continue;
            }
            if (!matches(states, position)) {
                cache.add(0, position, 0);
                continue;
            }
            states[position] = OUTPUT;
            cache.invalidate(0, position, 0);
            placements++;
        }
        return new Result(states, placements);
    }

    private static boolean matches(int[] states, int position) {
        return states[position] == SOURCE
                && (states[position + 1] == OUTPUT || states[position - 2] == 3);
    }

    private record Scenario(int[] initialStates, int[] visits) {
    }

    private record Result(int[] states, int placements) {
    }
}
