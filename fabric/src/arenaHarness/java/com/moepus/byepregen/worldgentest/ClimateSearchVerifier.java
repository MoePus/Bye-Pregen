package com.moepus.byepregen.worldgentest;

import com.moepus.byepregen.worldgen.biome.FastClimateParameterList;
import com.mojang.datafixers.util.Pair;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import net.minecraft.world.level.biome.Climate;

final class ClimateSearchVerifier {
    private static final int QUERIES = 10_000;
    private static final int THREADS = 4;
    private static final Climate.DistanceMetric METRIC = ClimateSearchVerifier::distance;
    private static final Method NATIVE_SEARCH;

    static {
        try {
            NATIVE_SEARCH = Climate.ParameterList.class.getDeclaredMethod("findValueIndex",
                    Climate.TargetPoint.class, Climate.DistanceMetric.class);
            NATIVE_SEARCH.setAccessible(true);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    static int verify() throws Exception {
        int count = 0;
        for (int size : new int[]{1, 19, 257}) {
            var values = entries(size);
            for (int branching : new int[]{2, 6, 19, 64}) {
                var optimized = new Climate.ParameterList<>(values).rebuildWithChildrenPerNode(branching);
                var nativeTree = new Climate.ParameterList<>(values).rebuildWithChildrenPerNode(branching);
                if (!(optimized instanceof FastClimateParameterList<?>)) throw new AssertionError("Missing climate mixin");
                try (var executor = Executors.newFixedThreadPool(THREADS)) {
                    var jobs = new ArrayList<java.util.concurrent.Future<Integer>>();
                    for (int thread = 0; thread < THREADS; thread++) {
                        final int seed = thread;
                        jobs.add(executor.submit(() -> compare(optimized, nativeTree, seed)));
                    }
                    for (var job : jobs) count += job.get();
                }
                count += compareColumns(optimized, nativeTree, branching);
            }
        }
        return count;
    }

    /**
     * Depth-only sweeps. The first query opens a column (full descent) and the rest reuse the fixed
     * distances of the depth cache, including repeated depths that force the vanilla tie behavior.
     */
    private static int compareColumns(Climate.ParameterList<Integer> optimized,
                                      Climate.ParameterList<Integer> nativeTree, int seed) throws Exception {
        Random random = new Random(seed * 31L + 7L);
        int count = 0;
        for (int column = 0; column < 64; column++) {
            long temperature = coordinate(random);
            long humidity = coordinate(random);
            long continentalness = coordinate(random);
            long erosion = coordinate(random);
            long firstDepth = coordinate(random);
            long weirdness = coordinate(random);
            for (int step = 0; step < 32; step++) {
                long depth = step % 4 == 0 || step < 2 ? firstDepth : coordinate(random);
                var target = new Climate.TargetPoint(temperature, humidity, continentalness, erosion, depth, weirdness);
                Object expected = NATIVE_SEARCH.invoke(nativeTree, target, METRIC);
                Object actual = step % 2 == 0 ? optimized.findValueIndex(target) : rawSearch(optimized, target);
                if (!expected.equals(actual)) {
                    throw new AssertionError("Climate column mismatch at " + column + '/' + step + ": " + target);
                }
                count++;
            }
        }
        return count;
    }

    private static int compare(Climate.ParameterList<Integer> optimized,
                               Climate.ParameterList<Integer> nativeTree, int seed) throws Exception {
        Random random = new Random(seed);
        for (int i = 0; i < QUERIES; i++) {
            // A small coordinate lattice creates overlapping intervals and frequent exact ties.
            var target = new Climate.TargetPoint(coordinate(random), coordinate(random), coordinate(random),
                    coordinate(random), coordinate(random), coordinate(random));
            Object expected = NATIVE_SEARCH.invoke(nativeTree, target, METRIC);
            Object actual = i % 2 == 0 ? optimized.findValueIndex(target) : rawSearch(optimized, target);
            if (!expected.equals(actual)) throw new AssertionError("Climate mismatch at " + i + ": " + target);
            if (i % 17 == 0) {
                // Extension metrics must update the same last-result slot used by the optimized path.
                Climate.DistanceMetric custom = (node, values) -> node.parameterSpace[4].distance(values[4]);
                Object a = NATIVE_SEARCH.invoke(nativeTree, target, custom);
                Object b = NATIVE_SEARCH.invoke(optimized, target, custom);
                if (!a.equals(b)) throw new AssertionError("Custom metric mismatch");
            }
        }
        return QUERIES;
    }

    @SuppressWarnings("unchecked")
    private static Object rawSearch(Climate.ParameterList<Integer> tree, Climate.TargetPoint target) {
        return ((FastClimateParameterList<Integer>) tree).byepregen$findValue(new long[]{target.temperature(),
                target.humidity(), target.continentalness(), target.erosion(), target.depth(), target.weirdness(), 0});
    }

    private static List<Pair<Climate.ParameterPoint, Integer>> entries(int size) {
        Random random = new Random(9876);
        List<Pair<Climate.ParameterPoint, Integer>> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            entries.add(Pair.of(new Climate.ParameterPoint(parameter(random), parameter(random), parameter(random),
                    parameter(random), parameter(random), parameter(random), i % 3), i));
        }
        return entries;
    }

    private static Climate.Parameter parameter(Random random) {
        long min = coordinate(random);
        return new Climate.Parameter(min, min + random.nextInt(3));
    }

    private static long coordinate(Random random) { return random.nextInt(9) - 4; }

    private static long distance(Climate.RTree.Node<?> node, long[] target) {
        long sum = 0;
        for (int i = 0; i < 7; i++) {
            long delta = node.parameterSpace[i].distance(target[i]);
            sum += delta * delta;
        }
        return sum;
    }
}
