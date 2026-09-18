package com.moepus.byepregen.dfctest;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Density functions for the operators the compiler emits but vanilla worldgen never builds.
 *
 * <p>Vanilla's own data only reaches abs, square, cube, negate, squeeze, clamp, half/quarter
 * negative (a leaky relu), the dense add/sub/mul/div/min/max samplers, their constant-right
 * specializations, the zero and range short circuits, and lerp. Sqrt, reciprocal, log, sign, a
 * constant right operand of min and a non-constant divisor never occur anywhere in the shipped
 * density functions, and no sampled volume takes the zero short circuit of mul/div or the range
 * short circuit of max, so the kernels generated for those cases were never compared.
 *
 * <p>The probes ride the same baseline and optimized runs as the registry functions, so the
 * ordinary number comparison covers them instead.
 */
final class DfcOperatorProbes {
    private DfcOperatorProbes() { }

    static void add(Map<String, DensityFunction> functions, HolderGetter<NormalNoise> noises) {
        Holder<NormalNoise> noise = noises.getOrThrow(ResourceKey.create(Registries.NOISE,
                Identifier.fromNamespaceAndPath("minecraft", "temperature")));
        DensityFunction base = DensityFunctions.noise(noise);
        // Reciprocal and log are undefined at or below zero, so their inputs stay in a safe range.
        DensityFunction positive = DensityFunctions.add(DensityFunctions.abs(base), DensityFunctions.constant(0.5F));
        functions.put("operator/sqrt", DensityFunctions.sqrt(DensityFunctions.abs(base)));
        functions.put("operator/reciprocal", DensityFunctions.reciprocal(positive));
        functions.put("operator/log", DensityFunctions.log(positive));
        functions.put("operator/sign", DensityFunctions.sign(base));
        functions.put("operator/min-constant", DensityFunctions.min(base, DensityFunctions.constant(0.25F)));
        // max skips the right operand once the left one reaches its range maximum. The two operand
        // ranges have to overlap, otherwise vanilla elides the max into the wider operand and no
        // sampler is left to compare.
        functions.put("operator/max-range",
                DensityFunctions.max(DensityFunctions.clamp(base, 0.0F, 1.0F),
                        DensityFunctions.clamp(base, -0.5F, 0.25F)));
        // The left operand is exactly zero, which is the case vanilla short circuits in the point path.
        functions.put("operator/div-zero-left",
                DensityFunctions.div(DensityFunctions.mul(base, DensityFunctions.constant(0.0F)), positive));
    }
}
