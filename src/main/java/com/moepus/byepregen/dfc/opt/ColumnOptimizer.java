/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.opt;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstPrinter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ColumnOptimizer {
    public static final String DISABLED_PASSES_PROPERTY = "byepregen.dfc.disabledPasses";
    private static final int CYCLE_GUARD = 64;
    private static final Map<String, OptimizationPass> CORE = corePasses();

    private ColumnOptimizer() {
    }

    public static Result optimize(AstNode root) {
        Set<String> disabled = disabledPasses(System.getProperty(DISABLED_PASSES_PROPERTY, ""));
        List<String> executed = new ArrayList<>();
        long core1Start = System.nanoTime();
        AstNode core1 = fixedPoint(root, disabled, executed, "Core-1");
        long core1Nanos = System.nanoTime() - core1Start;
        long splineStart = System.nanoTime();
        AstNode spline = disabled.contains("spline-arithmetic")
                ? core1 : run("spline-arithmetic", SplineArithmeticPass::apply, core1, executed);
        long splineNanos = System.nanoTime() - splineStart;
        long core2Start = System.nanoTime();
        AstNode core2 = fixedPoint(spline, disabled, executed, "Core-2");
        long core2Nanos = System.nanoTime() - core2Start;
        return new Result(core2, List.copyOf(executed), core1Nanos, splineNanos, core2Nanos);
    }

    static AstNode fixedPoint(AstNode root, Set<String> disabled,
                              List<String> executed, String phase) {
        return fixedPoint(root, disabled, executed, phase, CORE, CYCLE_GUARD);
    }

    static AstNode fixedPoint(
            AstNode root,
            Set<String> disabled,
            List<String> executed,
            String phase,
            Map<String, OptimizationPass> passes,
            int cycleGuard
    ) {
        AstNode current = root;
        String lastChanged = "none";
        for (int iteration = 0; iteration < cycleGuard; ++iteration) {
            AstNode iterationStart = current;
            for (Map.Entry<String, OptimizationPass> entry : passes.entrySet()) {
                if (disabled.contains(entry.getKey())) continue;
                AstNode next = run(entry.getKey(), entry.getValue(), current, executed);
                if (next != current) lastChanged = entry.getKey();
                current = next;
            }
            if (current == iterationStart) return current;
        }
        throw new OptimizationCycleException(
                phase, cycleGuard, lastChanged, AstPrinter.print(current));
    }

    private static AstNode run(String name, OptimizationPass pass,
                               AstNode root, List<String> executed) {
        executed.add(name);
        return pass.apply(root);
    }

    public static Set<String> disabledPasses(String property) {
        Set<String> disabled = new LinkedHashSet<>();
        if (property.isBlank()) return disabled;
        Set<String> known = new LinkedHashSet<>(CORE.keySet());
        known.add("spline-arithmetic");
        for (String part : property.split(",")) {
            String name = part.trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) continue;
            if (!known.contains(name)) {
                throw new IllegalArgumentException("Unknown ByePregen DFC pass: " + name);
            }
            disabled.add(name);
        }
        return disabled;
    }

    private static Map<String, OptimizationPass> corePasses() {
        Map<String, OptimizationPass> passes = new LinkedHashMap<>();
        passes.put("canonicalize", CorePasses::canonicalize);
        passes.put("constant-fold", CorePasses::constantFold);
        passes.put("strength-reduce", CorePasses::strengthReduce);
        passes.put("algebraic-simplify", CorePasses::algebraicSimplify);
        passes.put("identity-eliminate", CorePasses::identityEliminate);
        passes.put("range-prune", CorePasses::rangePrune);
        return Collections.unmodifiableMap(passes);
    }

    public record Result(
            AstNode root,
            List<String> executedPasses,
            long core1Nanos,
            long splineNanos,
            long core2Nanos
    ) {
    }

    public static final class OptimizationCycleException extends RuntimeException {
        private final String astDump;

        private OptimizationCycleException(
                String phase,
                int cycleGuard,
                String lastPass,
                String astDump
        ) {
            super(phase + " exceeded " + cycleGuard
                    + " iterations; last modifying pass: " + lastPass);
            this.astDump = astDump;
        }

        public String astDump() {
            return this.astDump;
        }
    }
}
