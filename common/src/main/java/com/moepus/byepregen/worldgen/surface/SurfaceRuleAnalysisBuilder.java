package com.moepus.byepregen.worldgen.surface;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

final class SurfaceRuleAnalysisBuilder {
    private final SurfaceRuleAnalyzer.Limits limits;
    private final Predicate<Object> trustedSources;
    private final IdentityHashMap<Object, Boolean> active = new IdentityHashMap<>();
    private final Map<SurfaceConditionSpec, SurfaceRulePlan.ConditionValue> canonicalValues =
            new HashMap<>();
    private int sourceNodes;
    private int nextValue;

    SurfaceRuleAnalysisBuilder(
            SurfaceRuleAnalyzer.Limits limits,
            Predicate<Object> trustedSources
    ) {
        this.limits = limits;
        this.trustedSources = trustedSources;
    }

    boolean isTrusted(Object source) {
        return this.trustedSources.test(source);
    }

    boolean enter(Object source, int depth) {
        if (depth > this.limits.maxDepth() || this.sourceNodes >= this.limits.maxSourceNodes()) {
            return false;
        }
        if (this.active.put(source, Boolean.TRUE) != null) {
            return false;
        }
        this.sourceNodes++;
        return true;
    }

    void exit(Object source) {
        this.active.remove(source);
    }

    boolean canExpand(int childCount) {
        return childCount >= 0
                && (long) this.sourceNodes + childCount <= this.limits.maxSourceNodes();
    }

    SurfaceRulePlan.OpaqueRule opaqueRule(Object source) {
        return new SurfaceRulePlan.OpaqueRule(source);
    }

    SurfaceRulePlan.OpaqueCondition opaqueCondition(Object source) {
        SurfaceConditionSpec spec = new SurfaceConditionSpec.Opaque(source.getClass().getName());
        return new SurfaceRulePlan.OpaqueCondition(source, this.newValue(spec));
    }

    SurfaceRulePlan.KnownCondition knownCondition(
            Object source,
            SurfaceConditionSpec spec
    ) {
        return new SurfaceRulePlan.KnownCondition(source, this.value(spec));
    }

    SurfaceRulePlan.ConditionValue value(SurfaceConditionSpec spec) {
        return this.canonicalValues.computeIfAbsent(spec, this::newValue);
    }

    SurfaceRulePlan finish(SurfaceRulePlan.Rule root) {
        return new SurfaceRulePlan(root);
    }

    private SurfaceRulePlan.ConditionValue newValue(SurfaceConditionSpec spec) {
        SurfaceRulePlan.ConditionValue value = new SurfaceRulePlan.ConditionValue(
                new SurfaceRulePlan.ValueId(this.nextValue++), spec
        );
        return value;
    }
}
