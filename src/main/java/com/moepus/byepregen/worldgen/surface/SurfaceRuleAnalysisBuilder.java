package com.moepus.byepregen.worldgen.surface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

final class SurfaceRuleAnalysisBuilder {
    private final SurfaceRuleAnalyzer.Limits limits;
    private final Predicate<Object> trustedSources;
    private final IdentityHashMap<Object, Boolean> active = new IdentityHashMap<>();
    private final Map<ValueKey, SurfaceRulePlan.ConditionValue> canonicalValues = new HashMap<>();
    private final IdentityHashMap<Object, SurfaceRulePlan.CacheGroupId> sharedCacheGroups =
            new IdentityHashMap<>();
    private final List<SurfaceRulePlan.ConditionValue> values = new ArrayList<>();
    private int sourceNodes;
    private int nextEntry;
    private int nextOccurrence;
    private int nextCacheGroup;

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

    SurfaceRulePlan.RuleMetadata ruleMetadata(
            Object source,
            SurfaceRuleSemantics.Semantics semantics,
            SurfaceRuleSemantics.BindingEffect bindingEffect
    ) {
        return new SurfaceRulePlan.RuleMetadata(
                new SurfaceRulePlan.EntryId(this.nextEntry++),
                new SurfaceRulePlan.OccurrenceId(this.nextOccurrence++),
                source, semantics, bindingEffect
        );
    }

    SurfaceRulePlan.ConditionMetadata conditionMetadata(
            Object source,
            SurfaceRuleSemantics.BindingEffect bindingEffect
    ) {
        return this.conditionMetadata(
                this.nextOccurrence(),
                source,
                bindingEffect,
                this.newCacheGroup()
        );
    }

    SurfaceRulePlan.OccurrenceId nextOccurrence() {
        return new SurfaceRulePlan.OccurrenceId(this.nextOccurrence++);
    }

    SurfaceRulePlan.ConditionMetadata conditionMetadata(
            SurfaceRulePlan.OccurrenceId occurrenceId,
            Object source,
            SurfaceRuleSemantics.BindingEffect bindingEffect,
            SurfaceRulePlan.CacheGroupId cacheGroupId
    ) {
        return new SurfaceRulePlan.ConditionMetadata(
                occurrenceId,
                source,
                bindingEffect,
                cacheGroupId
        );
    }

    SurfaceRulePlan.OpaqueRule opaqueRule(Object source) {
        return new SurfaceRulePlan.OpaqueRule(this.ruleMetadata(
                source,
                SurfaceRuleSemantics.OPAQUE,
                SurfaceRuleSemantics.BindingEffect.OPAQUE
        ));
    }

    SurfaceRulePlan.OpaqueCondition opaqueCondition(Object source) {
        SurfaceRulePlan.ConditionMetadata metadata = this.conditionMetadata(
                source, SurfaceRuleSemantics.BindingEffect.OPAQUE
        );
        SurfaceConditionSpec spec = new SurfaceConditionSpec.Opaque(source.getClass().getName());
        return new SurfaceRulePlan.OpaqueCondition(
                metadata,
                this.newValue(spec, SurfaceRuleSemantics.OPAQUE)
        );
    }

    SurfaceRulePlan.KnownCondition knownCondition(
            Object source,
            SurfaceConditionSpec spec,
            SurfaceRuleSemantics.Semantics semantics,
            SurfaceRuleSemantics.BindingEffect bindingEffect
    ) {
        SurfaceRulePlan.CacheGroupId cacheGroup = spec instanceof SurfaceConditionSpec.Singleton
                ? this.sharedCacheGroup(spec)
                : this.newCacheGroup();
        return new SurfaceRulePlan.KnownCondition(
                this.conditionMetadata(
                        this.nextOccurrence(), source, bindingEffect, cacheGroup
                ),
                this.value(spec, semantics)
        );
    }

    SurfaceRulePlan.ConditionValue value(
            SurfaceConditionSpec spec,
            SurfaceRuleSemantics.Semantics semantics
    ) {
        ValueKey key = new ValueKey(spec, semantics);
        return this.canonicalValues.computeIfAbsent(key, ignored -> this.newValue(spec, semantics));
    }

    SurfaceRulePlan finish(SurfaceRulePlan.Rule root) {
        return new SurfaceRulePlan(
                root,
                this.values,
                this.nextEntry,
                this.nextOccurrence,
                this.nextCacheGroup
        );
    }

    private SurfaceRulePlan.CacheGroupId sharedCacheGroup(Object key) {
        return this.sharedCacheGroups.computeIfAbsent(key, ignored -> this.newCacheGroup());
    }

    private SurfaceRulePlan.CacheGroupId newCacheGroup() {
        return new SurfaceRulePlan.CacheGroupId(this.nextCacheGroup++);
    }

    private SurfaceRulePlan.ConditionValue newValue(
            SurfaceConditionSpec spec,
            SurfaceRuleSemantics.Semantics semantics
    ) {
        SurfaceRulePlan.ConditionValue value = new SurfaceRulePlan.ConditionValue(
                new SurfaceRulePlan.ValueId(this.values.size()), spec, semantics
        );
        this.values.add(value);
        return value;
    }

    private record ValueKey(
            SurfaceConditionSpec spec,
            SurfaceRuleSemantics.Semantics semantics
    ) {
    }
}
