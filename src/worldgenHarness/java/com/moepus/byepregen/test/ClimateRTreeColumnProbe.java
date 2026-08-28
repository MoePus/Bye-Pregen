package com.moepus.byepregen.test;

import com.moepus.byepregen.worldgen.biome.DepthClimateParameterList;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.biome.Climate;

final class ClimateRTreeColumnProbe {
    private static final float[] COLUMN_DEPTHS = {
            -1.25F, -0.75F, -0.25F, 0.0F, 0.25F, 0.75F, 1.25F
    };

    private ClimateRTreeColumnProbe() {
    }

    static void verify() {
        verifyTieAndLastResult();
        verifyColumnsAgainstFullSearch();
        verifyEpochInvalidation();
    }

    private static void verifyTieAndLastResult() {
        List<Pair<Climate.ParameterPoint, String>> leaves = List.of(
                leaf("negative", 0.0F, -1.0F),
                leaf("positive", 0.0F, 1.0F));
        Climate.ParameterList<String> reference = new Climate.ParameterList<>(leaves);
        Climate.ParameterList<String> candidate = new Climate.ParameterList<>(leaves);
        DepthClimateParameterList<String> depth = depthLookup(candidate);

        compareOrdinary(reference, candidate, target(0.0F, -1.0F), "ordinary negative warmup");
        depth.byepregen$beginDepthColumn(targetArray(target(0.0F, 1.0F)));
        compareDepth(reference, depth, target(0.0F, 1.0F), "depth positive warmup");
        require("positive".equals(compareDepth(
                reference, depth, target(0.0F, 0.0F), "positive lastResult tie")),
                "Depth search did not retain the positive lastResult on a tie");

        compareOrdinary(reference, candidate, target(0.0F, -1.0F), "ordinary rewarm");
        depth.byepregen$beginDepthColumn(targetArray(target(0.0F, 0.0F)));
        require("negative".equals(compareDepth(
                reference, depth, target(0.0F, 0.0F), "negative lastResult tie")),
                "Depth search did not retain the negative lastResult on a tie");
    }

    private static void verifyColumnsAgainstFullSearch() {
        List<Pair<Climate.ParameterPoint, String>> leaves = variedLeaves();
        Climate.ParameterList<String> reference = new Climate.ParameterList<>(leaves);
        Climate.ParameterList<String> candidate = new Climate.ParameterList<>(leaves);
        DepthClimateParameterList<String> depth = depthLookup(candidate);
        float[][] columns = {
                {-0.8F, -0.4F, 0.2F, 0.6F, -0.3F},
                {0.1F, 0.7F, -0.6F, -0.2F, 0.8F},
                {0.9F, -0.1F, 0.5F, -0.8F, 0.1F}
        };
        for (int column = 0; column < columns.length; ++column) {
            float[] fixed = columns[column];
            Climate.TargetPoint first = target(fixed, COLUMN_DEPTHS[0]);
            depth.byepregen$beginDepthColumn(targetArray(first));
            for (float value : COLUMN_DEPTHS) {
                compareDepth(reference, depth, target(fixed, value),
                        "column=" + column + ", depth=" + value);
            }
        }
    }

    private static void verifyEpochInvalidation() {
        List<Pair<Climate.ParameterPoint, String>> leaves = List.of(
                leaf("cold", -1.0F, 0.0F),
                leaf("warm", 1.0F, 0.0F));
        Climate.ParameterList<String> reference = new Climate.ParameterList<>(leaves);
        Climate.ParameterList<String> candidate = new Climate.ParameterList<>(leaves);
        DepthClimateParameterList<String> depth = depthLookup(candidate);

        Climate.TargetPoint cold = target(-0.8F, 0.25F);
        depth.byepregen$beginDepthColumn(targetArray(cold));
        require("cold".equals(compareDepth(reference, depth, cold, "cold column")),
                "Cold column selected the wrong leaf");

        Climate.TargetPoint warm = target(0.8F, -0.25F);
        depth.byepregen$beginDepthColumn(targetArray(warm));
        require("warm".equals(compareDepth(reference, depth, warm, "warm column")),
                "New column reused stale fixed distances");
    }

    private static List<Pair<Climate.ParameterPoint, String>> variedLeaves() {
        List<Pair<Climate.ParameterPoint, String>> leaves = new ArrayList<>();
        for (int index = 0; index < 12; ++index) {
            float temperature = (index % 3 - 1) * 0.8F;
            float humidity = (index / 3 % 2 == 0) ? -0.6F : 0.6F;
            float continentalness = (index % 4 - 1.5F) * 0.4F;
            float erosion = (index / 2 % 3 - 1) * 0.7F;
            float depth = (index % 6 - 2.5F) * 0.45F;
            float weirdness = (index / 4 - 1) * 0.75F;
            leaves.add(Pair.of(Climate.parameters(temperature, humidity, continentalness,
                    erosion, depth, weirdness, 0.0F), "leaf-" + index));
        }
        return leaves;
    }

    private static Pair<Climate.ParameterPoint, String> leaf(
            String value,
            float temperature,
            float depth
    ) {
        return Pair.of(Climate.parameters(
                temperature, 0.0F, 0.0F, 0.0F, depth, 0.0F, 0.0F), value);
    }

    private static Climate.TargetPoint target(float temperature, float depth) {
        return Climate.target(temperature, 0.0F, 0.0F, 0.0F, depth, 0.0F);
    }

    private static Climate.TargetPoint target(float[] fixed, float depth) {
        return Climate.target(fixed[0], fixed[1], fixed[2], fixed[3], depth, fixed[4]);
    }

    private static long[] targetArray(Climate.TargetPoint target) {
        return new long[]{target.temperature(), target.humidity(), target.continentalness(),
                target.erosion(), target.depth(), target.weirdness(), 0L};
    }

    private static void compareOrdinary(
            Climate.ParameterList<String> reference,
            Climate.ParameterList<String> candidate,
            Climate.TargetPoint target,
            String label
    ) {
        String expected = reference.findValue(target);
        String actual = candidate.findValue(target);
        require(Objects.equals(expected, actual), mismatch(label, expected, actual));
    }

    private static String compareDepth(
            Climate.ParameterList<String> reference,
            DepthClimateParameterList<String> candidate,
            Climate.TargetPoint target,
            String label
    ) {
        String expected = reference.findValue(target);
        String actual = candidate.byepregen$findValueAtDepth(target.depth());
        require(Objects.equals(expected, actual), mismatch(label, expected, actual));
        return actual;
    }

    @SuppressWarnings("unchecked")
    private static DepthClimateParameterList<String> depthLookup(
            Climate.ParameterList<String> parameters
    ) {
        require((Object)parameters instanceof DepthClimateParameterList<?>,
                "Climate ParameterList lacks the depth-column mixin");
        return (DepthClimateParameterList<String>)(Object)parameters;
    }

    private static String mismatch(String label, String expected, String actual) {
        return "R-tree column mismatch at " + label + ": expected=" + expected + ", actual=" + actual;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
